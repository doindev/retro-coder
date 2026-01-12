package org.me.retrocoder.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.me.retrocoder.model.Conversation;
import org.me.retrocoder.service.ConversationService;
import org.me.retrocoder.service.RegistryService;
import org.me.retrocoder.websocket.AssistantChatSession;
import org.me.retrocoder.websocket.AssistantChatSessionManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for assistant chat management.
 */
@Slf4j
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantChatSessionManager sessionManager;
    private final ConversationService conversationService;
    private final RegistryService registryService;

    private static final Pattern PROJECT_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,50}$");

    // ========================================================================
    // Conversation Management
    // ========================================================================

    /**
     * List all conversations for a project.
     */
    @GetMapping("/conversations/{projectName}")
    public ResponseEntity<?> listConversations(@PathVariable String projectName) {
        if (!isValidProjectName(projectName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid project name"));
        }

        if (registryService.getProjectPath(projectName).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Conversation> conversations = conversationService.getConversations(projectName);

        List<Map<String, Object>> result = conversations.stream()
                .map(c -> {
                    Map<String, Object> conv = new HashMap<>();
                    conv.put("id", c.getId());
                    conv.put("project_name", c.getProjectName());
                    conv.put("title", c.getTitle());
                    conv.put("created_at", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
                    conv.put("updated_at", c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null);
                    conv.put("message_count", c.getMessageCount());
                    return conv;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Get a specific conversation with all messages.
     */
    @GetMapping("/conversations/{projectName}/{conversationId}")
    public ResponseEntity<?> getConversation(
            @PathVariable String projectName,
            @PathVariable Long conversationId) {

        if (!isValidProjectName(projectName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid project name"));
        }

        Optional<Conversation> convOpt = conversationService.getConversation(projectName, conversationId);
        if (convOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Conversation c = convOpt.get();

        Map<String, Object> result = new HashMap<>();
        result.put("id", c.getId());
        result.put("project_name", c.getProjectName());
        result.put("title", c.getTitle());
        result.put("created_at", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        result.put("updated_at", c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null);

        List<Map<String, Object>> messages = c.getMessages().stream()
                .map(m -> {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("id", m.getId());
                    msg.put("role", m.getRole());
                    msg.put("content", m.getContent());
                    msg.put("timestamp", m.getTimestamp() != null ? m.getTimestamp().toString() : null);
                    return msg;
                })
                .collect(Collectors.toList());

        result.put("messages", messages);

        return ResponseEntity.ok(result);
    }

    /**
     * Create a new conversation for a project.
     */
    @PostMapping("/conversations/{projectName}")
    public ResponseEntity<?> createConversation(@PathVariable String projectName) {
        if (!isValidProjectName(projectName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid project name"));
        }

        if (registryService.getProjectPath(projectName).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            Conversation c = conversationService.createConversation(projectName);

            Map<String, Object> result = new HashMap<>();
            result.put("id", c.getId());
            result.put("project_name", c.getProjectName());
            result.put("title", c.getTitle());
            result.put("created_at", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
            result.put("updated_at", c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null);
            result.put("message_count", 0);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to create conversation", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create conversation"));
        }
    }

    /**
     * Delete a conversation.
     */
    @DeleteMapping("/conversations/{projectName}/{conversationId}")
    public ResponseEntity<?> deleteConversation(
            @PathVariable String projectName,
            @PathVariable Long conversationId) {

        if (!isValidProjectName(projectName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid project name"));
        }

        boolean deleted = conversationService.deleteConversation(projectName, conversationId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Conversation deleted"));
    }

    // ========================================================================
    // Session Management
    // ========================================================================

    /**
     * List all active assistant sessions.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<String>> listSessions() {
        return ResponseEntity.ok(sessionManager.listSessions());
    }

    /**
     * Get information about an active session.
     */
    @GetMapping("/sessions/{projectName}")
    public ResponseEntity<?> getSessionInfo(@PathVariable String projectName) {
        if (!isValidProjectName(projectName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid project name"));
        }

        Optional<AssistantChatSession> sessionOpt = sessionManager.getSession(projectName);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AssistantChatSession session = sessionOpt.get();

        Map<String, Object> result = new HashMap<>();
        result.put("project_name", projectName);
        result.put("conversation_id", session.getConversationId());
        result.put("is_active", true);

        return ResponseEntity.ok(result);
    }

    /**
     * Close an active session.
     */
    @DeleteMapping("/sessions/{projectName}")
    public ResponseEntity<?> closeSession(@PathVariable String projectName) {
        if (!isValidProjectName(projectName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid project name"));
        }

        if (!sessionManager.hasSession(projectName)) {
            return ResponseEntity.notFound().build();
        }

        sessionManager.removeSession(projectName);
        return ResponseEntity.ok(Map.of("success", true, "message", "Session closed"));
    }

    /**
     * Validate project name to prevent path traversal.
     */
    private boolean isValidProjectName(String name) {
        return name != null && PROJECT_NAME_PATTERN.matcher(name).matches();
    }
}
