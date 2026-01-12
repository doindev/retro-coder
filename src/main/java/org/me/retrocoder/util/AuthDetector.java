package org.me.retrocoder.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects authentication errors in Claude CLI output.
 */
public class AuthDetector {

    private static final List<Pattern> AUTH_ERROR_PATTERNS = List.of(
        Pattern.compile("not logged in", Pattern.CASE_INSENSITIVE),
        Pattern.compile("not authenticated", Pattern.CASE_INSENSITIVE),
        Pattern.compile("login required", Pattern.CASE_INSENSITIVE),
        Pattern.compile("please log in", Pattern.CASE_INSENSITIVE),
        Pattern.compile("authentication required", Pattern.CASE_INSENSITIVE),
        Pattern.compile("unauthorized", Pattern.CASE_INSENSITIVE),
        Pattern.compile("invalid.*token", Pattern.CASE_INSENSITIVE),
        Pattern.compile("invalid.*credential", Pattern.CASE_INSENSITIVE),
        Pattern.compile("invalid.*api.?key", Pattern.CASE_INSENSITIVE),
        Pattern.compile("expired.*token", Pattern.CASE_INSENSITIVE),
        Pattern.compile("expired.*session", Pattern.CASE_INSENSITIVE),
        Pattern.compile("session.*expired", Pattern.CASE_INSENSITIVE),
        Pattern.compile("token.*expired", Pattern.CASE_INSENSITIVE),
        Pattern.compile("api.?key.*invalid", Pattern.CASE_INSENSITIVE),
        Pattern.compile("api.?key.*required", Pattern.CASE_INSENSITIVE),
        Pattern.compile("need.*to.*log.*in", Pattern.CASE_INSENSITIVE),
        Pattern.compile("must.*log.*in", Pattern.CASE_INSENSITIVE),
        Pattern.compile("auth.*fail", Pattern.CASE_INSENSITIVE),
        Pattern.compile("permission.*denied", Pattern.CASE_INSENSITIVE),
        Pattern.compile("access.*denied", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Check if text contains an authentication error.
     */
    public static boolean isAuthError(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        for (Pattern pattern : AUTH_ERROR_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get help message for CLI auth errors.
     */
    public static String getCliHelpMessage() {
        return """
            ================================================================================
            AUTHENTICATION ERROR DETECTED
            ================================================================================

            The Claude CLI is not authenticated. Please run:

                claude login

            Then try again.

            If you continue to have issues, check:
            1. Your internet connection
            2. Your ~/.claude directory exists
            3. You have a valid Anthropic account

            ================================================================================
            """;
    }

    /**
     * Get help message for server/API auth errors.
     */
    public static String getServerHelpMessage() {
        return """
            Authentication Error

            Please ensure you are logged in to Claude CLI.
            Run 'claude login' in a terminal and try again.
            """;
    }
}
