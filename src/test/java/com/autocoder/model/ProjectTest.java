package com.autocoder.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.me.retrocoder.model.ClaudeIntegrationMode;
import org.me.retrocoder.model.Project;

class ProjectTest {

    @Test
    void validNameWithAlphanumeric() {
        assertTrue(Project.isValidName("myproject"));
        assertTrue(Project.isValidName("MyProject123"));
        assertTrue(Project.isValidName("my-project"));
        assertTrue(Project.isValidName("my_project"));
        assertTrue(Project.isValidName("a"));
    }

    @Test
    void invalidNameWithSpecialChars() {
        assertFalse(Project.isValidName("my project"));
        assertFalse(Project.isValidName("my.project"));
        assertFalse(Project.isValidName("my@project"));
        assertFalse(Project.isValidName("my/project"));
    }

    @Test
    void invalidNameEmpty() {
        assertFalse(Project.isValidName(""));
        assertFalse(Project.isValidName(null));
    }

    @Test
    void invalidNameTooLong() {
        String longName = "a".repeat(51);
        assertFalse(Project.isValidName(longName));
    }

    @Test
    void validNameMaxLength() {
        String maxName = "a".repeat(50);
        assertTrue(Project.isValidName(maxName));
    }

    @Test
    void getPosixPath() {
        Project project = Project.builder()
            .name("test")
            .path("C:\\Users\\test\\project")
            .build();

        assertEquals("C:/Users/test/project", project.getPosixPath());
    }

    @Test
    void getPosixPathAlreadyPosix() {
        Project project = Project.builder()
            .name("test")
            .path("/home/user/project")
            .build();

        assertEquals("/home/user/project", project.getPosixPath());
    }

    @Test
    void defaultIntegrationMode() {
        Project project = Project.builder()
            .name("test")
            .path("/test")
            .build();

        assertEquals(ClaudeIntegrationMode.CLI_WRAPPER, project.getIntegrationMode());
    }
}
