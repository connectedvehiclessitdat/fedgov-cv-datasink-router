package gov.usdot.cv.router.datasink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import gov.usdot.cv.common.model.BoundingBox;
import gov.usdot.cv.common.model.Filter;
import gov.usdot.cv.common.model.Subscriber;
import gov.usdot.cv.common.subscription.dao.SituationDataFilterDao;
import gov.usdot.cv.resources.PrivateTestResourceLoader;
import gov.usdot.cv.router.datasink.model.SubscriptionDataSource;
import gov.usdot.cv.router.datasink.sim.RouterSimulator;
import gov.usdot.cv.router.datasink.subscriber.ChannelSubscriber;
import gov.usdot.cv.router.datasink.subscriber.MessageCollector;
import gov.usdot.cv.router.datasink.util.CertificateUtil;
import gov.usdot.cv.router.datasink.util.DatabaseUtil;

import java.io.File;
import java.io.IOException;
import java.sql.DriverManager;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;

import javax.sql.DataSource;

import net.sf.json.JSONObject;

import org.apache.activemq.broker.BrokerService;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.deleidos.rtws.commons.config.RtwsConfig;
import com.deleidos.rtws.commons.dao.jdbc.DataAccessSession;
import com.deleidos.rtws.commons.dao.source.H2ConnectionPool;

public class RouteVehSitDataTest {
	
	private static final String JDBC_URL = "jdbc:h2:%s";
	private static final String JMS_BROKER_NAME = "junit-router";
	private static final String JMS_CONNECTOR_URL = "tcp://localhost:61619";
	private static final String JMS_USERNAME = "test";
	private static final String JMS_PASSWORD =
			PrivateTestResourceLoader.getProperty("@datasink-router/route.veh.sit.data.test.password@");
	private static DataSource dataSource;
	private static DataAccessSession session;
	
	private static BrokerService broker;
	private static List<ChannelSubscriber> subscribers = new ArrayList<ChannelSubscriber>();
	
	private static RouterSimulator router1;
	private static RouterSimulator router2;
	private static RouterSimulator router3;
	private static RouterSimulator router4;
	
	private static SituationDataFilterDao dao;
	
	private static final String BASE64_SIT_DATA = "MHCAAgCKgQEBokWgKaATgAIH3oEBAoIBCoMBCIQBHoUBGYEBK4IBVYMCA0iEAQCFAgA3hwEkgQEAggECoxKDEP/u/rQAAABk//n+yAAAAGSDIAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
	
	private static JSONObject sampleRecord;
	private static JSONObject noBundleTypeMatchRecord;
	private static JSONObject spatialMatchRecord;
	private static JSONObject noSpatialMatchRecord;
	private static JSONObject notWithinTimeRange;
	
	private static Subscriber rhsdw;
	private static Filter rhsdw_f1;
	
