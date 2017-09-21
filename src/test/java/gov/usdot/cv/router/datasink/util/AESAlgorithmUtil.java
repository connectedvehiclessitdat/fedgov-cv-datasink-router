package gov.usdot.cv.router.datasink.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

public class AESAlgorithmUtil {
	
	private final static Logger logger = Logger.getLogger(AESAlgorithmUtil.class);
	private final static String TRANSFORMATION = "AES/ECB/PKCS5Padding";
	private final static String ALGORITHM = "AES";
	
	public static String encrypt(String text, byte [] key) {
		try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            final SecretKeySpec secretKey = new SecretKeySpec(key, ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            final String encrypted = Base64.encodeBase64String(cipher.doFinal(text.getBytes()));
            return encrypted;
        } catch (Exception ex) {
            logger.error("Failed to encrypt data.", ex);
        }
		return null;
	}
	
	public static String decrypt(String text, byte [] key) {
		try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            final SecretKeySpec secretKey = new SecretKeySpec(key, ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            final String decrypted = new String(cipher.doFinal(Base64.decodeBase64(text)));
            return decrypted;
        }  catch (Exception ex) {
            logger.error("Failed to decrypt data.", ex);
        }
        return null;
	}
	
	public static void main(String [] args) {
		String secretKey = "thisIsASecretKey";
		
		String text = "Text to be encrypted.";
		
		String encrypted = encrypt(text, secretKey.getBytes());
		String decrypted = decrypt(encrypted, secretKey.getBytes());
		
		System.out.println("Original Text: " + text);
		System.out.println("Encrypted Text: " + encrypted);
		System.out.println("Decrypted Text: " + decrypted);
	}
	
}