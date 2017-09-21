package gov.usdot.cv.router.datasink.jms;

import gov.usdot.cv.common.model.Subscriber;
import gov.usdot.cv.common.util.InstanceMetadataUtil;
import gov.usdot.cv.common.util.PropertyLocator;
import gov.usdot.cv.router.datasink.index.SubscriptionManager;
import gov.usdot.cv.security.SecurityHelper;
import gov.usdot.cv.security.crypto.CryptoProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import javax.jms.BytesMessage;
import javax.jms.JMSException;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

import com.deleidos.rtws.commons.exception.InitializationException;
import com.deleidos.rtws.commons.net.jms.BasicMessageProducer;
import com.deleidos.rtws.commons.net.jms.JMSFactory;
import com.deleidos.rtws.commons.net.jms.RoundRobinJMSConnectionFactory;
import com.deleidos.rtws.commons.util.Initializable;

public class TopicProducerManager implements Initializable  {
	
	private static Logger logger = Logger.getLogger(TopicProducerManager.class);
	
	private static final String RTWS_DOMAIN 		  	= "RTWS_DOMAIN";
	private static final String CV_SITDATA_TOPIC_NAME 	= "cv.sitdata";
	private static final int Psid = 0x2fe1;
	
	/** Lock used to synchronize producer updates. */
	private final ReadLock readLock;

	/** Lock used to synchronize producer updates. */
	private final WriteLock writeLock;
	
	/** Stores a collection of active topic producers. */
	private HashMap<Integer, BasicMessageProducer> producers =
		new HashMap<Integer, BasicMessageProducer>();
	
	/** Producers dedicated to send record to custom topics. */
	private List<BasicMessageProducer> customProducers;
	
	/** Used to construct topic producer objects. */
	private JMSFactory factory;
	
	/** The subscription manager we are interested in listening to events. */
	private SubscriptionManager manager;
	
	/** Indicates if the object have been initialized. */
	private boolean initialized = false;
	
	/** Indicates if the object have been disposed. */
	private boolean disposed = false;
	
	/** Indicate whether this node will be the one creating the topic. */
	private boolean topicActivator = false;
	
	private CryptoProvider cryptoProvider;
	
	private static class TopicProducerManagerHolder { 
		public static final TopicProducerManager INSTANCE = new TopicProducerManager();
	}
	
	public static TopicProducerManager newInstance() {
		return TopicProducerManagerHolder.INSTANCE;
	}
	
	private TopicProducerManager() {
		super();
		ReentrantReadWriteLock semaphore = new ReentrantReadWriteLock();
		readLock = semaphore.readLock();
		writeLock = semaphore.writeLock();
		cryptoProvider = new CryptoProvider();
	}
	
	public void initialize() {
		writeLock.lock();
		try {
			if (! initialized) {
				SecurityHelper.initSecurity();
				
				// Only the first node for this datasink will create new topics
				if (InstanceMetadataUtil.getNodeNumber() == 1) {
					logger.info("This node has been marked as a topic activator.");
					topicActivator = true;
				}
				
				// Load the properties to connect to the pubsub server
				String pubsub_protocol = PropertyLocator.getString("pubsub.server.protocol", "nio");
				int pubsub_port = PropertyLocator.getInt("pubsub.server.port", 61617);
				String pubsub_domain = PropertyLocator.getString("pubsub.server.domain");
				if (pubsub_domain == null) {
					String rtwsDomain = PropertyLocator.getString(RTWS_DOMAIN);
					if (rtwsDomain == null) {
						throw new InitializationException("Missing system property 'RTWS_DOMAIN'.");
					}
					pubsub_domain = "pubsub-server." + rtwsDomain;
				}
				String username = PropertyLocator.getString("messaging.external.connection.user");
				if (username == null) {
					throw new InitializationException("Missing property 'messaging.external.connection.user'.");
				}
				String password = PropertyLocator.getString("messaging.external.connection.password");
				if (password == null) {
					throw new InitializationException("Missing property 'messaging.external.connection.password'.");
				}
				
				StringBuilder brokerURL = new StringBuilder();
				brokerURL.append(pubsub_protocol).append("://").append(pubsub_domain).append(':').append(pubsub_port);

				RoundRobinJMSConnectionFactory cf = new RoundRobinJMSConnectionFactory();
				cf.setBrokerURL(brokerURL.toString());
				cf.setUserName(username);
				cf.setPassword(password);
		
				this.factory = new JMSFactory();
				this.factory.setConnectionFactory(cf);
				this.manager.register(new UpdateListener());
				
				customProducers = new ArrayList<BasicMessageProducer>();
				customProducers.add(factory.createSimpleTopicProducer(CV_SITDATA_TOPIC_NAME));
				for (BasicMessageProducer session : customProducers) {
					if (topicActivator) {
						send("This topic is now active.".getBytes(), session);
					}
				}
				
				this.initialized = true;
				logger.info("The topic producer manager is initialized.");
			}
		} finally {
			writeLock.unlock();
		}
	}

