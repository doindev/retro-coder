package org.me.retrocoder.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.model.dto.FeatureStatsDTO;
import org.me.retrocoder.model.dto.ProjectCreateDTO;
import org.me.retrocoder.model.dto.ProjectDTO;
import org.me.retrocoder.service.FeatureService;
import org.me.retrocoder.service.ProjectService;
import org.me.retrocoder.service.PromptService;
import org.me.retrocoder.service.RegistryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for project management.
 */
@Slf4j
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final FeatureService featureService;
    private final PromptService promptService;
    private final RegistryService registryService;

    /**
     * List all projects.
     */
    @GetMapping
    public ResponseEntity<List<ProjectDTO>> listProjects() {
        return ResponseEntity.ok(projectService.listProjects());
    }

    /**
     * Create a new project.
     */
    @PostMapping
    public ResponseEntity<ProjectDTO> createProject(@Valid @RequestBody ProjectCreateDTO dto) {
        try {
            ProjectDTO project = projectService.createProject(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(project);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create project: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get project by name.
     */
    @GetMapping("/{name}")
    public ResponseEntity<ProjectDTO> getProject(@PathVariable String name) {
        return projectService.getProject(name)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a project.
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable String name,
            @RequestParam(defaultValue = "false") boolean deleteFiles) {
        if (projectService.deleteProject(name, deleteFiles)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Get project prompts.
     */
    @GetMapping("/{name}/prompts")
    public ResponseEntity<Map<String, String>> getPrompts(@PathVariable String name) {
        String projectPath = registryService.getProjectPath(name).orElse(null);
        if (projectPath == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Map<String, String> prompts = Map.of(
                "app_spec", promptService.getPromptContent(projectPath, "app_spec.txt"),
                "initializer_prompt", promptService.getPromptContent(projectPath, "initializer_prompt.md"),
                "coding_prompt", promptService.getPromptContent(projectPath, "coding_prompt.md"),
                "coding_prompt_yolo", promptService.getPromptContent(projectPath, "coding_prompt_yolo.md")
            );
            return ResponseEntity.ok(prompts);
        } catch (Exception e) {
            log.warn("Failed to get prompts for project: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update project prompts.
     */
    @PutMapping("/{name}/prompts")
    public ResponseEntity<Void> updatePrompts(
            @PathVariable String name,
            @RequestBody Map<String, String> prompts) {
        String projectPath = registryService.getProjectPath(name).orElse(null);
        if (projectPath == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            prompts.forEach((key, value) -> {
                String fileName = switch (key) {
                    case "app_spec" -> "app_spec.txt";
                    case "initializer_prompt" -> "initializer_prompt.md";
                    case "coding_prompt" -> "coding_prompt.md";
                    case "coding_prompt_yolo" -> "coding_prompt_yolo.md";
                    default -> null;
                };
                if (fileName != null) {
                    promptService.savePromptContent(projectPath, fileName, value);
                }
            });
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to update prompts for project: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get project statistics.
     */
    @GetMapping("/{name}/stats")
    public ResponseEntity<FeatureStatsDTO> getStats(@PathVariable String name) {
        if (!registryService.projectExists(name)) {
            return ResponseEntity.notFound().build();
        }

        try {
            return ResponseEntity.ok(featureService.getStats(name));
        } catch (Exception e) {
            log.warn("Failed to get stats for project: {}", name, e);
            return ResponseEntity.ok(FeatureStatsDTO.of(0, 0, 0));
        }
    }

    /**
     * Update project settings (yolo_mode, model).
     */
    @PatchMapping("/{name}/settings")
    public ResponseEntity<ProjectDTO> updateProjectSettings(
            @PathVariable String name,
            @RequestBody Map<String, Object> settings) {
        if (!registryService.projectExists(name)) {
            return ResponseEntity.notFound().build();
        }

        try {
            Boolean yoloMode = settings.containsKey("yolo_mode")
                ? (Boolean) settings.get("yolo_mode")
                : null;
            String model = settings.containsKey("model")
                ? (String) settings.get("model")
                : null;

            ProjectDTO updated = projectService.updateProjectSettings(name, yoloMode, model);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to update project settings: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error updating project settings for: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
