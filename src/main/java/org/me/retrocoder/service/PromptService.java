package org.me.retrocoder.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Service for loading and managing prompt templates.
 */
@Slf4j
@Service
public class PromptService {

    private static final String TEMPLATES_PATH = "templates/";
    private static final String PROMPTS_DIR = "prompts";

    /**
     * Load a prompt by name with fallback chain:
     * 1. Project-specific: {project_dir}/prompts/{name}.md
     * 2. Base template: classpath:templates/{name}.template.md
     */
    public String loadPrompt(String name, String projectPath) {
        // Try project-specific first
        Path projectPrompt = Paths.get(projectPath, PROMPTS_DIR, name + ".md");
        if (Files.exists(projectPrompt)) {
            try {
                return Files.readString(projectPrompt, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("Failed to read project prompt: {}", projectPrompt, e);
            }
        }

        // Fall back to template
        return loadTemplate(name + ".template.md");
    }

    /**
     * Load initializer prompt.
     */
    public String getInitializerPrompt(String projectPath) {
        return loadPrompt("initializer_prompt", projectPath);
    }

    /**
     * Load coding prompt (standard mode).
     */
    public String getCodingPrompt(String projectPath) {
        return loadPrompt("coding_prompt", projectPath);
    }

    /**
     * Load coding prompt (YOLO mode).
     */
    public String getCodingPromptYolo(String projectPath) {
        return loadPrompt("coding_prompt_yolo", projectPath);
    }

    /**
     * Load app spec.
     */
    public String getAppSpec(String projectPath) {
        // Try prompts directory first
        Path promptsSpec = Paths.get(projectPath, PROMPTS_DIR, "app_spec.txt");
        if (Files.exists(promptsSpec)) {
            try {
                return Files.readString(promptsSpec, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("Failed to read app spec from prompts: {}", promptsSpec, e);
            }
        }

        // Try legacy root location
        Path legacySpec = Paths.get(projectPath, "app_spec.txt");
        if (Files.exists(legacySpec)) {
            try {
                return Files.readString(legacySpec, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("Failed to read legacy app spec: {}", legacySpec, e);
            }
        }

        throw new RuntimeException("App spec not found in project: " + projectPath);
    }

    /**
     * Check if project has app spec.
     */
    public boolean hasAppSpec(String projectPath) {
        Path promptsSpec = Paths.get(projectPath, PROMPTS_DIR, "app_spec.txt");
        Path legacySpec = Paths.get(projectPath, "app_spec.txt");
        return Files.exists(promptsSpec) || Files.exists(legacySpec);
    }

    /**
     * Scaffold prompt templates for a new project.
     */
    public void scaffoldProjectPrompts(String projectPath) {
        Path promptsDir = Paths.get(projectPath, PROMPTS_DIR);

        try {
            if (!Files.exists(promptsDir)) {
                Files.createDirectories(promptsDir);
            }

            // Copy templates
            copyTemplateIfNotExists("app_spec.template.txt", promptsDir.resolve("app_spec.txt"));
            copyTemplateIfNotExists("initializer_prompt.template.md", promptsDir.resolve("initializer_prompt.md"));
            copyTemplateIfNotExists("coding_prompt.template.md", promptsDir.resolve("coding_prompt.md"));
            copyTemplateIfNotExists("coding_prompt_yolo.template.md", promptsDir.resolve("coding_prompt_yolo.md"));

            log.info("Scaffolded prompts for project: {}", projectPath);
        } catch (IOException e) {
            log.error("Failed to scaffold prompts for project: {}", projectPath, e);
            throw new RuntimeException("Failed to scaffold project prompts", e);
        }
    }

    /**
     * Check if project has prompts directory.
     */
    public boolean hasProjectPrompts(String projectPath) {
        Path promptsDir = Paths.get(projectPath, PROMPTS_DIR);
        return Files.exists(promptsDir) && Files.isDirectory(promptsDir);
    }

    /**
     * Get prompt file contents for editing.
     */
    public String getPromptContent(String projectPath, String promptName) {
        Path promptFile = Paths.get(projectPath, PROMPTS_DIR, promptName);
        if (!Files.exists(promptFile)) {
            throw new RuntimeException("Prompt file not found: " + promptName);
        }

        try {
            return Files.readString(promptFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read prompt file: " + promptName, e);
        }
    }

    /**
     * Save prompt file contents.
     */
    public void savePromptContent(String projectPath, String promptName, String content) {
        Path promptFile = Paths.get(projectPath, PROMPTS_DIR, promptName);

        try {
            if (!Files.exists(promptFile.getParent())) {
                Files.createDirectories(promptFile.getParent());
            }
            Files.writeString(promptFile, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save prompt file: " + promptName, e);
        }
    }

    private String loadTemplate(String templateName) {
        try {
            ClassPathResource resource = new ClassPathResource(TEMPLATES_PATH + templateName);
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("Failed to load template: {}", templateName, e);
            throw new RuntimeException("Template not found: " + templateName, e);
        }
    }

    private void copyTemplateIfNotExists(String templateName, Path destination) throws IOException {
        if (Files.exists(destination)) {
            return;
        }

        try {
            ClassPathResource resource = new ClassPathResource(TEMPLATES_PATH + templateName);
            try (InputStream is = resource.getInputStream()) {
                Files.copy(is, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warn("Template not found, creating empty file: {}", templateName);
            Files.writeString(destination, "# " + destination.getFileName() + "\n\n", StandardCharsets.UTF_8);
        }
    }
}
