package gov.usdot.cv.router.datasink.util;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Base64;

public class CertificateUtil {
	
	private final static String ALGORITHM = "RSA";
	
	public static String generatePublicKey() throws NoSuchAlgorithmException {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
		keyGen.initialize(1024);
		KeyPair key = keyGen.generateKeyPair();
		byte [] publicKey = key.getPublic().getEncoded();
		return Base64.encodeBase64String(publicKey);
	}
	
}