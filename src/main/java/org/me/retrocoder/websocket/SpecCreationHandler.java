package org.me.retrocoder.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * WebSocket handler for interactive spec creation.
 *
 * Message protocol:
 *
 * Client -> Server:
 * - {"type": "start"} - Start the spec creation session
 * - {"type": "message", "content": "..."} - Send user message
 * - {"type": "message", "content": "...", "attachments": [...]} - Send message with images
 * - {"type": "answer", "answers": {...}} - Answer structured question
 * - {"type": "ping"} - Keep-alive ping
 *
 * Server -> Client:
 * - {"type": "text", "content": "..."} - Text chunk from Claude
 * - {"type": "question", "questions": [...]} - Structured question
 * - {"type": "spec_complete", "path": "..."} - Spec file created
 * - {"type": "file_written", "path": "..."} - Other file written
 * - {"type": "complete", "path": "..."} - Session complete
 * - {"type": "response_done"} - Response streaming finished
 * - {"type": "error", "content": "..."} - Error message
 * - {"type": "pong"} - Keep-alive pong
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpecCreationHandler extends TextWebSocketHandler {

    private final SpecChatSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String projectName = extractProjectName(session);
        log.info("Spec creation WebSocket connected for project: {}", projectName);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String projectName = extractProjectName(session);
        if (projectName == null) {
            sendError(session, "Invalid WebSocket path");
            return;
        }

        // Log raw WebSocket message size
        String payload = message.getPayload();
        log.debug("WebSocket message received: {} bytes", payload.length());

        try {
            JsonNode json = objectMapper.readTree(payload);
            String type = json.has("type") ? json.get("type").asText() : null;

            switch (type) {
                case "ping" -> sendPong(session);
                case "start" -> handleStart(session, projectName);
                case "message" -> handleMessage(session, projectName, json);
                case "answer" -> handleAnswer(session, projectName, json);
                default -> sendError(session, "Unknown message type: " + type);
            }
        } catch (Exception e) {
            log.error("Error handling spec creation message", e);
            sendError(session, e.getMessage());
        }
    }

    /**
     * Handle "start" message - create and start a new session.
     */
    private void handleStart(WebSocketSession session, String projectName) {
        try {
            SpecChatSession chatSession = sessionManager.createSession(projectName);
            chatSession.start(session);
        } catch (IllegalArgumentException e) {
            log.error("Project not found: {}", projectName);
            sendError(session, "Project not found in registry: " + projectName);
        } catch (Exception e) {
            log.error("Failed to start spec creation session", e);
            sendError(session, "Failed to start session: " + e.getMessage());
        }
    }

    /**
     * Handle "message" message - send user message to Claude.
     */
    private void handleMessage(WebSocketSession session, String projectName, JsonNode json) {
        Optional<SpecChatSession> chatSessionOpt = sessionManager.getSession(projectName);
        if (chatSessionOpt.isEmpty()) {
            sendError(session, "No active session. Send 'start' first.");
            return;
        }

        SpecChatSession chatSession = chatSessionOpt.get();
        String content = json.has("content") ? json.get("content").asText() : "";

        // Log received message size for debugging
        log.info("Received user message: {} chars", content.length());

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
     * Handle "answer" message - user answered a structured question.
     */
    private void handleAnswer(WebSocketSession session, String projectName, JsonNode json) {
        Optional<SpecChatSession> chatSessionOpt = sessionManager.getSession(projectName);
        if (chatSessionOpt.isEmpty()) {
            sendError(session, "No active session");
            return;
        }

        SpecChatSession chatSession = chatSessionOpt.get();

        // Parse answers
        Map<String, Object> answers = new HashMap<>();
        if (json.has("answers")) {
            try {
                answers = objectMapper.convertValue(
                        json.get("answers"),
                        new TypeReference<Map<String, Object>>() {}
                );
            } catch (Exception e) {
                log.warn("Invalid answer data: {}", e.getMessage());
                sendError(session, "Invalid answer format");
                return;
            }
        }

        try {
            chatSession.handleAnswer(session, answers);
        } catch (IOException e) {
            log.error("Failed to handle answer", e);
            sendError(session, "Failed to process answer: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String projectName = extractProjectName(session);
        log.info("Spec creation WebSocket disconnected for project: {} (status: {})",
                projectName, status);
        // Don't remove the session on disconnect - allow resume
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String projectName = extractProjectName(session);
        log.error("Spec creation WebSocket transport error for project: {}", projectName, exception);
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
     * Expected path: /ws/spec-creation/{projectName}
     */
    private String extractProjectName(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }

        String path = uri.getPath();
        String[] parts = path.split("/");
        // Path: /ws/spec-creation/{projectName}
        // Parts: ["", "ws", "spec-creation", "{projectName}"]
        if (parts.length >= 4 && "spec-creation".equals(parts[2])) {
            return parts[3];
        }
        return null;
    }
}
