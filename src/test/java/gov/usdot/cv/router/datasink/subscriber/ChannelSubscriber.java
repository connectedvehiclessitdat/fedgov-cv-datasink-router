package gov.usdot.cv.router.datasink.subscriber;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.deleidos.rtws.commons.exception.InitializationException;

public class ChannelSubscriber {
	
	private String brokerUrl;
	private String username;
	private String password;
	private String subscriberId;
	
	private Connection conn = null;
	private Session session = null;
	private MessageConsumer consumer = null;
	
	public ChannelSubscriber(
			String brokerUrl, 
			String username, 
			String password,
			String subscriberId) throws JMSException {
		this.brokerUrl = brokerUrl;
		this.username = username;
		this.password = password;
		this.subscriberId = subscriberId;
		initialize();
	}
	
	public void initialize() {
		ConnectionFactory factory = new ActiveMQConnectionFactory(this.username, this.password, brokerUrl);
		try {
			this.conn = factory.createConnection();
			this.session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
			this.consumer = session.createConsumer(session.createTopic(this.subscriberId));
			this.consumer.setMessageListener(new VSDMListener(this.subscriberId));
			this.conn.start();
		} catch (JMSException jmse) {
			throw new InitializationException("Failed to initialize channel subscriber '" + this.subscriberId + "'.", jmse);
		}
	}
	
}