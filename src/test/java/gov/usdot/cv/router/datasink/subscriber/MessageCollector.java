package gov.usdot.cv.router.datasink.subscriber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageCollector {
	
	private Map<String, List<String>> messages =
		new HashMap<String, List<String>>();
	
	private static class MessageCollectorHolder { 
		public static final MessageCollector INSTANCE = new MessageCollector();
	}
	
	public static MessageCollector getInstance() {
		return MessageCollectorHolder.INSTANCE;
	}
	
	private MessageCollector() { }
	
	public synchronized void add(String subscriberId, String message) {
		List<String> items = messages.get(subscriberId);
		if (items == null) {
			items = new ArrayList<String>();
			messages.put(subscriberId, items);
		}
		items.add(message);
	}
	
	public synchronized List<String> get(String subscriberId) {
		return messages.get(subscriberId);
	}
	
	public synchronized void clear(String subscriberId) {
		List<String> items = messages.get(subscriberId);
		if (items != null) {
			items.clear();
		}
	}
	
	public synchronized void clearAll() {
		messages.clear();
	}
	
}