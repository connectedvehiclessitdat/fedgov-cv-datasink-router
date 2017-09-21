package gov.usdot.cv.router.datasink.index;

import gov.usdot.cv.common.model.BoundingBox;
import gov.usdot.cv.common.model.Filter;
import gov.usdot.cv.common.model.Subscriber;
import gov.usdot.cv.common.model.SubscriptionType;
import gov.usdot.cv.common.model.VsmType;
import gov.usdot.cv.common.util.PropertyLocator;
import gov.usdot.cv.router.datasink.model.DataModel;
import gov.usdot.cv.router.datasink.model.IntersectionSitDataModel;
import gov.usdot.cv.router.datasink.model.SubscriptionDataSource;
import gov.usdot.cv.router.datasink.model.VehSitDataModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.Timer;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.log4j.Logger;

import com.deleidos.rtws.commons.util.Initializable;
import com.spatial4j.core.context.SpatialContext;
import com.spatial4j.core.shape.Point;
import com.spatial4j.core.shape.Rectangle;
import com.spatial4j.core.shape.ShapeCollection;
import com.spatial4j.core.shape.SpatialRelation;

public class SubscriptionManager implements Initializable {
	
	private static final String DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
	
	private static final SimpleDateFormat FORMATTER = new SimpleDateFormat(DATE_PATTERN);
	
	private static Logger logger = Logger.getLogger(SubscriptionManager.class);
	
	/** The subscription data source. */
	private SubscriptionDataSource dataSource;
	
	/** Used to reload and build the indexes. */
	private SubscriptionIndexRefresher indexRefresher = new SubscriptionIndexRefresher();
	
	/** Lock used to synchronize index updates. */
	private final ReadLock readLock;

	/** Lock used to synchronize index updates. */
	private final WriteLock writeLock;
	
	/** Index of subscriber id to the subscriber object. */
	private Index<Integer, Subscriber> subscriberIndexes;
	
	/** Index of subscription type to a list of filters. */
	private Index<String, List<Filter>> filterIndexes;
	
	/** Topic listener that gets notified when the index changes. */
	private TopicProducerListener listener;
	
	/** Timer used to rebuild the subscription index. */
	private Timer timer = new Timer("Subscription Index Refresher", true);
	
	/** Indicates if the object have been initialized. */
	private boolean initialized = false;
	
	/** Indicates if the object have been disposed. */
	private boolean disposed = false;
	
	/** The context object to the spatial4j library. */
	private SpatialContext ctx;
	
	private static class SubscriptionManagerHolder { 
		public static final SubscriptionManager INSTANCE = new SubscriptionManager();
	}
	
	public static SubscriptionManager newInstance() {
		return SubscriptionManagerHolder.INSTANCE;
	}
	
	private SubscriptionManager() {
		super();
		this.ctx = SpatialContext.GEO;
		ReentrantReadWriteLock semaphore = new ReentrantReadWriteLock();
		readLock = semaphore.readLock();
		writeLock = semaphore.writeLock();
	}
	
	public void setDataSource(SubscriptionDataSource dataSource) {
		this.dataSource = dataSource;
	}
	
	public void initialize() {
		writeLock.lock();
		try {
			if (! initialized) {
				int refresh = PropertyLocator.getInt("cvrouter.index.refresh.interval", 60000); 
				logger.info(String.format("Subscription manager will refresh every %s milliseconds.", refresh));
				this.indexRefresher.setIndexRefreshInterval(refresh);
				this.indexRefresher.setDataSource(this.dataSource);
				this.indexRefresher.initialize();
				this.indexRefresher.register(new UpdateListener());
				this.indexRefresher.schedule(timer, true);
				this.initialized = true;
				logger.info("Subscription manager is initialized.");
			}
		} finally {
			writeLock.unlock();
		}
	}

	public  void dispose() {
		writeLock.lock();
		try {
			if (! disposed) {
				this.indexRefresher.cancel();
				this.filterIndexes = null;
				this.subscriberIndexes = null;
				this.disposed = true;
			}
			logger.info("The subscription manager is disposed.");
		} finally {
			writeLock.unlock();
		}
	}
	
