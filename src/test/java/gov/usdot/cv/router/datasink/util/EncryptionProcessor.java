package gov.usdot.cv.router.datasink.util;

import java.security.PrivateKey;
import java.security.PublicKey;

public class EncryptionProcessor {
	
	public String encryptWithPublicKey(String message, PublicKey key) {
		return RSAAlgorithmUtil.encrypt(message, key);
	}
	
	public String decryptWithPrivateKey(String message, PrivateKey key) {
		return RSAAlgorithmUtil.decrypt(message, key);
	}
	
	public String encryptWithSymmetricKey(String message, String key) {
		return AESAlgorithmUtil.encrypt(message, key.getBytes());
	}
	
	public String decryptWithSymmetricKey(String message, String key) {
		return AESAlgorithmUtil.decrypt(message, key.getBytes());
	}
	
}