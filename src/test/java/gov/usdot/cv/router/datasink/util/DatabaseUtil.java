package gov.usdot.cv.router.datasink.util;

import gov.usdot.cv.common.model.BoundingBox;
import gov.usdot.cv.common.model.Filter;
import gov.usdot.cv.common.model.Subscriber;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import com.deleidos.rtws.commons.dao.jdbc.DataAccessSession;
import com.deleidos.rtws.commons.dao.jdbc.DefaultStatementHandler;
import com.deleidos.rtws.commons.exception.InitializationException;

public class DatabaseUtil {
	
	public static void buildSubscriptionDatabase(DataAccessSession session) {
		try {
			PreparedStatement createSchemaStmt = session.prepareStatement("CREATE SCHEMA IF NOT EXISTS APPLICATION;");
			session.executeStatement(createSchemaStmt, null);
		} catch (SQLException sqle) {
			throw new InitializationException("Failed to create application schema.", sqle);
		}
		
		try {
			StringBuilder createSubscriberTableStmt = new StringBuilder();
			createSubscriberTableStmt.append("CREATE TABLE IF NOT EXISTS APPLICATION.TEST_SUBSCRIBER(");
			createSubscriberTableStmt.append("ID 			VARCHAR(255) 	NOT NULL PRIMARY KEY,");
			createSubscriberTableStmt.append("CERTIFICATE 	BINARY 			NOT NULL,");
			createSubscriberTableStmt.append("TARGET_HOST 	VARCHAR(255) 	NOT NULL,");
			createSubscriberTableStmt.append("TARGET_PORT 	INT 			NOT NULL,");
			createSubscriberTableStmt.append(");");
			
			PreparedStatement createUserTablePreparedStmt = session.prepareStatement(createSubscriberTableStmt.toString());
			session.executeStatement(createUserTablePreparedStmt, null);
		} catch (SQLException sqle) {
			throw new InitializationException("Failed to create subscription user table.", sqle);
		}
		
		try {
			StringBuilder createFilterTableStmt = new StringBuilder();
			createFilterTableStmt.append("CREATE TABLE IF NOT EXISTS APPLICATION.TEST_SITUATION_DATA_FILTER(");
			createFilterTableStmt.append("ID 			INT 		NOT NULL,");
			createFilterTableStmt.append("END_TIME 		TIMESTAMP 	NOT NULL,");
			createFilterTableStmt.append("TYPE 			VARCHAR(32) NOT NULL,");
			createFilterTableStmt.append("TYPE_VALUE 	INT 		NOT NULL,");
			createFilterTableStmt.append("REQUEST_ID 	INT 		NOT NULL,");
			createFilterTableStmt.append("NW_LAT 	NUMBER,");
			createFilterTableStmt.append("NW_LON 	NUMBER,");
			createFilterTableStmt.append("SE_LAT 	NUMBER,");
			createFilterTableStmt.append("SE_LON 	NUMBER,");
			createFilterTableStmt.append("CONSTRAINT SUBSCRIBER_ID_FK FOREIGN KEY(ID) REFERENCES APPLICATION.TEST_SUBSCRIBER(ID)");
			createFilterTableStmt.append(");");
			
			PreparedStatement createFilterTablePreparedStmt = session.prepareStatement(createFilterTableStmt.toString());
			session.executeStatement(createFilterTablePreparedStmt, null);
		} catch (SQLException sqle) {
			throw new InitializationException("Failed to create situation data filter table.", sqle);
		}
	}
	
	public static void loadSubscribers(DataAccessSession session) throws UnsupportedEncodingException, NoSuchAlgorithmException {
		List<Subscriber> subscribers = new ArrayList<Subscriber>();
		
		Subscriber.Builder builder = new Subscriber.Builder();
		builder.setSubscriberId(10000000);
		builder.setCertificate(CertificateUtil.generatePublicKey().getBytes("UTF-8"));
		builder.setDestHost("127.0.0.1");
		builder.setDestPort(7443);
		subscribers.add(builder.build());
		builder.setSubscriberId(10000001);
		builder.setCertificate(CertificateUtil.generatePublicKey().getBytes("UTF-8"));
		builder.setDestHost("127.0.0.1");
		builder.setDestPort(7443);
		subscribers.add(builder.build());
		builder.setSubscriberId(10000002);
		builder.setCertificate(CertificateUtil.generatePublicKey().getBytes("UTF-8"));
		builder.setDestHost("127.0.0.1");
		builder.setDestPort(7443);
		subscribers.add(builder.build());
		
		for (Subscriber subscriber : subscribers) {
			addSubscriber(session, subscriber);
		}
	}
	
	/**
	 * Load a list of canned subscribers into the database.
	 */
	@SuppressWarnings("deprecation")
	public static void addSubscriber(DataAccessSession session, Subscriber subscriber) {
		StringBuilder sql = new StringBuilder();
		sql.append("INSERT INTO APPLICATION.TEST_SUBSCRIBER");
		sql.append(" (");
		sql.append("ID").append(", ");
		sql.append("CERTIFICATE").append(", ");
		sql.append("TARGET_HOST").append(", ");
		sql.append("TARGET_PORT").append(") ");
		sql.append(" VALUES (?,?,?,?)");
		
		DefaultStatementHandler handler = new DefaultStatementHandler(new Object [] { 
			subscriber.getSubscriberId(), 
			subscriber.getCertificate(), 
			subscriber.getDestHost(), 
			subscriber.getDestPort()
		});
		
		session.executeStatement(sql.toString(), handler);
	}
	
