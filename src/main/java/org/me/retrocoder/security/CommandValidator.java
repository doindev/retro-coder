package org.me.retrocoder.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates bash commands against the allowlist.
 */
@Slf4j
@Component
public class CommandValidator {

    private static final Pattern COMMAND_PATTERN = Pattern.compile("^([a-zA-Z0-9_./-]+)");

    /**
     * Validate a bash command.
     *
     * @param command The command to validate
     * @return ValidationResult with success/failure and message
     */
    public ValidationResult validate(String command) {
        if (command == null || command.isEmpty()) {
            return ValidationResult.fail("Empty command");
        }

        // Extract commands from the line (handles pipes, &&, ||, ;)
        List<String> commands = extractCommands(command);

        for (String cmd : commands) {
            ValidationResult result = validateSingleCommand(cmd);
            if (!result.isValid()) {
                return result;
            }
        }

        return ValidationResult.ok();
    }

    private ValidationResult validateSingleCommand(String command) {
        String trimmed = command.trim();
        if (trimmed.isEmpty()) {
            return ValidationResult.ok();
        }

        // Extract command name
        Matcher matcher = COMMAND_PATTERN.matcher(trimmed);
        if (!matcher.find()) {
            return ValidationResult.fail("Could not parse command: " + trimmed);
        }

        String cmdName = matcher.group(1);

        // Handle path prefixes (./script.sh, /path/to/cmd)
        if (cmdName.contains("/")) {
            cmdName = cmdName.substring(cmdName.lastIndexOf("/") + 1);
        }

        // Check against allowlist
        if (!AllowedCommands.ALLOWED.contains(cmdName)) {
            return ValidationResult.fail("Command not allowed: " + cmdName);
        }

        // Extra validation for special commands
        if (AllowedCommands.REQUIRES_VALIDATION.contains(cmdName)) {
            return validateSpecialCommand(cmdName, trimmed);
        }

        return ValidationResult.ok();
    }

    private ValidationResult validateSpecialCommand(String cmdName, String fullCommand) {
        switch (cmdName) {
            case "pkill" -> {
                return validatePkill(fullCommand);
            }
            case "chmod" -> {
                return validateChmod(fullCommand);
            }
            case "init.sh" -> {
                return validateInitSh(fullCommand);
            }
            case "rm" -> {
                return validateRm(fullCommand);
            }
            default -> {
                return ValidationResult.ok();
            }
        }
    }

    private ValidationResult validatePkill(String command) {
        // Only allow pkill for specific dev processes
        for (String target : AllowedCommands.ALLOWED_PKILL_TARGETS) {
            if (command.contains(target)) {
                return ValidationResult.ok();
            }
        }
        return ValidationResult.fail("pkill only allowed for dev processes");
    }

    private ValidationResult validateChmod(String command) {
        // Only allow chmod +x or safe modes
        for (String mode : AllowedCommands.ALLOWED_CHMOD_MODES) {
            if (command.contains(mode)) {
                return ValidationResult.ok();
            }
        }
        return ValidationResult.fail("chmod only allowed with +x or safe modes");
    }

    private ValidationResult validateInitSh(String command) {
        // Only allow ./init.sh or path/init.sh
        if (command.matches("^(\\./)?(.*/)?" + "init\\.sh.*$")) {
            return ValidationResult.ok();
        }
        return ValidationResult.fail("init.sh must be called as ./init.sh");
    }

    private ValidationResult validateRm(String command) {
        // Prevent rm -rf /
        if (command.matches(".*-r.*\\s+/$") || command.matches(".*-rf\\s+/$")) {
            return ValidationResult.fail("rm -rf / is not allowed");
        }
        return ValidationResult.ok();
    }

    private List<String> extractCommands(String commandLine) {
        List<String> commands = new ArrayList<>();

        // Split on pipe, &&, ||, ; (handling quoted strings)
        String[] parts = commandLine.split("(?<!\\\\)[|&;]");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                commands.add(trimmed);
            }
        }

        return commands;
    }

    /**
     * Result of command validation.
     */
    public record ValidationResult(boolean valid, String message) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult fail(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }
    }
}