	private static Subscriber all;
	private static Filter all_f1;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Properties testProperties = System.getProperties();
		if (testProperties.getProperty("RTWS_CONFIG_DIR") == null) {
			testProperties.setProperty("RTWS_CONFIG_DIR", testProperties.getProperty("basedir", "."));
			try {
				testProperties.load(
						PrivateTestResourceLoader.getFileAsStream(
								"@properties/datasink-router-filtering.properties@"));

				System.setProperties(testProperties);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		System.out.println("Configuring subscription database ...");
		
		StringBuilder dirPath = new StringBuilder();
		dirPath.append(System.getProperty("user.dir")).append(File.separator).append("src").append(File.separator);
		dirPath.append("test").append(File.separator).append("resources").append(File.separator);
		dirPath.append("db").append(File.separator).append("testdb");
		
		File h2db = new File(dirPath.toString() + ".h2.db");
		h2db.delete();
		File h2tracedb = new File(dirPath.toString() + ".trace.db");
		h2tracedb.delete();
		
		String url = String.format(JDBC_URL, dirPath.toString());
		System.out.println("Setting unit test database directory at '" + dirPath + "'.");
		
		System.setProperty("pubsub.server.protocol", "tcp");
		System.setProperty("pubsub.server.port", "61619");
		System.setProperty("pubsub.server.domain", "localhost");
		System.setProperty("cvrouter.index.refresh.interval", "1000");
		
		String username = RtwsConfig.getInstance().getString("h2.sa.connection.user");
		String password = RtwsConfig.getInstance().getString("h2.sa.connection.password");
		
		System.out.println("Starting subscription database ...");
		DriverManager.getConnection(url, username, password);
		
		dataSource = new H2ConnectionPool();
		((H2ConnectionPool) dataSource).setURL(url);
		((H2ConnectionPool) dataSource).setUser(username);
		((H2ConnectionPool) dataSource).setPassword(password);
		
		session = DataAccessSession.session(dataSource);
		
		SituationDataFilterDao.Builder daoBuilder = new SituationDataFilterDao.Builder();
		daoBuilder.setDataSource(dataSource).setTableName("TEST_SITUATION_DATA_FILTER");
		dao = daoBuilder.build();
		
		System.out.println("Loading subscribers and filters into database ...");
		DatabaseUtil.buildSubscriptionDatabase(session);
		DatabaseUtil.loadSubscribers(session);
		DatabaseUtil.loadFilters(session);
		
		System.out.println("Starting jms broker service ...");
		System.setProperty("messaging.external.connection.user", "test");
		System.setProperty("messaging.external.connection.password", "password");
		
		broker = new BrokerService();
		broker.setBrokerName(JMS_BROKER_NAME);
		broker.addConnector(JMS_CONNECTOR_URL);
		broker.setPersistent(false);
		broker.setUseJmx(false);
		broker.start();
		
		System.out.println("Creating channel subscribers ...");
		ChannelSubscriber tmc = new ChannelSubscriber(JMS_CONNECTOR_URL, JMS_USERNAME, JMS_PASSWORD, Integer.toString(10000000));
		ChannelSubscriber pc = new ChannelSubscriber(JMS_CONNECTOR_URL, JMS_USERNAME, JMS_PASSWORD, Integer.toString(10000001));
		ChannelSubscriber obe = new ChannelSubscriber(JMS_CONNECTOR_URL, JMS_USERNAME, JMS_PASSWORD, Integer.toString(10000002));
		ChannelSubscriber rhsdw = new ChannelSubscriber(JMS_CONNECTOR_URL, JMS_USERNAME, JMS_PASSWORD, Integer.toString(10000003));
		ChannelSubscriber all = new ChannelSubscriber(JMS_CONNECTOR_URL, JMS_USERNAME, JMS_PASSWORD, Integer.toString(10000004));
		
		subscribers.add(tmc);
		subscribers.add(pc);
		subscribers.add(obe);
		subscribers.add(rhsdw);
		subscribers.add(all);
		
		System.out.println("Building data set ...");
		buildTestCaseSpecificDataSet();
		
		System.out.println("Creating a collection of situation data router ...");
		SubscriptionDataSource ds = new SubscriptionDataSource();
		ds.setSQLDataSource(dataSource);
		ds.setSubscriberTableName("TEST_SUBSCRIBER");
		ds.setFilterTableName("TEST_SITUATION_DATA_FILTER");
		
		router1 = new RouterSimulator(sampleRecord, ds);
		router1.setRecord(sampleRecord);
		router1.setNumRecordsToSend(1);
		router2 = new RouterSimulator(sampleRecord, ds);
		router2.setRecord(sampleRecord);
		router2.setNumRecordsToSend(1);
		router3 = new RouterSimulator(sampleRecord, ds);
		router3.setRecord(sampleRecord);
		router3.setNumRecordsToSend(1);
		router4 = new RouterSimulator(sampleRecord, ds);
		router4.setRecord(sampleRecord);
		router4.setNumRecordsToSend(1);
		
		// Give a little time for the index manager to load the index
		try { Thread.sleep(3000); } catch (Exception ignore) {}
	}
	
	@SuppressWarnings("deprecation")
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		System.out.println("Removing subscribers and filters from database ...");
		session.executeStatement("DELETE APPLICATION.TEST_SITUATION_DATA_FILTER;", null);
		session.executeStatement("DELETE APPLICATION.TEST_SUBSCRIBER;", null);
		
		System.out.println("Stopping jms broker service ...");
		broker.stop();
	}
	
