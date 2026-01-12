package org.me.retrocoder.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.service.RegistryService;
import org.me.retrocoder.websocket.ExpandChatSession;
import org.me.retrocoder.websocket.ExpandChatSessionManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * REST controller for expand project session management.
 */
@Slf4j
@RestController
@RequestMapping("/api/expand")
@RequiredArgsConstructor
public class ExpandController {

    private final ExpandChatSessionManager sessionManager;
    private final RegistryService registryService;

    private static final Pattern PROJECT_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,50}$");

    /**
     * List all active expansion sessions.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<String>> listSessions() {
        return ResponseEntity.ok(sessionManager.listSessions());
    }

    /**
     * Get status of an expansion session.
     */
    @GetMapping("/sessions/{projectName}")
    public ResponseEntity<?> getSessionStatus(@PathVariable String projectName) {
        if (!isValidProjectName(projectName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid project name"));
        }

        Optional<ExpandChatSession> sessionOpt = sessionManager.getSession(projectName);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ExpandChatSession session = sessionOpt.get();
        Map<String, Object> status = new HashMap<>();
        status.put("project_name", projectName);
        status.put("is_active", true);
        status.put("is_complete", session.isComplete());
        status.put("features_created", session.getFeaturesCreated());
        status.put("message_count", session.getMessageCount());

        return ResponseEntity.ok(status);
    }

    /**
     * Cancel and remove an expansion session.
     */
    @DeleteMapping("/sessions/{projectName}")
    public ResponseEntity<?> cancelSession(@PathVariable String projectName) {
        if (!isValidProjectName(projectName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid project name"));
        }

        if (!sessionManager.hasSession(projectName)) {
            return ResponseEntity.notFound().build();
        }

        sessionManager.removeSession(projectName);
        return ResponseEntity.ok(Map.of("success", true, "message", "Expansion session cancelled"));
    }

    /**
     * Check if project can be expanded (has app_spec.txt).
     */
    @GetMapping("/can-expand/{projectName}")
    public ResponseEntity<?> canExpand(@PathVariable String projectName) {
        if (!isValidProjectName(projectName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid project name"));
        }

        Optional<String> projectPathOpt = registryService.getProjectPath(projectName);
        if (projectPathOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Path projectDir = Paths.get(projectPathOpt.get());
        Path appSpec = projectDir.resolve("prompts").resolve("app_spec.txt");

        boolean canExpand = Files.exists(appSpec);
        String reason = canExpand ? null : "Project has no app_spec.txt. Create spec first.";

        Map<String, Object> response = new HashMap<>();
        response.put("can_expand", canExpand);
        if (reason != null) {
            response.put("reason", reason);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Validate project name to prevent path traversal.
     */
    private boolean isValidProjectName(String name) {
        return name != null && PROJECT_NAME_PATTERN.matcher(name).matches();
    }
}
