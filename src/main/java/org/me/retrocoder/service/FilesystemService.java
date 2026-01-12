package org.me.retrocoder.service;

import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.model.dto.DirectoryEntryDTO;
import org.me.retrocoder.model.dto.DirectoryListResponseDTO;
import org.me.retrocoder.model.dto.DriveInfoDTO;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for filesystem operations with security controls.
 */
@Slf4j
@Service
public class FilesystemService {

    private static final Set<String> BLOCKED_WINDOWS_PATHS = Set.of(
        "C:\\Windows", "C:\\Program Files", "C:\\Program Files (x86)",
        "C:\\ProgramData", "C:\\$Recycle.Bin", "C:\\System Volume Information"
    );

    private static final Set<String> BLOCKED_UNIX_PATHS = Set.of(
        "/System", "/Library", "/usr", "/bin", "/sbin", "/etc", "/var",
        "/private", "/cores", "/dev", "/proc", "/sys"
    );

    private static final Set<String> BLOCKED_HOME_DIRS = Set.of(
        ".ssh", ".aws", ".gnupg", ".docker", ".kube", ".config",
        ".local", ".cache", ".npm", ".gradle", ".m2"
    );

    private static final List<Pattern> HIDDEN_PATTERNS = Arrays.asList(
        Pattern.compile("\\.env.*"),
        Pattern.compile(".*\\.key"),
        Pattern.compile(".*\\.pem"),
        Pattern.compile(".*credentials.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*secrets.*", Pattern.CASE_INSENSITIVE)
    );

    /**
     * List directory contents.
     */
    public DirectoryListResponseDTO listDirectory(String pathStr, boolean showHidden) {
        Path path = Paths.get(pathStr).toAbsolutePath().normalize();

        if (!isPathAllowed(path)) {
            throw new SecurityException("Access to this path is not allowed: " + pathStr);
        }

        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path does not exist or is not a directory: " + pathStr);
        }

        try {
            List<DirectoryEntryDTO> entries = Files.list(path)
                .filter(p -> showHidden || !isHidden(p))
                .filter(p -> !isBlockedPath(p))
                .map(this::toDirectoryEntry)
                .sorted(Comparator.comparing(DirectoryEntryDTO::isDirectory).reversed()
                    .thenComparing(DirectoryEntryDTO::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

            String parentPath = path.getParent() != null ? path.getParent().toString() : null;

            return DirectoryListResponseDTO.builder()
                .currentPath(path.toString())
                .parentPath(parentPath)
                .entries(entries)
                .drives(isWindows() ? listDrives() : null)
                .build();
        } catch (IOException e) {
            log.error("Failed to list directory: {}", pathStr, e);
            throw new RuntimeException("Failed to list directory: " + pathStr, e);
        }
    }

    /**
     * Validate a path.
     */
    public boolean validatePath(String pathStr) {
        try {
            Path path = Paths.get(pathStr).toAbsolutePath().normalize();
            return Files.exists(path) && Files.isDirectory(path) && isPathAllowed(path);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Create a directory.
     */
    public boolean createDirectory(String pathStr) {
        Path path = Paths.get(pathStr).toAbsolutePath().normalize();

        if (!isPathAllowed(path.getParent())) {
            throw new SecurityException("Cannot create directory in this location: " + pathStr);
        }

        try {
            Files.createDirectories(path);
            return true;
        } catch (IOException e) {
            log.error("Failed to create directory: {}", pathStr, e);
            return false;
        }
    }

    /**
     * Get user home directory.
     */
    public String getUserHome() {
        return System.getProperty("user.home");
    }

    /**
     * List available drives (Windows only).
     */
    public List<DriveInfoDTO> listDrives() {
        if (!isWindows()) {
            return List.of();
        }

        return Arrays.stream(File.listRoots())
            .filter(File::exists)
            .map(root -> {
                String letter = root.getPath().substring(0, 1);
                return DriveInfoDTO.builder()
                    .letter(letter)
                    .path(root.getPath())
                    .label(getVolumeLabel(root))
                    .totalSpace(root.getTotalSpace())
                    .freeSpace(root.getFreeSpace())
                    .build();
            })
            .collect(Collectors.toList());
    }

    private boolean isPathAllowed(Path path) {
        if (path == null) {
            return false;
        }

        String pathStr = path.toString();
        String normalizedPath = pathStr.replace("\\", "/").toLowerCase();

        // Check blocked Windows paths
        if (isWindows()) {
            for (String blocked : BLOCKED_WINDOWS_PATHS) {
                if (normalizedPath.startsWith(blocked.toLowerCase().replace("\\", "/"))) {
                    return false;
                }
            }
        }

        // Check blocked Unix paths
        for (String blocked : BLOCKED_UNIX_PATHS) {
            if (normalizedPath.startsWith(blocked.toLowerCase())) {
                return false;
            }
        }

        // Check blocked home directories
        String home = System.getProperty("user.home").replace("\\", "/");
        for (String blocked : BLOCKED_HOME_DIRS) {
            if (normalizedPath.startsWith((home + "/" + blocked).toLowerCase())) {
                return false;
            }
        }

        return true;
    }

    private boolean isBlockedPath(Path path) {
        return !isPathAllowed(path);
    }

    private boolean isHidden(Path path) {
        String name = path.getFileName().toString();

        // Check if starts with dot
        if (name.startsWith(".")) {
            return true;
        }

        // Check hidden patterns
        for (Pattern pattern : HIDDEN_PATTERNS) {
            if (pattern.matcher(name).matches()) {
                return true;
            }
        }

        // Check OS hidden attribute
        try {
            return Files.isHidden(path);
        } catch (IOException e) {
            return false;
        }
    }

    private DirectoryEntryDTO toDirectoryEntry(Path path) {
        try {
            boolean isDir = Files.isDirectory(path);
            boolean hasChildren = false;
            if (isDir) {
                try (var stream = Files.list(path)) {
                    hasChildren = stream.anyMatch(p -> Files.isDirectory(p) && !isHidden(p));
                } catch (IOException | SecurityException e) {
                    // Can't read directory, assume no children
                }
            }
            return DirectoryEntryDTO.builder()
                .name(path.getFileName().toString())
                .path(path.toString())
                .isDirectory(isDir)
                .isHidden(isHidden(path))
                .hasChildren(hasChildren)
                .size(isDir ? null : Files.size(path))
                .lastModified(Files.getLastModifiedTime(path).toMillis())
                .build();
        } catch (IOException e) {
            return DirectoryEntryDTO.builder()
                .name(path.getFileName().toString())
                .path(path.toString())
                .isDirectory(Files.isDirectory(path))
                .isHidden(false)
                .hasChildren(false)
                .build();
        }
    }

    private String getVolumeLabel(File root) {
        try {
            return java.nio.file.FileSystems.getDefault()
                .getFileStores()
                .iterator()
                .next()
                .name();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
