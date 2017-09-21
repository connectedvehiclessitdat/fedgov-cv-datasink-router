package gov.usdot.cv.router.datasink.subscriber;

import javax.jms.BytesMessage;
import javax.jms.Message;
import javax.jms.MessageListener;

import org.apache.commons.codec.binary.Base64;

public class VSDMListener implements MessageListener {
	
	private String subscriberId;

	public VSDMListener(String subscriberId) {
		this.subscriberId = subscriberId;
	}
	
	public void onMessage(Message msg) {
		if (msg != null && msg instanceof BytesMessage) {
			try {
				BytesMessage bmsg = (BytesMessage) msg;
				byte [] bytes = new byte[(int) bmsg.getBodyLength()];
				bmsg.readBytes(bytes);
				MessageCollector.getInstance().add(this.subscriberId, Base64.encodeBase64String(bytes));
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
	
}