package org.me.retrocoder.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.model.dto.AgentStartRequestDTO;
import org.me.retrocoder.model.dto.AgentStatusDTO;
import org.me.retrocoder.service.AgentService;
import org.me.retrocoder.service.RegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for agent control.
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectName}/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;
    private final RegistryService registryService;

    /**
     * Get agent status.
     */
    @GetMapping("/status")
    public ResponseEntity<AgentStatusDTO> getStatus(@PathVariable String projectName) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(agentService.getStatus(projectName));
    }

    /**
     * Start agent.
     * Priority for settings: 1) project-specific, 2) request body, 3) global defaults
     */
    @PostMapping("/start")
    public ResponseEntity<AgentStatusDTO> startAgent(
            @PathVariable String projectName,
            @RequestBody(required = false) AgentStartRequestDTO request) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        try {
            // Get project to check for project-specific settings
            var project = registryService.getProject(projectName).orElse(null);

            // Priority: project settings > request body > global defaults
            boolean yoloMode;
            if (project != null && project.getYoloMode() != null) {
                yoloMode = project.getYoloMode();
            } else if (request != null && Boolean.TRUE.equals(request.getYoloMode())) {
                yoloMode = true;
            } else {
                yoloMode = registryService.isYoloMode();
            }

            String model;
            if (project != null && project.getModel() != null) {
                model = project.getModel();
            } else if (request != null && request.getModel() != null) {
                model = request.getModel();
            } else {
                model = registryService.getDefaultModel();
            }

            agentService.startAgent(projectName, yoloMode, model);

            return ResponseEntity.ok(agentService.getStatus(projectName));
        } catch (IllegalStateException e) {
            log.warn("Failed to start agent: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                AgentStatusDTO.crashed(e.getMessage())
            );
        } catch (Exception e) {
            log.error("Error starting agent for project: {}", projectName, e);
            return ResponseEntity.internalServerError().body(
                AgentStatusDTO.crashed(e.getMessage())
            );
        }
    }

    /**
     * Stop agent.
     */
    @PostMapping("/stop")
    public ResponseEntity<AgentStatusDTO> stopAgent(@PathVariable String projectName) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        try {
            agentService.stopAgent(projectName);
            return ResponseEntity.ok(agentService.getStatus(projectName));
        } catch (Exception e) {
            log.error("Error stopping agent for project: {}", projectName, e);
            return ResponseEntity.internalServerError().body(
                AgentStatusDTO.crashed(e.getMessage())
            );
        }
    }

    /**
     * Pause agent.
     */
    @PostMapping("/pause")
    public ResponseEntity<AgentStatusDTO> pauseAgent(@PathVariable String projectName) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        try {
            agentService.pauseAgent(projectName);
            return ResponseEntity.ok(agentService.getStatus(projectName));
        } catch (IllegalStateException e) {
            log.warn("Failed to pause agent: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                AgentStatusDTO.crashed(e.getMessage())
            );
        } catch (Exception e) {
            log.error("Error pausing agent for project: {}", projectName, e);
            return ResponseEntity.internalServerError().body(
                AgentStatusDTO.crashed(e.getMessage())
            );
        }
    }

    /**
     * Resume agent.
     */
    @PostMapping("/resume")
    public ResponseEntity<AgentStatusDTO> resumeAgent(@PathVariable String projectName) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        try {
            agentService.resumeAgent(projectName);
            return ResponseEntity.ok(agentService.getStatus(projectName));
        } catch (IllegalStateException e) {
            log.warn("Failed to resume agent: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                AgentStatusDTO.crashed(e.getMessage())
            );
        } catch (Exception e) {
            log.error("Error resuming agent for project: {}", projectName, e);
            return ResponseEntity.internalServerError().body(
                AgentStatusDTO.crashed(e.getMessage())
            );
        }
    }
}
