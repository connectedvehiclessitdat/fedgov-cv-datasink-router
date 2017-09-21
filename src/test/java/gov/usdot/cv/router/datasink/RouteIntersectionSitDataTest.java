package gov.usdot.cv.router.datasink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import gov.usdot.cv.common.model.Filter;
import gov.usdot.cv.resources.PrivateTestResourceLoader;
import gov.usdot.cv.router.datasink.model.SubscriptionDataSource;
import gov.usdot.cv.router.datasink.sim.RouterSimulator;
import gov.usdot.cv.router.datasink.subscriber.ChannelSubscriber;
import gov.usdot.cv.router.datasink.subscriber.MessageCollector;
import gov.usdot.cv.router.datasink.util.DatabaseUtil;

import java.io.File;
import java.io.IOException;
import java.sql.DriverManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;

import javax.sql.DataSource;

import net.sf.json.JSONObject;

import org.apache.activemq.broker.BrokerService;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.deleidos.rtws.commons.config.RtwsConfig;
import com.deleidos.rtws.commons.dao.jdbc.DataAccessSession;
import com.deleidos.rtws.commons.dao.source.H2ConnectionPool;

public class RouteIntersectionSitDataTest {
	
	private static final String JDBC_URL = "jdbc:h2:%s";
	private static final String JMS_BROKER_NAME = "junit-router";
	private static final String JMS_CONNECTOR_URL = "tcp://localhost:61619";
	private static final String JMS_USERNAME = "test";
	private static final String JMS_PASSWORD =
			PrivateTestResourceLoader.getProperty("@datasink-router/route.intersection.sit.data.test.password@");
	private static DataSource dataSource;
	private static DataAccessSession session;
	
	private static BrokerService broker;
	private static List<ChannelSubscriber> subscribers = new ArrayList<ChannelSubscriber>();
	
	private static RouterSimulator router1;
	private static RouterSimulator router2;
	private static RouterSimulator router3;
	private static RouterSimulator router4;
	
	private static final String BASE64_SIT_DATA = "MIGTgAIAooEBBYIEAAAD6YMBAYQBAaUcoAyABBmhR4CBBM1WB4ChDIAEGHAagIEEzx/LAKZgoAqAAQeBAQGHAgAAoVKgE4ACB92BAQyCAQmDAQmEAR6FAR6hO4AXU2FtcGxlIEludGVyc2VjdGlvbiBbMF2BBAAAA1mCAQGDAicQhAECpQowCIICAQKGAicVhgEBhwEC";
	