	@Before
	public void setUp() throws Exception {
		MessageCollector.getInstance().clearAll();
	}
	
	@After
	public void tearDown() {
		System.out.println("Deleting 'RHSDW' subscriber and filter from database ...");
		DatabaseUtil.deleteFilter(session, rhsdw_f1);
		DatabaseUtil.deleteSubscriber(session, rhsdw);
		// Give the background thread some time to remove the filter
		int retries = 5;
		while (retries > 0) {
			if (findFilter(10000003, false) == null) break;
			try { Thread.sleep(3000); } catch (InterruptedException ignore) {}
			retries--;
		}
	}
	
	/**
	 * Test sending a single record from a single router and having the subscribers 
	 * pick up that one message.
	 */
	@Test
	public void testVsmTypeMatch() {
		System.out.println(">>> Running testVsmTypeMatch() ...");
		
		router1.setNumRecordsToSend(1);
		router1.setRecord(sampleRecord);
		
		Thread sim_t = new Thread(router1);
		sim_t.start();
		
		try {
			// Give the subscribers some time to collect all the messages sent
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		List<String> tmc_messages = MessageCollector.getInstance().get("10000000");
		assertTrue("Expecting 1 message for the 10000000 subscriber but got " + tmc_messages.size() + ".", tmc_messages.size() == 1);
		assertEquals(BASE64_SIT_DATA, tmc_messages.get(0));
		List<String> pc_messages = MessageCollector.getInstance().get("10000001");
		assertTrue("Expecting 1 message for the 10000001 subscriber but got " + pc_messages.size() + ".", pc_messages.size() == 1);
		assertEquals(BASE64_SIT_DATA, pc_messages.get(0));
		List<String> obe_messages = MessageCollector.getInstance().get("10000002");
		assertTrue("Expecting 1 message for the 10000002 subscriber but got " + obe_messages.size() + ".", obe_messages.size() == 1);
		assertEquals(BASE64_SIT_DATA, obe_messages.get(0));
	}
	
	/**
	 * Test sending n messages from a single router and having the subscribers
	 * pick up all of them and not drop a message.
	 */
	@Test
	public void testManyRecordMatch() {
		System.out.println(">>> Running testManyRecordMatch() ...");
		
		router1.setNumRecordsToSend(10);
		router1.setRecord(sampleRecord);
		
		Thread sim_t = new Thread(router1);
		sim_t.start();
		
		try {
			// Give the subscribers some time to collect all the messages sent
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		List<String> tmc_messages = MessageCollector.getInstance().get("10000000");
		assertTrue("Expecting 10 messages for the 10000000 subscriber but got " + tmc_messages.size() + ".", tmc_messages.size() == 10);
		assertEquals(BASE64_SIT_DATA, tmc_messages.get(0));
		List<String> pc_messages = MessageCollector.getInstance().get("10000001");
		assertTrue("Expecting 10 messages for the 10000001 subscriber but got " + pc_messages.size() + ".", pc_messages.size() == 10);
		assertEquals(BASE64_SIT_DATA, pc_messages.get(0));
		List<String> obe_messages = MessageCollector.getInstance().get("10000002");
		assertTrue("Expecting 10 messages for the 10000002 subscriber but got " + obe_messages.size() + ".", obe_messages.size() == 10);
		assertEquals(BASE64_SIT_DATA, obe_messages.get(0));
	}
	
	/**
	 * Test having several routers send n messages and having the
	 * subscribers pick up all of them and not drop any messages.
	 */
	@Test
	public void testSeveralRoutersManyRecordMatch() {
		System.out.println(">>> Running testSeveralRoutersManyRecordMatch() ...");
		
		router1.setNumRecordsToSend(10);
		router1.setRecord(sampleRecord);
		router2.setNumRecordsToSend(10);
		router2.setRecord(sampleRecord);
		router3.setNumRecordsToSend(10);
		router3.setRecord(sampleRecord);
		router4.setNumRecordsToSend(10);
		router4.setRecord(sampleRecord);
		
		Thread sim_t1 = new Thread(router1);
		Thread sim_t2 = new Thread(router2);
		Thread sim_t3 = new Thread(router3);
		Thread sim_t4 = new Thread(router4);
		
		sim_t1.start();
		sim_t2.start();
		sim_t3.start();
		sim_t4.start();
		
		try {
			// Give the subscribers some time to collect all the messages sent
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		List<String> tmc_messages = MessageCollector.getInstance().get("10000000");
		assertTrue("Expecting 40 messages for the 10000000 subscriber but got " + tmc_messages.size() + ".", tmc_messages.size() == 40);
		assertEquals(BASE64_SIT_DATA, tmc_messages.get(0));
		List<String> pc_messages = MessageCollector.getInstance().get("10000001");
		assertTrue("Expecting 40 messages for the 10000001 subscriber but got " + pc_messages.size() + ".", pc_messages.size() == 40);
		assertEquals(BASE64_SIT_DATA, pc_messages.get(0));
		List<String> obe_messages = MessageCollector.getInstance().get("10000002");
		assertTrue("Expecting 40 messages for the 10000002 subscriber but got " + obe_messages.size() + ".", obe_messages.size() == 40);
		assertEquals(BASE64_SIT_DATA, obe_messages.get(0));
	}
	
	@Test
	public void testSubscribing2AllVsmTypes() {
		System.out.println(">>> Running testSubscribing2AllVsmTypes() ...");
		
		DatabaseUtil.addSubscriber(session, all);
		DatabaseUtil.addFilter(session, all_f1);
		
		try {
			// Give the background thread some time to update the index
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		router1.setNumRecordsToSend(5);
		router1.setRecord(sampleRecord);
		
		Thread sim_t = new Thread(router1);
		sim_t.start();
		
		try {
			// Give the subscribers some time to collect all the messages sent
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		List<String> messages = MessageCollector.getInstance().get("10000004");
		assertNotNull("TMC expecting matches but no messages found.", messages);
		assertTrue("Expecting 5 messages for the 10000004 subscriber but got " + messages.size() + ".", messages.size() == 5);
	}
	
	/**
	 * Test sending a record that doesn't match any filters.
	 */
	@Test
	public void testNoVsmTypeMatch() throws Exception {
		System.out.println(">>> Running testNoVsmTypeMatch() ...");
		
		router1.setNumRecordsToSend(1);
		router1.setRecord(noBundleTypeMatchRecord);
		
		Thread sim_t = new Thread(router1);
		sim_t.start();
		
		try {
			// Give the subscribers some time to collect all the messages sent
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		List<String> tmc_messages = MessageCollector.getInstance().get("10000000");
		assertNull("Subscriber 10000000 expecting no matches but got messages were found.", tmc_messages);
		List<String> pc_messages = MessageCollector.getInstance().get("10000001");
		assertNull("Subscriber 10000001 expecting no matches but got messages were found.", pc_messages);
		List<String> obe_messages = MessageCollector.getInstance().get("10000002");
		assertNull("Subscriber 10000002 expecting no matches but got messages were found.", obe_messages);
	}
	
	/**
	 * Test adding a new subscriber and a new filter, and verifying
	 * that the router picked it up within the configured refresh
	 * interval.
	 */
	@Test
	public void testAddNewSubscriber() throws Exception {
		System.out.println(">>> Running testAddNewSubscriber() ...");
		
		DatabaseUtil.addSubscriber(session, rhsdw);
		DatabaseUtil.addFilter(session, rhsdw_f1);
		
		try {
			// Give the background thread some time to update the index
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		Filter found = null;
		int retries = 5;
		while (retries > 0) {
			found = findFilter(10000003, true);
			if (found != null) break;
			try { Thread.sleep(3000); } catch (InterruptedException ignore) {}
			retries--;
		}
		
		if (found != null) {
			assertEquals(10000003, found.getSubscriberId().intValue());
			assertTrue(found.getType().equals("VsmType"));
			assertNotNull(found.getBoundingBox());
			assertTrue(Double.compare(43.0, found.getBoundingBox().getNWLat()) == 0);
			assertTrue(Double.compare(-84.0, found.getBoundingBox().getNWLon()) == 0);
			assertTrue(Double.compare(42.0, found.getBoundingBox().getSELat()) == 0);
			assertTrue(Double.compare(-83.0, found.getBoundingBox().getSELon()) == 0);
		} else {
			Assert.fail("Failed to locate the new subscriber 'RHSDW' and filter.");
		}
	}
	
	/**
	 * Test matching a spatial region filter.
	 */
	@Test
	public void testSpatialRegionMatch() {
		System.out.println(">>> Running testSpatialRegionMatch() ...");
		
		DatabaseUtil.addSubscriber(session, rhsdw);
		DatabaseUtil.addFilter(session, rhsdw_f1);
		
		try {
			// Give the background thread some time to update the index
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		router1.setNumRecordsToSend(1);
		router1.setRecord(spatialMatchRecord);
		
		Thread sim_t = new Thread(router1);
		sim_t.start();
		
		try {
			// Give the subscribers some time to collect all the messages sent
			Thread.sleep(10 * 1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		List<String> tmc_messages = MessageCollector.getInstance().get("10000000");
		assertNotNull("Subscriber 10000000 expecting matches but no messages found.", tmc_messages);
		assertTrue("Expecting 1 message for the TMC subscriber but got " + tmc_messages.size() + ".", tmc_messages.size() == 1);
		List<String> rhsdw_messages = MessageCollector.getInstance().get("10000003");
		assertNotNull("Subscriber 10000003 expecting matches but no messages found.", rhsdw_messages);
		assertTrue("Expecting 1 message for the RHSDW subscriber but got " + rhsdw_messages.size() + ".", rhsdw_messages.size() == 1);
	}
	
	/**
	 * Test not matching a spatial region filter.
	 */
	@Test
	public void testNoSpatialRegionMatch() {
		System.out.println(">>> Running testNoSpatialRegionMatch() ...");
		
		DatabaseUtil.addSubscriber(session, rhsdw);
		DatabaseUtil.addFilter(session, rhsdw_f1);
		
		try {
			// Give the background thread some time to update the index
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		router1.setNumRecordsToSend(1);
		router1.setRecord(noSpatialMatchRecord);
		
		Thread sim_t = new Thread(router1);
		sim_t.start();
		
		try {
			// Give the subscribers some time to collect all the messages sent
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		assertNull("RHSDW expecting no matches but got messages.", MessageCollector.getInstance().get("10000003"));
	}
	
	@Test
	public void testRecordNotWithinTimeRange() {
		System.out.println(">>> Running testRecordNotWithinTimeRange() ...");

		router1.setNumRecordsToSend(1);
		router1.setRecord(notWithinTimeRange);
		
		Thread sim_t = new Thread(router1);
		sim_t.start();
		
		try {
			// Give the subscribers some time to collect all the messages sent
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		List<String> tmc_messages = MessageCollector.getInstance().get("10000000");
		assertNull("Subscriber 10000000 expecting no matches but got messages were found.", tmc_messages);
		List<String> pc_messages = MessageCollector.getInstance().get("10000001");
		assertNull("Subscriber 10000001 expecting no matches but got messages were found.", pc_messages);
		List<String> obe_messages = MessageCollector.getInstance().get("10000002");
		assertNull("Subscriber 10000002 expecting no matches but got messages were found.", obe_messages);
	}
	
	/**
	 * Build a set of records, subscribers, and filters that are used by the 
	 * individual test cases.
	 */
	private static void buildTestCaseSpecificDataSet() throws Exception {
		StringBuilder sb = new StringBuilder();
		sb.append('{');
			sb.append("\"standardHeader\": {");
			sb.append("\"uuid\": \"88aec29c-99ed-4199-ab8c-a7c681c7a9d1\",");
			sb.append("\"source\": \"UNKNOWN\",");
			sb.append("\"accessLabel\": \"UNCLASSIFIED\",");
			sb.append("\"modelName\": \"vehSitDataMessage\",");
			sb.append("\"modelVersion\": \"1.3\"");
		sb.append("},");
		sb.append("\"dialogId\": 155,"); 
		sb.append("\"sequenceId\": 5,"); 
		sb.append("\"type\": 1,"); 
		sb.append("\"vsmType\": 1,"); 
		sb.append("\"year\": 2014,");
		sb.append("\"month\": 2,");
		sb.append("\"day\": 10,");
		sb.append("\"hour\": 8,");
		sb.append("\"minute\": 30,");
		sb.append("\"second\": 25,");
		sb.append("\"lat\": 42.44783187,"); // lat within processing center bounding box
		sb.append("\"long\": -83.43090838,"); // lon within processing center bounding box
		sb.append("\"encodedMsg\":\"" + BASE64_SIT_DATA + "\"");
		sb.append('}');
		sampleRecord = JSONObject.fromObject(sb.toString());
		
		sb = new StringBuilder();
		sb.append('{');
			sb.append("\"standardHeader\": {");
			sb.append("\"uuid\": \"88aec29c-99ed-4199-ab8c-a7c681c7a9d1\",");
			sb.append("\"source\": \"UNKNOWN\",");
			sb.append("\"accessLabel\": \"UNCLASSIFIED\",");
			sb.append("\"modelName\": \"vehSitDataMessage\",");
			sb.append("\"modelVersion\": \"1.3\"");
		sb.append("},");
		sb.append("\"dialogId\": 155,"); 
		sb.append("\"sequenceId\": 5,"); 
		sb.append("\"vsmType\": 55,"); // no such vsm type
		sb.append("\"year\": 2014,");
		sb.append("\"month\": 2,");
		sb.append("\"day\": 10,");
		sb.append("\"hour\": 8,");
		sb.append("\"minute\": 30,");
		sb.append("\"second\": 25,");
		sb.append("\"lat\": 42.44783187,");
		sb.append("\"long\": -83.43090838,");
		sb.append("\"encodedMsg\":\"" + BASE64_SIT_DATA + "\"");
		sb.append('}');
		noBundleTypeMatchRecord = JSONObject.fromObject(sb.toString());
		
		sb = new StringBuilder();
		sb.append('{');
			sb.append("\"standardHeader\": {");
			sb.append("\"uuid\": \"88aec29c-99ed-4199-ab8c-a7c681c7a9d1\",");
			sb.append("\"source\": \"UNKNOWN\",");
			sb.append("\"accessLabel\": \"UNCLASSIFIED\",");
			sb.append("\"modelName\": \"vehSitDataMessage\",");
			sb.append("\"modelVersion\": \"1.3\"");
		sb.append("},");
		sb.append("\"dialogId\": 155,"); 
		sb.append("\"sequenceId\": 5,"); 
		sb.append("\"vsmType\": 1,");
		sb.append("\"year\": 2014,");
		sb.append("\"month\": 2,");
		sb.append("\"day\": 10,");
		sb.append("\"hour\": 5,");
		sb.append("\"minute\": 30,");
		sb.append("\"second\": 15,");
		sb.append("\"lat\": 42.5,");
		sb.append("\"long\": -83.5,");
		sb.append("\"encodedMsg\":\"" + BASE64_SIT_DATA + "\"");
		sb.append('}');
		spatialMatchRecord = JSONObject.fromObject(sb.toString());
		
		sb = new StringBuilder();
		sb.append('{');
			sb.append("\"standardHeader\": {");
			sb.append("\"uuid\": \"88aec29c-99ed-4199-ab8c-a7c681c7a9d1\",");
			sb.append("\"source\": \"UNKNOWN\",");
			sb.append("\"accessLabel\": \"UNCLASSIFIED\",");
			sb.append("\"modelName\": \"vehSitDataMessage\",");
			sb.append("\"modelVersion\": \"1.3\"");
		sb.append("},");
		sb.append("\"dialogId\": 155,"); 
		sb.append("\"sequenceId\": 5,"); 
		sb.append("\"vsmType\": 1,");
		sb.append("\"year\": 2014,");
		sb.append("\"month\": 2,");
		sb.append("\"day\": 10,");
		sb.append("\"hour\": 5,");
		sb.append("\"minute\": 30,");
		sb.append("\"second\": 15,");
		sb.append("\"lat\": 41.5,");
		sb.append("\"long\": -82.5,");
		sb.append("\"encodedMsg\":\"" + BASE64_SIT_DATA + "\"");
		sb.append('}');
		noSpatialMatchRecord = JSONObject.fromObject(sb.toString());
		
		sb = new StringBuilder();
		sb.append('{');
			sb.append("\"standardHeader\": {");
			sb.append("\"uuid\": \"88aec29c-99ed-4199-ab8c-a7c681c7a9d1\",");
			sb.append("\"source\": \"UNKNOWN\",");
			sb.append("\"accessLabel\": \"UNCLASSIFIED\",");
			sb.append("\"modelName\": \"vehSitDataMessage\",");
			sb.append("\"modelVersion\": \"1.3\"");
		sb.append("},");
		sb.append("\"dialogId\": 155,"); 
		sb.append("\"sequenceId\": 5,"); 
		sb.append("\"vsmType\": 1,");
		sb.append("\"year\": 4013,");
		sb.append("\"month\": 2,");
		sb.append("\"day\": 10,");
		sb.append("\"hour\": 8,");
		sb.append("\"minute\": 30,");
		sb.append("\"second\": 25,");
		sb.append("\"lat\": 42.44783187,"); 
		sb.append("\"long\": -83.43090838,"); 
		sb.append("\"encodedMsg\":\"" + BASE64_SIT_DATA + "\"");
		sb.append('}');
		notWithinTimeRange = JSONObject.fromObject(sb.toString());
		
		// ***** END TIME *****
		
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		Calendar end = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		end.setTimeInMillis(df.parse("3020-02-28T10:10:00Z").getTime());
		
		// ***** SUBSCRIBERS *****
		
		Subscriber.Builder s = new Subscriber.Builder();
		s.setSubscriberId(10000003);
		s.setCertificate(CertificateUtil.generatePublicKey().getBytes("UTF-8"));
		s.setDestHost("127.0.0.1");
		s.setDestPort(7443);
		rhsdw = s.build();
		
		s = new Subscriber.Builder();
		s.setSubscriberId(10000004);
		s.setCertificate(CertificateUtil.generatePublicKey().getBytes("UTF-8"));
		s.setDestHost("127.0.0.1");
		s.setDestPort(7443);
		all = s.build();
		
		// ***** FILTERS *****
		
		Filter.Builder f = new Filter.Builder();
		BoundingBox.Builder bb = new BoundingBox.Builder();
		
		f.setSubscriberId(10000003);
		f.setRequestId(1001);
		f.setEndTime(end);
		f.setType("VsmType");
		f.setTypeValue(1);
		bb.setNWLat(43.0);
		bb.setNWLon(-84.0);
		bb.setSELat(42.0);
		bb.setSELon(-83.0);
		f.setBoundingBox(bb.build());
		rhsdw_f1 = f.build();
		
		f.setSubscriberId(10000004);
		f.setRequestId(1001);
		f.setEndTime(end);
		f.setType("VsmType");
		f.setTypeValue(31);
		all_f1 = f.build();
	}
	
	private static Filter findFilter(int subscriberId, boolean canRetry) {
		Filter found = null;
		int retries = 5;
		while (retries > 0) {
			Collection<Filter> filters = dao.findAll();
			if (filters != null) {
				for (Filter filter : filters) {
					if (filter.getSubscriberId() == subscriberId) {
						found = filter;
					}
				}
			}
			if (found != null || ! canRetry) break;
			try { Thread.sleep(3000); } catch (InterruptedException ignore) {}
		}
		return found;
	}

}