	public void register(TopicProducerListener listener) {
		this.listener = listener;
	}
	
	public Set<Subscriber> findMatchingSubscriptions(DataModel model) {
		String encodedMsg = model.getEncodedMsg();
		if (encodedMsg == null) {
			logger.error(String.format("Record is missing original situation data [%s].", model.toString()));
			return Collections.emptySet();
		}
		
		readLock.lock();
		try {
			logger.debug(String.format("Finding matching subscriptions for record [%s]", model.toString()));
			
			if (this.filterIndexes == null || this.subscriberIndexes == null) {
				logger.debug("Filter and/or subscriber indexes not populated.");
				return Collections.emptySet();
			}
			
			if (model instanceof VehSitDataModel) {
				return findMatchingVehSitDataSubscriptions(model);
			} else if (model instanceof IntersectionSitDataModel) {
				return findMatchingIntersectionSitDataSubscriptions(model);
			} else {
				return Collections.emptySet();
			}
		} finally {
			readLock.unlock();
		}
	}
	
	public Set<Subscriber> findMatchingVehSitDataSubscriptions(DataModel model) {
		readLock.lock();
		try {
			VehSitDataModel vehSitDataModel = (VehSitDataModel) model;
			Integer sitDataVsmType = vehSitDataModel.getVsmType();
			if (sitDataVsmType == null) {
				logger.debug("VSM type attribute not found in record.");
				return Collections.emptySet();
			}
			
			List<Filter> filters = getMatchingVsmTypeFilters(sitDataVsmType);
			if (filters == null || filters.size() == 0) {
				logger.debug(String.format("No filters found for vsm type [%s]", sitDataVsmType));
				return Collections.emptySet();
			}
			
			Double lat 		= vehSitDataModel.getLat();
			Double lon 		= vehSitDataModel.getLon();
			Integer year 	= vehSitDataModel.getYear();
			Integer month 	= vehSitDataModel.getMonth();
			Integer day 	= vehSitDataModel.getDay();
			Integer hour 	= vehSitDataModel.getHour();
			Integer minute 	= vehSitDataModel.getMinute();
			Integer second 	= vehSitDataModel.getSecond();
			
			Calendar recordDate = Calendar.getInstance();
			recordDate.set(year, month - 1, day, hour, minute, second);
			recordDate.setTimeZone(TimeZone.getTimeZone(Filter.UTC_TIMEZONE));
			
			Set<Subscriber> result = new HashSet<Subscriber>();
			for (Filter filter : filters) {
				BoundingBox bb = filter.getBoundingBox();
				if (bb != null) {
					Rectangle filterRect = this.ctx.makeRectangle(bb.getNWLon(), bb.getSELon(), bb.getSELat(), bb.getNWLat());
					Point vehSitPoint = this.ctx.makePoint(lon, lat);
					
					ShapeCollection<Point> collection = new ShapeCollection<Point>(Collections.singletonList(vehSitPoint), this.ctx);
					SpatialRelation relation = collection.relate(filterRect);
					if (relation == SpatialRelation.DISJOINT) {
						logger.debug(String.format("Vehicle record not within nor intersects the bounding box filter [%s].", filter.toString()));
						continue;
					}
				}
				
				if (recordDate.after(filter.getEndTime())) {
					logger.debug(String.format("Vehicle record is after the end time [%s].", filter.toString()));
					continue;
				}

				logger.debug(String.format("Vehicle record match filter [%s].", filter.toString()));
				
				Subscriber subscriber = subscriberIndexes.getValue(filter.getSubscriberId());
				if (subscriber == null) {
					logger.debug(String.format("Subscriber [%s] not found.", filter.getSubscriberId()));
					continue;
				}

				result.add(subscriber);
			}
			
			return result;
		} catch (Exception ex) {
			logger.error("Failed to find matching vehicle situation data subscriptions.", ex);
		} finally {
			readLock.unlock();
		}
		
		return Collections.emptySet();
	}
	
