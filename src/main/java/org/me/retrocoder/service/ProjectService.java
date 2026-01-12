package org.me.retrocoder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.model.ClaudeIntegrationMode;
import org.me.retrocoder.model.Project;
import org.me.retrocoder.model.dto.ProjectCreateDTO;
import org.me.retrocoder.model.dto.ProjectDTO;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for project management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final RegistryService registryService;
    private final PromptService promptService;
    private final FeatureService featureService;

    /**
     * Create a new project.
     */
    public ProjectDTO createProject(ProjectCreateDTO dto) {
        // Validate name
        if (!Project.isValidName(dto.getName())) {
            throw new IllegalArgumentException("Invalid project name. Must be alphanumeric with dashes and underscores, 1-50 characters.");
        }

        // Check if already exists
        if (registryService.projectExists(dto.getName())) {
            throw new IllegalArgumentException("Project already exists: " + dto.getName());
        }

        // Create directory if it doesn't exist
        Path projectPath = Paths.get(dto.getPath());
        try {
            if (!Files.exists(projectPath)) {
                Files.createDirectories(projectPath);
                log.info("Created project directory: {}", projectPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create project directory: " + dto.getPath(), e);
        }

        // Scaffold prompts
        promptService.scaffoldProjectPrompts(dto.getPath());

        // Register project
        ClaudeIntegrationMode mode = dto.getIntegrationMode() != null
            ? dto.getIntegrationMode()
            : registryService.getDefaultIntegrationMode();

        Project project = registryService.registerProject(dto.getName(), dto.getPath(), mode);

        ProjectDTO result = ProjectDTO.fromEntity(project);
        result.setHasSpec(promptService.hasAppSpec(dto.getPath()));
        result.setHasFeatures(false);
        result.setStats(org.me.retrocoder.model.dto.FeatureStatsDTO.of(0, 0, 0));

        return result;
    }

    /**
     * Get project by name with additional metadata.
     */
    public Optional<ProjectDTO> getProject(String name) {
        return registryService.getProject(name).map(project -> {
            ProjectDTO dto = ProjectDTO.fromEntity(project);
            dto.setHasSpec(promptService.hasAppSpec(project.getPath()));
            dto.setHasFeatures(featureService.hasFeatures(name));
            try {
                dto.setStats(featureService.getStats(name));
            } catch (Exception e) {
                dto.setStats(org.me.retrocoder.model.dto.FeatureStatsDTO.of(0, 0, 0));
            }
            return dto;
        });
    }

    /**
     * List all projects with metadata.
     */
    public List<ProjectDTO> listProjects() {
        return registryService.listProjects().stream()
            .map(project -> {
                ProjectDTO dto = ProjectDTO.fromEntity(project);
                try {
                    dto.setHasSpec(promptService.hasAppSpec(project.getPath()));
                    dto.setHasFeatures(featureService.hasFeatures(project.getName()));
                    dto.setStats(featureService.getStats(project.getName()));
                } catch (Exception e) {
                    log.warn("Error checking project status for {}: {}", project.getName(), e.getMessage());
                    dto.setHasSpec(false);
                    dto.setHasFeatures(false);
                    dto.setStats(org.me.retrocoder.model.dto.FeatureStatsDTO.of(0, 0, 0));
                }
                return dto;
            })
            .collect(Collectors.toList());
    }

    /**
     * Delete a project.
     * @param name Project name
     * @param deleteFiles If true, also delete project files
     */
    public boolean deleteProject(String name, boolean deleteFiles) {
        Optional<Project> project = registryService.getProject(name);

        if (project.isEmpty()) {
            return false;
        }

        if (deleteFiles) {
            try {
                Path projectPath = Paths.get(project.get().getPath());
                if (Files.exists(projectPath)) {
                    deleteDirectory(projectPath);
                    log.info("Deleted project files: {}", projectPath);
                }
            } catch (IOException e) {
                log.error("Failed to delete project files for: {}", name, e);
                throw new RuntimeException("Failed to delete project files", e);
            }
        }

        return registryService.deleteProject(name);
    }

    /**
     * Validate project path.
     */
    public boolean validatePath(String path) {
        return registryService.validateProjectPath(path);
    }

    /**
     * Update project settings (yolo_mode, model).
     */
    public ProjectDTO updateProjectSettings(String name, Boolean yoloMode, String model) {
        var project = registryService.updateProjectSettings(name, yoloMode, model);
        ProjectDTO dto = ProjectDTO.fromEntity(project);
        try {
            dto.setHasSpec(promptService.hasAppSpec(project.getPath()));
            dto.setHasFeatures(featureService.hasFeatures(name));
            dto.setStats(featureService.getStats(name));
        } catch (Exception e) {
            log.warn("Error checking project status for {}: {}", name, e.getMessage());
            dto.setHasSpec(false);
            dto.setHasFeatures(false);
            dto.setStats(org.me.retrocoder.model.dto.FeatureStatsDTO.of(0, 0, 0));
        }
        return dto;
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                entries.forEach(entry -> {
                    try {
                        deleteDirectory(entry);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
        Files.delete(path);
    }
}
