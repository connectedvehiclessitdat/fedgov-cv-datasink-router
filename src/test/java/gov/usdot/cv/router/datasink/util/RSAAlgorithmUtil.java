package gov.usdot.cv.router.datasink.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.crypto.Cipher;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

public class RSAAlgorithmUtil {

	private final static Logger logger = Logger.getLogger(RSAAlgorithmUtil.class);
	private final static String ALGORITHM = "RSA";

	public static boolean generateKeyPair(
			String privateKeyPath, 
			String publicKeyPath) throws NoSuchAlgorithmException, IOException {
		File privateKeyFile = new File(privateKeyPath);
		File publicKeyFile = new File(publicKeyPath);
		
		if (privateKeyFile.exists() && publicKeyFile.exists()) {
			return false;
		}

		// Create files to store public and private key
		if (privateKeyFile.getParentFile() != null) {
			privateKeyFile.getParentFile().mkdirs();
		}
		privateKeyFile.createNewFile();

		if (publicKeyFile.getParentFile() != null) {
			publicKeyFile.getParentFile().mkdirs();
		}
		publicKeyFile.createNewFile();
		
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
		keyGen.initialize(1024);
		KeyPair key = keyGen.generateKeyPair();

		// Saving the Public key in a file
		ObjectOutputStream publicKeyOS = new ObjectOutputStream(new FileOutputStream(publicKeyFile));
		publicKeyOS.writeObject(key.getPublic());
		publicKeyOS.close();

		// Saving the Private key in a file
		ObjectOutputStream privateKeyOS = new ObjectOutputStream(new FileOutputStream(privateKeyFile));
		privateKeyOS.writeObject(key.getPrivate());
		privateKeyOS.close();
		
		return true;
	}

	public static String encrypt(String text, PublicKey key) {
		String encrypted = null;

		try {
			final Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, key);
			encrypted = Base64.encodeBase64String(cipher.doFinal(text.getBytes()));
		} catch (Exception ex) {
			logger.error("Failed to encrypt data.", ex);
		}

		return encrypted;
	}

	public static String decrypt(String text, PrivateKey key) {
		String decrypted = null;

		try {
			final Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, key);
			decrypted = new String(cipher.doFinal(Base64.decodeBase64(text)));
		} catch (Exception ex) {
			logger.error("Failed to decrypt data.", ex);
		}

		return decrypted;
	}
	
	public static Key loadKey(String filePath) throws FileNotFoundException, IOException, ClassNotFoundException {
		ObjectInputStream inputStream = null;
		try {
		inputStream = new ObjectInputStream(new FileInputStream(filePath));
		return (Key) inputStream.readObject();
		} finally {
			if ( inputStream != null ) {
				inputStream.close();
			}
		}
	}

	public static void main(String [] args) throws Exception {
		String privateKeyPath = "/tmp/private.key";
		String publicKeyPath = "/tmp/public.key";
		
		File privFile = new File(privateKeyPath);
		File publicFile = new File(publicKeyPath);
		
		if (! privFile.exists() && ! publicFile.exists()) {
			generateKeyPair(privateKeyPath, publicKeyPath);
		}
		
		PrivateKey privateKey = (PrivateKey) loadKey(privateKeyPath);
		PublicKey publicKey = (PublicKey) loadKey(publicKeyPath);

		String text = "Text to be encrypted.";
		
		String encrypted = encrypt(text, publicKey);
		String decrypted = decrypt(encrypted, privateKey);
		
		System.out.println("Original Text: " + text);
		System.out.println("Encrypted Text: " + encrypted);
		System.out.println("Decrypted Text: " + decrypted);
	}

}