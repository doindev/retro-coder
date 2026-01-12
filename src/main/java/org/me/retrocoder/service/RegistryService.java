package org.me.retrocoder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.model.ClaudeIntegrationMode;
import org.me.retrocoder.model.Project;
import org.me.retrocoder.model.Settings;
import org.me.retrocoder.repository.ProjectRepository;
import org.me.retrocoder.repository.SettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing the project registry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistryService {

    private final ProjectRepository projectRepository;
    private final SettingsRepository settingsRepository;

    /**
     * Register a new project.
     */
    @Transactional
    public Project registerProject(String name, String path, ClaudeIntegrationMode mode) {
        if (!Project.isValidName(name)) {
            throw new IllegalArgumentException("Invalid project name: " + name);
        }

        Path projectPath = Paths.get(path);
        if (!Files.exists(projectPath)) {
            throw new IllegalArgumentException("Project path does not exist: " + path);
        }

        if (projectRepository.existsByName(name)) {
            throw new IllegalArgumentException("Project already exists: " + name);
        }

        Project project = Project.builder()
            .name(name)
            .path(projectPath.toAbsolutePath().toString().replace("\\", "/"))
            .integrationMode(mode != null ? mode : ClaudeIntegrationMode.CLI_WRAPPER)
            .build();

        return projectRepository.save(project);
    }

    /**
     * Get project by name.
     */
    public Optional<Project> getProject(String name) {
        return projectRepository.findByName(name);
    }

    /**
     * Get project path by name.
     */
    public Optional<String> getProjectPath(String name) {
        return projectRepository.findByName(name).map(Project::getPath);
    }

    /**
     * List all registered projects.
     */
    public List<Project> listProjects() {
        return projectRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Delete a project from registry.
     */
    @Transactional
    public boolean deleteProject(String name) {
        if (projectRepository.existsByName(name)) {
            projectRepository.deleteById(name);
            return true;
        }
        return false;
    }

    /**
     * Check if project exists.
     */
    public boolean projectExists(String name) {
        return projectRepository.existsByName(name);
    }

    /**
     * Validate project path is accessible.
     */
    public boolean validateProjectPath(String path) {
        try {
            Path p = Paths.get(path);
            return Files.exists(p) && Files.isDirectory(p) && Files.isReadable(p) && Files.isWritable(p);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get a setting value.
     */
    public Optional<String> getSetting(String key) {
        return settingsRepository.findByKey(key).map(Settings::getValue);
    }

    /**
     * Get a setting value with default.
     */
    public String getSetting(String key, String defaultValue) {
        return getSetting(key).orElse(defaultValue);
    }

    /**
     * Get a boolean setting value.
     */
    public boolean getBooleanSetting(String key, boolean defaultValue) {
        return settingsRepository.findByKey(key)
            .map(Settings::getBooleanValue)
            .orElse(defaultValue);
    }

    /**
     * Set a setting value.
     */
    @Transactional
    public void setSetting(String key, String value) {
        Settings setting = settingsRepository.findByKey(key)
            .orElse(Settings.builder().key(key).build());
        setting.setValue(value);
        settingsRepository.save(setting);
    }

    /**
     * Set a boolean setting value.
     */
    @Transactional
    public void setBooleanSetting(String key, boolean value) {
        setSetting(key, String.valueOf(value));
    }

    /**
     * Get default Claude integration mode.
     */
    public ClaudeIntegrationMode getDefaultIntegrationMode() {
        return getSetting(Settings.KEY_DEFAULT_INTEGRATION_MODE)
            .map(ClaudeIntegrationMode::fromString)
            .orElse(ClaudeIntegrationMode.CLI_WRAPPER);
    }

    /**
     * Get default model.
     */
    public String getDefaultModel() {
        return getSetting(Settings.KEY_DEFAULT_MODEL, "claude-opus-4-5-20251101");
    }

    /**
     * Get YOLO mode setting.
     */
    public boolean isYoloMode() {
        return getBooleanSetting(Settings.KEY_YOLO_MODE, false);
    }

    /**
     * Update project settings (yolo_mode, model).
     */
    @Transactional
    public Project updateProjectSettings(String name, Boolean yoloMode, String model) {
        Project project = projectRepository.findByName(name)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + name));

        if (yoloMode != null) {
            project.setYoloMode(yoloMode);
        }
        if (model != null) {
            project.setModel(model);
        }

        return projectRepository.save(project);
    }
}
