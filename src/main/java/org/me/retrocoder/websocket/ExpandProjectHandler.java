package org.me.retrocoder.websocket;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket handler for project expansion (adding new features).
 *
 * Message protocol:
 *
 * Client -> Server:
 * - {"type": "start"} - Start the expansion session
 * - {"type": "message", "content": "..."} - Send user message
 * - {"type": "message", "content": "...", "attachments": [...]} - Send message with images
 * - {"type": "done"} - User is done adding features
 * - {"type": "ping"} - Keep-alive ping
 *
 * Server -> Client:
 * - {"type": "text", "content": "..."} - Text chunk from Claude
 * - {"type": "features_created", "count": N, "features": [...]} - Features added
 * - {"type": "expansion_complete", "total_added": N} - Session complete
 * - {"type": "response_done"} - Response complete
 * - {"type": "error", "content": "..."} - Error message
 * - {"type": "pong"} - Keep-alive pong
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpandProjectHandler extends TextWebSocketHandler {

    private final ExpandChatSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String projectName = extractProjectName(session);
        log.info("Expand project WebSocket connected for project: {}", projectName);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String projectName = extractProjectName(session);
        if (projectName == null) {
            sendError(session, "Invalid WebSocket path");
            return;
        }

        try {
            JsonNode json = objectMapper.readTree(message.getPayload());
            String type = json.has("type") ? json.get("type").asText() : null;

            switch (type) {
                case "ping" -> sendPong(session);
                case "start" -> handleStart(session, projectName);
                case "message" -> handleMessage(session, projectName, json);
                case "done" -> handleDone(session, projectName);
                default -> sendError(session, "Unknown message type: " + type);
            }
        } catch (Exception e) {
            log.error("Error handling expand project message", e);
            sendError(session, e.getMessage());
        }
    }

    /**
     * Handle "start" message - create or resume a session.
     */
    private void handleStart(WebSocketSession session, String projectName) {
        try {
            // Check if session already exists (idempotent start)
            Optional<ExpandChatSession> existingOpt = sessionManager.getSession(projectName);
            if (existingOpt.isPresent()) {
                sendMessage(session, Map.of(
                        "type", "text",
                        "content", "Resuming existing expansion session. What would you like to add?"
                ));
                sendMessage(session, Map.of("type", "response_done"));
                return;
            }

            // Create and start a new session
            ExpandChatSession chatSession = sessionManager.createSession(projectName);
            chatSession.start(session);
        } catch (IllegalArgumentException e) {
            log.error("Project not found: {}", projectName);
            sendError(session, "Project not found in registry: " + projectName);
        } catch (Exception e) {
            log.error("Failed to start expand project session", e);
            sendError(session, "Failed to start session: " + e.getMessage());
        }
    }

    /**
     * Handle "message" message - send user message to Claude.
     */
    private void handleMessage(WebSocketSession session, String projectName, JsonNode json) {
        Optional<ExpandChatSession> chatSessionOpt = sessionManager.getSession(projectName);
        if (chatSessionOpt.isEmpty()) {
            sendError(session, "No active session. Send 'start' first.");
            return;
        }

        ExpandChatSession chatSession = chatSessionOpt.get();
        String content = json.has("content") ? json.get("content").asText() : "";

        // Parse attachments if present
        List<Map<String, Object>> attachments = null;
        if (json.has("attachments") && json.get("attachments").isArray()) {
            try {
                attachments = objectMapper.convertValue(
                        json.get("attachments"),
                        new TypeReference<List<Map<String, Object>>>() {}
                );
            } catch (Exception e) {
                log.warn("Invalid attachment data: {}", e.getMessage());
                sendError(session, "Invalid attachment: " + e.getMessage());
                return;
            }
        }

        try {
            chatSession.sendUserMessage(session, content, attachments);
        } catch (IOException e) {
            log.error("Failed to send user message", e);
            sendError(session, "Failed to send message: " + e.getMessage());
        }
    }

    /**
     * Handle "done" message - user is done adding features.
     */
    private void handleDone(WebSocketSession session, String projectName) {
        Optional<ExpandChatSession> chatSessionOpt = sessionManager.getSession(projectName);
        if (chatSessionOpt.isPresent()) {
            try {
                chatSessionOpt.get().markComplete(session);
            } catch (IOException e) {
                log.error("Failed to mark session complete", e);
                sendError(session, "Failed to complete session");
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String projectName = extractProjectName(session);
        log.info("Expand project WebSocket disconnected for project: {} (status: {})",
                projectName, status);
        // Don't remove the session on disconnect - allow resume
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String projectName = extractProjectName(session);
        log.error("Expand project WebSocket transport error for project: {}", projectName, exception);
    }

    /**
     * Send a pong response.
     */
    private void sendPong(WebSocketSession session) {
        sendMessage(session, Map.of("type", "pong"));
    }

    /**
     * Send an error message.
     */
    private void sendError(WebSocketSession session, String content) {
        sendMessage(session, Map.of("type", "error", "content", content));
    }

    /**
     * Send a message to the WebSocket.
     */
    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            }
        } catch (Exception e) {
            log.error("Failed to send message", e);
        }
    }

    /**
     * Extract project name from the WebSocket URI.
     * Expected path: /ws/expand-project/{projectName}
     */
    private String extractProjectName(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }

        String path = uri.getPath();
        String[] parts = path.split("/");
        // Path: /ws/expand-project/{projectName}
        // Parts: ["", "ws", "expand-project", "{projectName}"]
        if (parts.length >= 4 && "expand-project".equals(parts[2])) {
            return parts[3];
        }
        return null;
    }
}
