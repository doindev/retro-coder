package org.me.retrocoder.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.service.RegistryService;
import org.me.retrocoder.websocket.SpecChatSession;
import org.me.retrocoder.websocket.SpecChatSessionManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * REST controller for spec creation session management.
 */
@Slf4j
@RestController
@RequestMapping("/api/spec")
@RequiredArgsConstructor
public class SpecController {

    private final SpecChatSessionManager sessionManager;
    private final RegistryService registryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern PROJECT_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,50}$");

    /**
     * List all active spec creation sessions.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<String>> listSessions() {
        return ResponseEntity.ok(sessionManager.listSessions());
    }

    /**
     * Get status of a spec creation session.
     */
    @GetMapping("/sessions/{projectName}")
    public ResponseEntity<?> getSessionStatus(@PathVariable String projectName) {
        if (!isValidProjectName(projectName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid project name"));
        }

        Optional<SpecChatSession> sessionOpt = sessionManager.getSession(projectName);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        SpecChatSession session = sessionOpt.get();
        Map<String, Object> status = new HashMap<>();
        status.put("project_name", projectName);
        status.put("is_active", true);
        status.put("is_complete", session.isComplete());
        status.put("message_count", session.getMessageCount());

        return ResponseEntity.ok(status);
    }

    /**
     * Cancel and remove a spec creation session.
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
        return ResponseEntity.ok(Map.of("success", true, "message", "Session cancelled"));
    }

    /**
     * Get spec creation status by reading .spec_status.json from the project.
     * This is used for polling to detect when Claude has finished writing spec files.
     */
    @GetMapping("/status/{projectName}")
    public ResponseEntity<?> getSpecFileStatus(@PathVariable String projectName) {
        if (!isValidProjectName(projectName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid project name"));
        }

        Optional<String> projectPathOpt = registryService.getProjectPath(projectName);
        if (projectPathOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Path projectDir = Paths.get(projectPathOpt.get());
        if (!Files.exists(projectDir)) {
            return ResponseEntity.notFound().build();
        }

        Path statusFile = projectDir.resolve("prompts").resolve(".spec_status.json");

        if (!Files.exists(statusFile)) {
            Map<String, Object> response = new HashMap<>();
            response.put("exists", false);
            response.put("status", "not_started");
            response.put("feature_count", null);
            response.put("timestamp", null);
            response.put("files_written", List.of());
            return ResponseEntity.ok(response);
        }

        try {
            String content = Files.readString(statusFile, StandardCharsets.UTF_8);
            JsonNode data = objectMapper.readTree(content);

            Map<String, Object> response = new HashMap<>();
            response.put("exists", true);
            response.put("status", data.has("status") ? data.get("status").asText() : "unknown");
            response.put("feature_count", data.has("feature_count") ? data.get("feature_count").asInt() : null);
            response.put("timestamp", data.has("timestamp") ? data.get("timestamp").asText() : null);

            if (data.has("files_written") && data.get("files_written").isArray()) {
                List<String> filesWritten = new java.util.ArrayList<>();
                for (JsonNode file : data.get("files_written")) {
                    filesWritten.add(file.asText());
                }
                response.put("files_written", filesWritten);
            } else {
                response.put("files_written", List.of());
            }

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Error reading spec status file", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("exists", true);
            errorResponse.put("status", "error");
            errorResponse.put("feature_count", null);
            errorResponse.put("timestamp", null);
            errorResponse.put("files_written", List.of());
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Check if project has app spec file.
     */
    @GetMapping("/has-spec/{projectName}")
    public ResponseEntity<?> hasSpec(@PathVariable String projectName) {
        if (!isValidProjectName(projectName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid project name"));
        }

        Optional<String> projectPathOpt = registryService.getProjectPath(projectName);
        if (projectPathOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Path projectDir = Paths.get(projectPathOpt.get());
        Path appSpec = projectDir.resolve("prompts").resolve("app_spec.txt");
        Path legacyAppSpec = projectDir.resolve("app_spec.txt");

        boolean hasSpec = Files.exists(appSpec) || Files.exists(legacyAppSpec);

        return ResponseEntity.ok(Map.of("has_spec", hasSpec));
    }

    /**
     * Validate project name to prevent path traversal.
     */
    private boolean isValidProjectName(String name) {
        return name != null && PROJECT_NAME_PATTERN.matcher(name).matches();
    }
}