	@SuppressWarnings("deprecation")
	public static void deleteSubscriber(DataAccessSession session, Subscriber subscriber)  {
		StringBuilder sql = new StringBuilder();
		sql.append("DELETE FROM APPLICATION.TEST_SUBSCRIBER");
		sql.append(" WHERE ");
		sql.append("ID=").append("'").append(subscriber.getSubscriberId()).append("'");
		session.executeStatement(sql.toString(), null);
	}
	
	/**
	 * Load a list of canned filters into the database.
	 */
	public static void loadFilters(DataAccessSession session) throws ParseException {
		List<Filter> filters = new ArrayList<Filter>();
		
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		Calendar end = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		end.setTimeInMillis(df.parse("3020-02-28T10:10:00Z").getTime());
		
		Filter.Builder f = new Filter.Builder();
		BoundingBox.Builder bb = new BoundingBox.Builder();
		
		// TMC subscriber
		f.setSubscriberId(10000000);
		f.setRequestId(1001);
		f.setEndTime(end);
		f.setType("VsmType");
		f.setTypeValue(1);
		f.setBoundingBox(null);
		filters.add(f.build()); // vsm type filter
		f.setEndTime(end);
		f.setType("IsdType");
		f.setTypeValue(1);
		f.setBoundingBox(null);
		filters.add(f.build()); // isd type filter

		// Processing Center subscriber with bounding box filter
		f.setSubscriberId(10000001);
		f.setRequestId(1001);
		f.setEndTime(end);
		f.setType("VsmType");
		f.setTypeValue(1);
		bb.setNWLat(43.0);
		bb.setNWLon(-85.0);
		bb.setSELat(41.0);
		bb.setSELon(-82.0);
		f.setBoundingBox(bb.build());
		filters.add(f.build()); // vsm type with bounding box filter
		f.setSubscriberId(10000001);
		f.setRequestId(1001);
		f.setEndTime(end);
		f.setType("IsdType");
		f.setTypeValue(1);
		bb.setNWLat(43.5);
		bb.setNWLon(-85.5);
		bb.setSELat(40.5);
		bb.setSELon(-81.5);
		f.setBoundingBox(bb.build());
		filters.add(f.build()); // isd type with bounding box filter
		
		// OBE subscriber with bundle type filters
		f.setSubscriberId(10000002);
		f.setRequestId(1001);
		f.setEndTime(end);
		f.setType("VsmType");
		f.setTypeValue(1);
		f.setBoundingBox(null);
		filters.add(f.build()); // vsm type filter
		f.setEndTime(end);
		f.setType("IsdType");
		f.setTypeValue(1);
		bb.setNWLat(44.0);
		bb.setNWLon(-86.0);
		bb.setSELat(41.5);
		bb.setSELon(-82.5);
		f.setBoundingBox(bb.build());
		filters.add(f.build()); // isd type filter
		
		for (Filter filter : filters) {
			addFilter(session, filter);
		}
	}
	
	@SuppressWarnings("deprecation")
	public static void deleteFilter(DataAccessSession session, Filter filter)  {
		StringBuilder sql = new StringBuilder();
		sql.append("DELETE FROM APPLICATION.TEST_SITUATION_DATA_FILTER");
		sql.append(" WHERE ");
		sql.append("ID=").append("'").append(filter.getSubscriberId()).append("'");
		session.executeStatement(sql.toString(), null);
	}
	
	@SuppressWarnings("deprecation")
	public static void addFilter(DataAccessSession session, Filter filter) {
		StringBuilder sql = new StringBuilder();
		sql.append("INSERT INTO APPLICATION.TEST_SITUATION_DATA_FILTER");
		sql.append(" (");
		sql.append("ID").append(", ");
		sql.append("REQUEST_ID").append(", ");
		sql.append("END_TIME").append(", ");
		sql.append("TYPE").append(", ");
		sql.append("TYPE_VALUE").append(", ");
		sql.append("NW_LAT").append(", ");
		sql.append("NW_LON").append(", ");
		sql.append("SE_LAT").append(", ");
		sql.append("SE_LON").append(") ");
		sql.append(" VALUES (?,?,?,?,?,?,?,?,?)");
		
		DefaultStatementHandler handler = null;
		if (filter.getBoundingBox() == null) {
			handler = new DefaultStatementHandler(new Object [] { 
				filter.getSubscriberId(), 
				filter.getRequestId(),
				filter.getEndTime().getTime(), 
				filter.getType(),
				filter.getTypeValue(),
				null, null, null, null
			});
		} else {
			handler = new DefaultStatementHandler(new Object [] { 
				filter.getSubscriberId(), 
				filter.getRequestId(),
				filter.getEndTime().getTime(), 
				filter.getType(),
				filter.getTypeValue(),
				filter.getBoundingBox().getNWLat(), 
				filter.getBoundingBox().getNWLon(), 
				filter.getBoundingBox().getSELat(), 
				filter.getBoundingBox().getSELon()
			});
		}
		
		session.executeStatement(sql.toString(), handler);
	}
	
}