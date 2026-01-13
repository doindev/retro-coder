package org.me.retrocoder.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.me.retrocoder.config.RetrocoderProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Service for encrypting and decrypting sensitive data (API credentials).
 * Uses AES-256-GCM encryption with PBKDF2 key derivation.
 *
 * Supports three-tier password resolution:
 * 1. RETROCODER_ENCRYPTION_KEY environment variable
 * 2. Application config (retrocoder.security.encryption-key)
 * 3. Session password (set via unlock endpoint)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 65536;
    private static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String ENV_KEY_NAME = "RETROCODER_ENCRYPTION_KEY";

    private final RetrocoderProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    // Session-based encryption key (derived from password)
    private volatile SecretKey sessionKey;
    private volatile String passwordSource;

    /**
     * Initialize encryption service on startup.
     * Attempts to auto-unlock using environment variable or config.
     */
    @PostConstruct
    public void init() {
        // Try environment variable first
        String envKey = System.getenv(ENV_KEY_NAME);
        if (envKey != null && !envKey.isBlank()) {
            try {
                sessionKey = deriveKey(envKey);
                passwordSource = "environment";
                log.info("Encryption service unlocked using environment variable");
                return;
            } catch (Exception e) {
                log.warn("Failed to derive key from environment variable: {}", e.getMessage());
            }
        }

        // Try application config
        String configKey = properties.getSecurity() != null ? properties.getSecurity().getEncryptionKey() : null;
        if (configKey != null && !configKey.isBlank()) {
            try {
                sessionKey = deriveKey(configKey);
                passwordSource = "config";
                log.info("Encryption service unlocked using application config");
                return;
            } catch (Exception e) {
                log.warn("Failed to derive key from application config: {}", e.getMessage());
            }
        }

        log.info("Encryption service requires manual unlock - no key found in environment or config");
    }

    /**
     * Check if the encryption service is unlocked and ready to use.
     */
    public boolean isUnlocked() {
        return sessionKey != null;
    }

    /**
     * Get the source of the current encryption password.
     * @return "environment", "config", "session", or null if locked
     */
    public String getPasswordSource() {
        return passwordSource;
    }

    /**
     * Unlock the encryption service with a password.
     * The password is used to derive the encryption key.
     *
     * @param password The password to use for encryption
     * @throws IllegalArgumentException if password is null or empty
     */
    public void unlock(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        try {
            sessionKey = deriveKey(password);
            passwordSource = "session";
            log.info("Encryption service unlocked using session password");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive encryption key", e);
        }
    }

    /**
     * Lock the encryption service, clearing the session key.
     * Only clears session-based keys, not env/config-based ones.
     */
    public void lock() {
        if ("session".equals(passwordSource)) {
            sessionKey = null;
            passwordSource = null;
            log.info("Encryption service locked");
        }
    }

    /**
     * Encrypt a plaintext string.
     *
     * @param plaintext The text to encrypt
     * @return Base64-encoded encrypted string (includes IV)
     * @throws IllegalStateException if service is not unlocked
     */
    public String encrypt(String plaintext) {
        if (!isUnlocked()) {
            throw new IllegalStateException("Encryption service is locked. Please unlock first.");
        }
        if (plaintext == null) {
            return null;
        }

        try {
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, sessionKey, parameterSpec);

            // Encrypt
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt an encrypted string.
     *
     * @param encryptedText Base64-encoded encrypted string (includes IV)
     * @return The decrypted plaintext
     * @throws IllegalStateException if service is not unlocked
     */
    public String decrypt(String encryptedText) {
        if (!isUnlocked()) {
            throw new IllegalStateException("Encryption service is locked. Please unlock first.");
        }
        if (encryptedText == null || encryptedText.isBlank()) {
            return null;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);

            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, sessionKey, parameterSpec);

            // Decrypt
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed - wrong password or corrupted data", e);
        }
    }

    /**
     * Validate a password by attempting to decrypt a test value.
     *
     * @param password The password to validate
     * @param encryptedTestValue An encrypted value to test against
     * @return true if the password can decrypt the test value
     */
    public boolean validatePassword(String password, String encryptedTestValue) {
        if (password == null || encryptedTestValue == null) {
            return false;
        }
        try {
            SecretKey testKey = deriveKey(password);
            byte[] decoded = Base64.getDecoder().decode(encryptedTestValue);

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, testKey, parameterSpec);
            cipher.doFinal(ciphertext);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Derive an AES key from a password using PBKDF2.
     */
    private SecretKey deriveKey(String password) throws Exception {
        // Use a fixed salt derived from the password itself for deterministic key derivation
        // This allows the same password to always produce the same key
        byte[] salt = ("retrocoder-salt-" + password.hashCode()).getBytes(StandardCharsets.UTF_8);

        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);

        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }
}