	public void dispose() {
		writeLock.lock();
		try {
			if (! disposed) {
				SecurityHelper.disposeSecurity();
				
				Iterator<Entry<Integer, BasicMessageProducer>> it1 = 
					this.producers.entrySet().iterator();
				while (it1.hasNext()) {
					Entry<Integer, BasicMessageProducer> entry = it1.next();
					entry.getValue().close();
					it1.remove();
				}
				this.producers = null;
				
				Iterator<BasicMessageProducer> it2 = customProducers.iterator();
				while(it2.hasNext()) {
					BasicMessageProducer entry = it2.next();
					entry.close();
					it2.remove();
				}
				this.customProducers = null;
				
				this.disposed = true;
				logger.info("The topic producer manager is disposed.");
			}
		} finally {
			writeLock.unlock();
		}
	}
	
	public void setSubscriptionManager(SubscriptionManager manager) {
		this.manager = manager;
	}
	
	/**
	 * Send data to custom topics.
	 */
	public void send(String data) {
		readLock.lock();
		try {
			if (customProducers == null) return;
			for (BasicMessageProducer session : customProducers) {
				byte [] encrypted = Base64.decodeBase64(data);
				synchronized (session) {
					send(encrypted, session);
				}
			}
		} finally {
			readLock.unlock();
		}
	}
	
	/**
	 * Send data to a specific subscriber.
	 */
	public void send(Subscriber subscriber, String data) {
		readLock.lock();
		try {
			BasicMessageProducer session = this.producers.get(subscriber.getSubscriberId());
			if (session == null) {
				return;
			}
			
			byte [] encrypted = encrypt(data, subscriber.getCertificate());
			logger.debug(String.format("Encrypted situation data [%s].", Base64.encodeBase64String(encrypted)));
			synchronized (session) {
				send(encrypted, session);
			}
		} finally {
			readLock.unlock();
		}
	}
	
	private byte [] encrypt(String data, byte [] certificate) {
		byte[] payload = Base64.decodeBase64(data);
		if (certificate != null) {
			try {
				byte[] certID8 = SecurityHelper.registerCert(certificate, cryptoProvider);
				payload = SecurityHelper.encrypt(payload, certID8, cryptoProvider, Psid);
			} catch (Exception ex) {
				logger.error("Couldn't encrypt outgoing message. Reason: " + ex.getMessage(), ex);
			}
		}
		return payload;
	}
	
	private void send(
			byte [] data, 
			BasicMessageProducer session) {
		boolean sent = false;
		while (! sent) try {
			connect(session);
			session.send(buildMessage(data, session));
			sent = true;
		} catch (JMSException e) {
			session.close();
		}
	}
	
	private void connect(BasicMessageProducer session) {
		boolean stuck = false;
		while (! session.isConnected()) try {
			session.reset();
		} catch (JMSException e) {
			if (!stuck) {
				logger.warn("Unable to establish connection to pubsub server; waiting...", e);
				stuck = true;
			}
			try { Thread.sleep(300); } catch (InterruptedException ignore) { }
		}
	}
	
	private BytesMessage buildMessage(
			byte [] data,
			BasicMessageProducer session) throws JMSException {
		BytesMessage message = session.createBytesMessage();
		message.writeBytes(data);
		return message;
	}
	
	private class UpdateListener implements SubscriptionManager.TopicProducerListener {
		public void update(Set<Integer> subscribers) {
			writeLock.lock();
			try {
				// identify the subscribers that are no longer active
				List<Integer> unactiveList = new ArrayList<Integer>();
				for (Integer subscriberId : producers.keySet()) {
					if (! subscribers.contains(subscriberId)) {
						unactiveList.add(subscriberId);
					}
				}
				// remove session that are no longer active
				for (Integer subscriberId : unactiveList) {
					BasicMessageProducer producer = producers.remove(subscriberId);
					try {
						logger.info(String.format("Removing subscriber '%s' cached topic session.", subscriberId));
						producer.close(); 
					} catch (Exception ex) {
						logger.error(String.format("Failed to close producer session for '%s'", subscriberId), ex);
					}
				}
				// add new producer sessions
				for (Integer subscriberId : subscribers) {
					try {
						if (! producers.containsKey(subscriberId)) {
							BasicMessageProducer producer = factory.createSimpleTopicProducer(subscriberId.toString());
							producers.put(subscriberId, producer);
							if (topicActivator) {
								logger.info(String.format("Activating topic for subscriber '%s'.", subscriberId));
								send("This topic is now active.".getBytes(), producer);
								logger.info("Topic '" + subscriberId + "' is now active.");
							}
						}
					} catch (Exception ex) {
						logger.error(String.format("Failed to create producer session for '%s'", subscriberId), ex);
					}
				}
			} finally {
				writeLock.unlock();
			}
		}
	}
}