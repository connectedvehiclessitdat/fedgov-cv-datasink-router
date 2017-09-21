package gov.usdot.cv.router.datasink;

import java.util.HashSet;
import java.util.Set;

import gov.usdot.cv.common.model.Subscriber;
import gov.usdot.cv.router.datasink.index.SubscriptionManager;
import gov.usdot.cv.router.datasink.jms.TopicProducerManager;
import gov.usdot.cv.router.datasink.model.DataModel;
import gov.usdot.cv.router.datasink.model.SubscriptionDataSource;

import javax.sql.DataSource;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.deleidos.rtws.commons.exception.InitializationException;
import com.deleidos.rtws.core.framework.Description;
import com.deleidos.rtws.core.framework.SystemConfigured;
import com.deleidos.rtws.core.framework.UserConfigured;
import com.deleidos.rtws.core.framework.processor.AbstractDataSink;

@Description("Route situation data to the appropriate subscription channel.")
public class SituationDataRouter extends AbstractDataSink {
	
	private final Logger logger = Logger.getLogger(getClass());
	
	private static final Object LOCK = new Object();
	
	/** Used to manage active subscriptions. */
	private static SubscriptionManager subscriptionFilterManager;
	
	/** Used to manage active topic producers. */
	private static TopicProducerManager messageProducerManager;
	
	/** Used to access the database. */
	private DataSource dataSource;
	
	/** The name of the subscriber table. */
	private String subscriberTableName;
	
	/** The name of the situation data filter table. */
	private String filterTableName;
	
	/** A list of data models which will go through the subscription engine. */
	private Set<String> modelsToFilter = new HashSet<String>();
	
	public SituationDataRouter() {
		super();
	}
	
	@Override
	@SystemConfigured(value = "Situation Data Router")
	public void setName(String name) {
		super.setName(name);
	}

	@Override
	@SystemConfigured(value = "cvrouter")
	public void setShortname(String shortname) {
		super.setShortname(shortname);
	}
	
	@SystemConfigured(value="java:com.deleidos.rtws.commons.dao.source.H2ConnectionPool <URL>@h2.app.connection.url@</URL><user>@h2.app.connection.user@</user><password>@h2.app.connection.password@</password>")
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
	
	@UserConfigured(
		value = "SUBSCRIBER", 
		description = "The name of the subscriber table.")
	public void setDatabaseSubscriberTableName(String subscriberTableName) {
		this.subscriberTableName = subscriberTableName;
	}
	
	public String getDatabaseSubscriberTableName() {
		return this.subscriberTableName;
	}
	
	@UserConfigured(
		value = "SITUATION_DATA_FILTER", 
		description = "The name of the situation data filter table.")
	public void setDatabaseFilterTableName(String filterTableName) {
		this.filterTableName = filterTableName;
	}
	
	public String getDatabaseFilterTableName() {
		return this.filterTableName;
	}
	
	@UserConfigured(
		value = "vehSitDataMessage,intersectionSitData",
		description = "List of input data model names, delimited by comma, that will go through the subscription engine.",
		flexValidator = {"StringValidator minLength=2 maxLength=1024"})
	public void setModelsToFilter(String modelsToFilter) {
		if (StringUtils.isNotBlank(modelsToFilter)) {
			for (String model : modelsToFilter.trim().split("[,]")) {
				this.modelsToFilter.add(model);
			}
		}
	}
	
	public void initialize() throws InitializationException {
		synchronized (LOCK) {
			if (messageProducerManager == null && subscriptionFilterManager == null) {
				logger.info("Constructing subscription data source ...");
				SubscriptionDataSource ds = new SubscriptionDataSource();
				ds.setSQLDataSource(this.dataSource);
				ds.setSubscriberTableName(this.subscriberTableName);
				ds.setFilterTableName(this.filterTableName);
		
				subscriptionFilterManager = SubscriptionManager.newInstance();
				messageProducerManager = TopicProducerManager.newInstance();
				
				logger.info("Initializing topic producer manager ...");
				messageProducerManager.setSubscriptionManager(subscriptionFilterManager);
				messageProducerManager.initialize();
		
				logger.info("Initializing subscription filter manager ...");
				subscriptionFilterManager.setDataSource(ds);
				subscriptionFilterManager.initialize();
			}
		}
		logger.info(String.format("%s is initialized.", this.getName()));
	}
	
	public void dispose() {
		synchronized (LOCK) {
			if (subscriptionFilterManager != null) {
				subscriptionFilterManager.dispose();
				subscriptionFilterManager = null;
			}
			if (messageProducerManager != null) {
				messageProducerManager.dispose();
				messageProducerManager= null;
			}
		}
		logger.info(String.format("%s is disposed.", this.getName()));
	}
	
	public void flush() {
		logger.debug(String.format("The method flush() is not used by this class '%s'.", this.getClass().getName()));
	}

	@Override
	protected void processInternal(JSONObject record, FlushCounter counter) {
		try {
			DataModel model = DataModel.newInstance(record);
			if (model == null) {
				logger.error("Unable to determine the data model type.");
				return;
			}
			String encodedMsg = model.getEncodedMsg();
			if (encodedMsg == null) {
				logger.error("Record is missing the '" + DataModel.ENCODED_MSG_KEY + "' field/value");
				return;
			}
			
			// Send the record to all custom topics
			messageProducerManager.send(encodedMsg);
			
			if (modelsToFilter.contains(model.getModelName())) {
				// Send the record to any matching subscription(s)
				for (Subscriber subscriber : subscriptionFilterManager.findMatchingSubscriptions(model)) {
					messageProducerManager.send(subscriber, encodedMsg);
				}
			}
		} finally {
			counter.noop();
		}
	}
	
}