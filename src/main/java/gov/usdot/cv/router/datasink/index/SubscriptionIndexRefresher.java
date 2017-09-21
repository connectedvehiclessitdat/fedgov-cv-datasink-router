package gov.usdot.cv.router.datasink.index;

import gov.usdot.cv.common.model.Filter;
import gov.usdot.cv.common.model.Subscriber;
import gov.usdot.cv.common.subscription.dao.SituationDataFilterDao;
import gov.usdot.cv.common.subscription.dao.SubscriberDao;
import gov.usdot.cv.router.datasink.model.SubscriptionDataSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;

import com.deleidos.rtws.commons.dao.exception.DataAccessException;

public class SubscriptionIndexRefresher extends TimerTask {
	
	private static final Logger logger = Logger.getLogger(SubscriptionIndexRefresher.class);
	
	/** The subscription data source. */
	private SubscriptionDataSource dataSource;
	
	/** The listener registered to receive cache update notifications. */
	private IndexUpdateListener listener;
	
	/** The data access object to the subscriber database table. */
	private SubscriberDao subscriberDao;
	
	/** The data access object to the situation data filter database table. */
	private SituationDataFilterDao filterDao;
	
	/** The index refresh interval. */
	private long refresh = -1;
	
	private boolean initialized = false;
	
	public SubscriptionIndexRefresher() {
		super();
	}
	
	public void setDataSource(SubscriptionDataSource dataSource) {
		this.dataSource = dataSource;
	}
	
	public void initialize() {
		synchronized (this) {
			if (initialized || dataSource == null) return;
			
			if (this.subscriberDao == null) {
				SubscriberDao.Builder subscriberBuilder = new SubscriberDao.Builder();
				subscriberBuilder.setDataSource(dataSource.getSQLDataSource()).setTableName(dataSource.getSubscriberTableName());
				this.subscriberDao = subscriberBuilder.build();
			}
			
			if (this.filterDao == null) {
				SituationDataFilterDao.Builder filterDaoBuilder = new SituationDataFilterDao.Builder();
				filterDaoBuilder.setDataSource(dataSource.getSQLDataSource()).setTableName(dataSource.getFilterTableName());
				this.filterDao = filterDaoBuilder.build();
			}
			
			if (this.subscriberDao != null && this.filterDao != null) {
				this.initialized = true;
			}
		}
	}
	
	public void setIndexRefreshInterval(long refresh) {
		this.refresh = refresh;
	}
	
	public void register(IndexUpdateListener listener) {
		this.listener = listener;
	}
	
	@Override
	public void run() {
		try {
			this.listener.update(buildSubscriberIndexes(), buildFilterIndexes());
		} catch(DataAccessException e) {
			logger.error("Unable to load the most current list of active subscriptions.", e);
		}
	}
	
	public void schedule(Timer timer, boolean initial) {
		if (initial) {
			run();
		}
		if (refresh > 0 && timer != null) {
			timer.schedule(this, refresh, refresh);
		}
	}
	
	/**
	 * Builds the subscribers index with the subscriber id as the key and the
	 * subscriber object as the value.
	 */
	protected Index<Integer, Subscriber> buildSubscriberIndexes() {
		Collection<Subscriber> subscribers = subscriberDao.findAll();
		Index.Builder<Integer, Subscriber> builder = new Index.Builder<Integer, Subscriber>();
		for (Subscriber subscriber : subscribers) {
			builder.add(subscriber.getSubscriberId(), subscriber);
		}
		return builder.build();
	}
	
	/**
	 * Builds the filters index with the bundle type as the key and list of filters
	 * associated to the type as the value.
	 */
	protected Index<String, List<Filter>> buildFilterIndexes() {
		Collection<Filter> filters = filterDao.findAll();
		Map<String, List<Filter>> buffer = new HashMap<String, List<Filter>>();
		for (Filter filter : filters) {
			List<Filter> bufList = buffer.get(filter.getType());
			if (bufList == null) {
				bufList = new ArrayList<Filter>();
				buffer.put(filter.getType(), bufList);
			}
			bufList.add(filter);
		}
		Index.Builder<String, List<Filter>> builder = new Index.Builder<String, List<Filter>>();
		for (Entry<String, List<Filter>> entry : buffer.entrySet()) {
			builder.add(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
		}
		return builder.build();
	}
	
	/**
	 * Interface that define the callback method for receiving the loaded data.
	 */
	public static interface IndexUpdateListener {
		public void update(Index<Integer, Subscriber> subscrubersIdx, Index<String, List<Filter>> filtersIdx);
	}
	
}