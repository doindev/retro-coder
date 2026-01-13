package org.me.retrocoder.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.me.retrocoder.security.PathValidator;

import static org.junit.jupiter.api.Assertions.*;

class PathValidatorTest {

    private PathValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PathValidator();
    }

    @Test
    void nullPath() {
        assertFalse(validator.isAllowed(null));
    }

    @Test
    void emptyPath() {
        assertFalse(validator.isAllowed(""));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void blockedWindowsPaths() {
        assertFalse(validator.isAllowed("C:\\Windows"));
        assertFalse(validator.isAllowed("C:\\Windows\\System32"));
        assertFalse(validator.isAllowed("C:\\Program Files"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void blockedUnixPaths() {
        assertTrue(validator.isSystemPath("/usr"));
        assertTrue(validator.isSystemPath("/etc"));
        assertTrue(validator.isSystemPath("/var"));
    }

    @Test
    void sensitiveHomePaths() {
        String home = System.getProperty("user.home");
        assertTrue(validator.isSensitiveHomePath(home + "/.ssh"));
        assertTrue(validator.isSensitiveHomePath(home + "/.aws"));
        assertTrue(validator.isSensitiveHomePath(home + "/.gnupg"));
    }

    @Test
    void sensitiveFiles() {
        assertTrue(validator.isSensitiveFile(".env"));
        assertTrue(validator.isSensitiveFile(".env.local"));
        assertTrue(validator.isSensitiveFile("private.key"));
        assertTrue(validator.isSensitiveFile("cert.pem"));
        assertTrue(validator.isSensitiveFile("credentials.json"));
    }

    @Test
    void normalFiles() {
        assertFalse(validator.isSensitiveFile("app.js"));
        assertFalse(validator.isSensitiveFile("index.html"));
        assertFalse(validator.isSensitiveFile("package.json"));
    }

    @Test
    void pathTraversal() {
        assertTrue(validator.containsTraversal("../etc/passwd"));
        assertTrue(validator.containsTraversal("..\\Windows\\System32"));
        assertTrue(validator.containsTraversal("%2e%2e/etc/passwd"));
    }

    @Test
    void sanitizePath() {
        String sanitized = validator.sanitize("./test/../test.txt");
        assertNotNull(sanitized);
        assertFalse(sanitized.contains(".."));
    }
}