	private Set<Subscriber> findMatchingIntersectionSitDataSubscriptions(DataModel model) {
		readLock.lock();
		try {
			IntersectionSitDataModel intersectionSitDataModel = (IntersectionSitDataModel) model;
			List<Filter> filters = this.filterIndexes.getValue(SubscriptionType.IsdType.toString());
			if (filters == null || filters.size() == 0) {
				logger.debug("No filters found for isd type.");
				return Collections.emptySet();
			}
			
			String timestamp 	= intersectionSitDataModel.getTimestamp();
			Double nwLat 		= intersectionSitDataModel.getNWLat();
			Double nwLon 		= intersectionSitDataModel.getNWLon();
			Double seLat 		= intersectionSitDataModel.getSELat();
			Double seLon 		= intersectionSitDataModel.getSELon();
			
			Calendar recordDate = null;
			try {
				recordDate = Calendar.getInstance();
				recordDate.setTimeZone(TimeZone.getTimeZone(Filter.UTC_TIMEZONE));
				synchronized (this) {
					recordDate.setTime(FORMATTER.parse(timestamp));
				}
			} catch (ParseException pe) {
				logger.error(String.format("Failed to parse intersection record timestamp '%s'.", timestamp), pe);
				return Collections.emptySet();
			}
			
			Set<Subscriber> result = new HashSet<Subscriber>();
			for (Filter filter : filters) {
				BoundingBox bb = filter.getBoundingBox();
				if (bb != null) {
					Rectangle filterRect = this.ctx.makeRectangle(bb.getNWLon(), bb.getSELon(), bb.getSELat(), bb.getNWLat());
					Rectangle intersectionRect = this.ctx.makeRectangle(nwLon, seLon, seLat, nwLat);
					
					ShapeCollection<Rectangle> collection = new ShapeCollection<Rectangle>(Collections.singletonList(intersectionRect), this.ctx);
					SpatialRelation relation = collection.relate(filterRect);
					if (relation == SpatialRelation.DISJOINT) {
						logger.debug(String.format("Intersection record not within nor intersects the bounding box filter [%s].", filter.toString()));
						continue;
					}
				}
				
				if (recordDate.after(filter.getEndTime())) {
					logger.debug(String.format("Intersection record is after the end time [%s].", filter.toString()));
					continue;
				}
				
				logger.debug(String.format("Intersection record match filter [%s].", filter.toString()));
				
				Subscriber subscriber = subscriberIndexes.getValue(filter.getSubscriberId());
				if (subscriber == null) {
					logger.debug(String.format("Subscriber [%s] not found.", filter.getSubscriberId()));
					continue;
				}
				
				result.add(subscriber);
			}
		
			return result;
		} catch (Exception ex) {
			logger.error("Failed to find matching intersection situation data subscriptions.", ex);
			ex.printStackTrace();
		} finally {
			readLock.unlock();
		}
		
		return Collections.emptySet();
	}
	
	private List<Filter> getMatchingVsmTypeFilters(Integer vehVsmTypeValue) {
		if (! VsmType.isValid(vehVsmTypeValue)) return Collections.emptyList();
		List<Filter> inMemFilters = this.filterIndexes.getValue(SubscriptionType.VsmType.toString());
		if (inMemFilters == null) return Collections.emptyList();
		List<Filter> matchingFilters = new ArrayList<Filter>();
		for (Filter filter : inMemFilters) {
			int result = filter.getTypeValue() & vehVsmTypeValue;
			if (result > 0) {
				matchingFilters.add(filter);
			}
		}
		return matchingFilters;
	}
	
	/**
	 * Inner class used to replace the current indexes.
	 */
	private class UpdateListener implements SubscriptionIndexRefresher.IndexUpdateListener {
		public void update(Index<Integer, Subscriber> subscribersIdx, Index<String, List<Filter>> filtersIdx) {
			writeLock.lock();
			try {
				// update the indexes & notify the topic producer manager
				subscriberIndexes = subscribersIdx;
				filterIndexes = filtersIdx;
				listener.update(subscribersIdx.keySet());
			} finally {
				writeLock.unlock();
			}
		}
	}
	
	/**
	 * Interface that define the callback method for refreshing the producers list.
	 */
	public static interface TopicProducerListener {
		public void update(Set<Integer> subscribers);
	}
	
}