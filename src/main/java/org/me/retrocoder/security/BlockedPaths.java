package org.me.retrocoder.security;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.List;

/**
 * Blocked paths and patterns for security.
 */
public final class BlockedPaths {

    private BlockedPaths() {}

    /**
     * Blocked Windows paths.
     */
    public static final Set<String> WINDOWS_BLOCKED = Set.of(
        "C:\\Windows",
        "C:\\Program Files",
        "C:\\Program Files (x86)",
        "C:\\ProgramData",
        "C:\\$Recycle.Bin",
        "C:\\System Volume Information"
    );

    /**
     * Blocked Unix paths.
     */
    public static final Set<String> UNIX_BLOCKED = Set.of(
        "/System",
        "/Library",
        "/usr",
        "/bin",
        "/sbin",
        "/etc",
        "/var",
        "/private",
        "/cores",
        "/dev",
        "/proc",
        "/sys"
    );

    /**
     * Blocked home directory folders.
     */
    public static final Set<String> HOME_BLOCKED = Set.of(
        ".ssh",
        ".aws",
        ".gnupg",
        ".gpg",
        ".docker",
        ".kube",
        ".config",
        ".local",
        ".cache",
        ".npm",
        ".gradle",
        ".m2",
        ".azure",
        ".gcloud",
        ".terraform"
    );

    /**
     * Patterns for hidden/sensitive files.
     */
    public static final List<Pattern> HIDDEN_PATTERNS = List.of(
        Pattern.compile("\\.env.*"),
        Pattern.compile(".*\\.key"),
        Pattern.compile(".*\\.pem"),
        Pattern.compile(".*\\.p12"),
        Pattern.compile(".*\\.pfx"),
        Pattern.compile(".*credentials.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*secrets.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*password.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\.secret.*"),
        Pattern.compile(".*\\.token")
    );
}
