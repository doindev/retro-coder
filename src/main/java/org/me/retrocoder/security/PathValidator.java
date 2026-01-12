package org.me.retrocoder.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Validates file paths for security.
 */
@Slf4j
@Component
public class PathValidator {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");
    private static final String USER_HOME = System.getProperty("user.home");

    /**
     * Check if a path is allowed for access.
     *
     * @param path The path to check
     * @return true if allowed, false if blocked
     */
    public boolean isAllowed(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        try {
            Path normalized = Paths.get(path).toAbsolutePath().normalize();
            String pathStr = normalized.toString();

            return !isSystemPath(pathStr) && !isSensitiveHomePath(pathStr);
        } catch (Exception e) {
            log.warn("Invalid path: {}", path, e);
            return false;
        }
    }

    /**
     * Check if a path is a system path.
     */
    public boolean isSystemPath(String path) {
        String normalizedPath = path.replace("\\", "/").toLowerCase();

        if (IS_WINDOWS) {
            for (String blocked : BlockedPaths.WINDOWS_BLOCKED) {
                String normalizedBlocked = blocked.replace("\\", "/").toLowerCase();
                if (normalizedPath.startsWith(normalizedBlocked)) {
                    return true;
                }
            }
        } else {
            for (String blocked : BlockedPaths.UNIX_BLOCKED) {
                if (normalizedPath.startsWith(blocked.toLowerCase())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if a path is in a sensitive home directory.
     */
    public boolean isSensitiveHomePath(String path) {
        String normalizedPath = path.replace("\\", "/").toLowerCase();
        String normalizedHome = USER_HOME.replace("\\", "/").toLowerCase();

        if (!normalizedPath.startsWith(normalizedHome)) {
            return false;
        }

        String relativePath = normalizedPath.substring(normalizedHome.length());

        for (String blocked : BlockedPaths.HOME_BLOCKED) {
            if (relativePath.startsWith("/" + blocked.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a filename matches hidden/sensitive patterns.
     */
    public boolean isSensitiveFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        for (Pattern pattern : BlockedPaths.HIDDEN_PATTERNS) {
            if (pattern.matcher(filename).matches()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a path contains path traversal attempts.
     */
    public boolean containsTraversal(String path) {
        if (path == null) {
            return false;
        }

        // Check for .. sequences
        return path.contains("..") || path.contains("%2e%2e");
    }

    /**
     * Sanitize a path for safe use.
     */
    public String sanitize(String path) {
        if (path == null) {
            return null;
        }

        try {
            return Paths.get(path).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return null;
        }
    }
}
