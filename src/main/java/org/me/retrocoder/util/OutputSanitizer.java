package org.me.retrocoder.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitizes output by redacting sensitive information.
 */
public class OutputSanitizer {

    private static final String REDACTED = "[REDACTED]";

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
        // API Keys
        Pattern.compile("sk-ant-[a-zA-Z0-9_-]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ANTHROPIC_API_KEY\\s*=\\s*[^\\s]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("anthropic[-_]?api[-_]?key[\"']?\\s*[:=]\\s*[\"']?[^\\s\"']+", Pattern.CASE_INSENSITIVE),

        // GitHub tokens
        Pattern.compile("gh[pousr]_[a-zA-Z0-9]{36,}", Pattern.CASE_INSENSITIVE),
        Pattern.compile("github_pat_[a-zA-Z0-9]{22}_[a-zA-Z0-9]{59}", Pattern.CASE_INSENSITIVE),
        Pattern.compile("GITHUB_TOKEN\\s*=\\s*[^\\s]+", Pattern.CASE_INSENSITIVE),

        // AWS
        Pattern.compile("AKIA[0-9A-Z]{16}", Pattern.CASE_INSENSITIVE),
        Pattern.compile("aws_secret_access_key\\s*=\\s*[^\\s]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("aws_access_key_id\\s*=\\s*[^\\s]+", Pattern.CASE_INSENSITIVE),

        // Generic secrets
        Pattern.compile("password\\s*[:=]\\s*[\"']?[^\\s\"']+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("secret\\s*[:=]\\s*[\"']?[^\\s\"']+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("api[-_]?key\\s*[:=]\\s*[\"']?[^\\s\"']+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("token\\s*[:=]\\s*[\"']?[^\\s\"']+", Pattern.CASE_INSENSITIVE),

        // Private keys
        Pattern.compile("-----BEGIN.*PRIVATE KEY-----"),
        Pattern.compile("-----BEGIN RSA PRIVATE KEY-----"),
        Pattern.compile("-----BEGIN EC PRIVATE KEY-----")
    );

    /**
     * Sanitize output by redacting sensitive information.
     */
    public static String sanitize(String output) {
        if (output == null || output.isEmpty()) {
            return output;
        }

        String result = output;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            Matcher matcher = pattern.matcher(result);
            result = matcher.replaceAll(REDACTED);
        }

        return result;
    }

    /**
     * Check if output contains sensitive information.
     */
    public static boolean containsSensitive(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }

        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(output).find()) {
                return true;
            }
        }

        return false;
    }
}
