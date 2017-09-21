package gov.usdot.cv.router.datasink.index;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Index<K,V> {
	private Map<K,V> keyValuePairs;
	
	private Index(Map<K,V> keyPairs) {
		this.keyValuePairs = keyPairs;
	}
	
	public Set<K> keySet() {
		return this.keyValuePairs.keySet();
	}
	
	public V getValue(K key) {
		return this.keyValuePairs.get(key);
	}
	
	public void clear() {
		this.keyValuePairs.clear();
	}
	
	public static class Builder<K,V> {
		private Map<K,V> keyValuePairs = new HashMap<K,V>();
		
		public Builder<K,V> add(K key, V value) {
			keyValuePairs.put(key, value);
			return this;
		}
		
		public Index<K,V> build() {
			return new Index<K,V>(Collections.unmodifiableMap(keyValuePairs));
		}
	}
}