	private static JSONObject sampleRecord;
	private static JSONObject noBundleTypeMatchRecord;
	private static JSONObject notWithinTimeRange;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		Properties testProperties = System.getProperties();
		if (testProperties.getProperty("RTWS_CONFIG_DIR") == null) {
			testProperties.setProperty("RTWS_CONFIG_DIR", testProperties.getProperty("basedir", "."));
			try {
				testProperties.load(
						PrivateTestResourceLoader.getFileAsStream(
								"@properties/datasink-query-processor-filtering.properties@"));

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
	
	/**
	 * Test sending a single record from a single router and having the subscribers 
	 * pick up that one message.
	 */
	@Test
	public void testIsdTypeAndSpatialRegionMatch() {
		System.out.println(">>> Running testIsdTypeAndSpatialRegionMatch() ...");
		
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
	
	/**
	 * Test sending a record that doesn't match any filters.
	 */
	@Test
	public void testNoSpatialRegionMatch() throws Exception {
		System.out.println(">>> Running testNoSpatialRegionMatch() ...");
		
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
		assertTrue("Expecting 1 messages for the 10000000 subscriber but got " + tmc_messages.size() + ".", tmc_messages.size() == 1);
		assertEquals(BASE64_SIT_DATA, tmc_messages.get(0));
		List<String> pc_messages = MessageCollector.getInstance().get("10000001");
		assertNull("Subscriber 10000001 expecting no matches but got messages were found.", pc_messages);
		List<String> obe_messages = MessageCollector.getInstance().get("10000002");
		assertNull("Subscriber 10000002 expecting no matches but got messages were found.", obe_messages);
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
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		
		Calendar recordDate = Calendar.getInstance();
		recordDate.setTimeZone(TimeZone.getTimeZone(Filter.UTC_TIMEZONE));
		recordDate.setTimeInMillis(System.currentTimeMillis());
		
		StringBuilder sb = new StringBuilder();
		sb.append('{');
			sb.append("\"standardHeader\": {");
			sb.append("\"uuid\": \"88aec29c-99ed-4199-ab8c-a7c681c7a9d1\",");
			sb.append("\"source\": \"UNKNOWN\",");
			sb.append("\"accessLabel\": \"UNCLASSIFIED\",");
			sb.append("\"modelName\": \"intersectionSitData\",");
			sb.append("\"modelVersion\": \"1.1\"");
		sb.append("},");
		sb.append("\"dialogId\": 162,"); 
		sb.append("\"sequenceId\": 5,"); 
		sb.append("\"bundleNumber\": 295748,");
		sb.append("\"timeToLive\": 1,");
		sb.append("\"timestamp\": \"" + formatter.format(recordDate.getTime()) + "\","); 
		sb.append("\"nwPos\": {");
			sb.append("\"lat\": 43.0,");
			sb.append("\"lon\": -85.0");
		sb.append("},");
		sb.append("\"sePos\": {");
			sb.append("\"lat\": 41.0,");
			sb.append("\"lon\": -82.0");
		sb.append("},");
		sb.append("\"encodedMsg\":\"" + BASE64_SIT_DATA + "\"");
		sb.append('}');
		sampleRecord = JSONObject.fromObject(sb.toString());
		
		sb = new StringBuilder();
		sb.append('{');
			sb.append("\"standardHeader\": {");
			sb.append("\"uuid\": \"88aec29c-99ed-4199-ab8c-a7c681c7a9d1\",");
			sb.append("\"source\": \"UNKNOWN\",");
			sb.append("\"accessLabel\": \"UNCLASSIFIED\",");
			sb.append("\"modelName\": \"intersectionSitData\",");
			sb.append("\"modelVersion\": \"1.1\"");
		sb.append("},");
		sb.append("\"dialogId\": 162,"); 
		sb.append("\"sequenceId\": 5,"); 
		sb.append("\"bundleNumber\": 295748,");
		sb.append("\"timeToLive\": 1,");
		sb.append("\"timestamp\": \"" + formatter.format(recordDate.getTime()) + "\",");
		// bounding box of the Washington, DC area.
		sb.append("\"nwPos\": {");
			sb.append("\"lat\": 39.072122,");
			sb.append("\"lon\": -77.318146");
		sb.append("},");
		sb.append("\"sePos\": {");
			sb.append("\"lat\": 38.754763,");
			sb.append("\"lon\": -76.749604");
		sb.append("},");
		sb.append("\"encodedMsg\":\"" + BASE64_SIT_DATA + "\"");
		sb.append('}');
		noBundleTypeMatchRecord = JSONObject.fromObject(sb.toString());
		
		Calendar futureRecordDate = Calendar.getInstance();
		futureRecordDate.setTimeZone(TimeZone.getTimeZone(Filter.UTC_TIMEZONE));
		futureRecordDate.setTimeInMillis(System.currentTimeMillis());
		futureRecordDate.set(Calendar.YEAR, 4013);
		
		sb = new StringBuilder();
		sb.append('{');
			sb.append("\"standardHeader\": {");
			sb.append("\"uuid\": \"88aec29c-99ed-4199-ab8c-a7c681c7a9d1\",");
			sb.append("\"source\": \"UNKNOWN\",");
			sb.append("\"accessLabel\": \"UNCLASSIFIED\",");
			sb.append("\"modelName\": \"intersectionSitData\",");
			sb.append("\"modelVersion\": \"1.1\"");
		sb.append("},");
		sb.append("\"dialogId\": 162,"); 
		sb.append("\"sequenceId\": 5,"); 
		sb.append("\"bundleNumber\": 295748,");
		sb.append("\"timeToLive\": 1,");
		sb.append("\"timestamp\": \"" + formatter.format(futureRecordDate.getTime()) + "\",");
		sb.append("\"nwPos\": {");
			sb.append("\"lat\": 43.0,");
			sb.append("\"lon\": -85.0");
		sb.append("},");
		sb.append("\"sePos\": {");
			sb.append("\"lat\": 41.0,");
			sb.append("\"lon\": -82.0");
		sb.append("},");
		sb.append("\"encodedMsg\":\"" + BASE64_SIT_DATA + "\"");
		sb.append('}');
		notWithinTimeRange = JSONObject.fromObject(sb.toString());
	}
}