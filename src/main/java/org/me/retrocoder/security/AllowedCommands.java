package org.me.retrocoder.security;

import java.util.Set;

/**
 * Allowed bash commands whitelist.
 * Matches the Python security.py ALLOWED_COMMANDS.
 */
public final class AllowedCommands {

    private AllowedCommands() {}

    /**
     * Set of allowed command names.
     */
    public static final Set<String> ALLOWED = Set.of(
        // File inspection
        "ls", "cat", "head", "tail", "wc", "grep", "find", "file", "stat",

        // File operations
        "cp", "mkdir", "chmod", "mv", "rm", "touch", "ln",

        // Development tools
        "npm", "npx", "pnpm", "node", "git", "docker", "docker-compose",
        "python", "python3", "pip", "pip3",
        "java", "javac", "mvn", "gradle",

        // Process management
        "ps", "lsof", "sleep", "kill", "pkill",

        // Network (curl allowed for API calls, wget blocked for security)
        "curl",

        // Shell
        "sh", "bash", "echo", "env", "export", "which", "pwd", "cd",

        // Init script
        "init.sh"
    );

    /**
     * Commands that need extra validation.
     */
    public static final Set<String> REQUIRES_VALIDATION = Set.of(
        "pkill", "chmod", "init.sh", "rm"
    );

    /**
     * Allowed pkill targets.
     */
    public static final Set<String> ALLOWED_PKILL_TARGETS = Set.of(
        "node", "npm", "npx", "vite", "next", "webpack", "esbuild"
    );

    /**
     * Allowed chmod modes.
     */
    public static final Set<String> ALLOWED_CHMOD_MODES = Set.of(
        "+x", "755", "644"
    );
}
