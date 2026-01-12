package org.me.retrocoder.websocket;

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
import java.util.Map;
import java.util.Optional;

/**
 * WebSocket handler for AI assistant chat.
 *
 * The assistant is a read-only project helper that can:
 * - Read and analyze source code
 * - Answer questions about the codebase
 * - Create and manage features in the backlog
 * - But CANNOT modify source code files
 *
 * Message protocol:
 *
 * Client -> Server:
 * - {"type": "start", "conversation_id": int | null} - Start/resume session
 * - {"type": "message", "content": "..."} - Send user message
 * - {"type": "ping"} - Keep-alive ping
 *
 * Server -> Client:
 * - {"type": "conversation_created", "conversation_id": int} - New conversation created
 * - {"type": "text", "content": "..."} - Text chunk from Claude
 * - {"type": "tool_call", "tool": "...", "input": {...}} - Tool being called
 * - {"type": "response_done"} - Response complete
 * - {"type": "error", "content": "..."} - Error message
 * - {"type": "pong"} - Keep-alive pong
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantChatHandler extends TextWebSocketHandler {

    private final AssistantChatSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String projectName = extractProjectName(session);
        log.info("Assistant chat WebSocket connected for project: {}", projectName);
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
                case "start" -> handleStart(session, projectName, json);
                case "message" -> handleMessage(session, projectName, json);
                default -> sendError(session, "Unknown message type: " + type);
            }
        } catch (Exception e) {
            log.error("Error handling assistant chat message", e);
            sendError(session, e.getMessage());
        }
    }

    /**
     * Handle "start" message - create or resume a session.
     */
    private void handleStart(WebSocketSession session, String projectName, JsonNode json) {
        try {
            // Get optional conversation_id to resume
            Long conversationId = null;
            if (json.has("conversation_id") && !json.get("conversation_id").isNull()) {
                conversationId = json.get("conversation_id").asLong();
            }

            // Create new session (closes any existing one)
            AssistantChatSession chatSession = sessionManager.createSession(projectName, conversationId);
            chatSession.start(session);

        } catch (IllegalArgumentException e) {
            log.error("Project not found: {}", projectName);
            sendError(session, "Project not found in registry: " + projectName);
        } catch (Exception e) {
            log.error("Failed to start assistant chat session", e);
            sendError(session, "Failed to start session: " + e.getMessage());
        }
    }

    /**
     * Handle "message" message - send user message to Claude.
     */
    private void handleMessage(WebSocketSession session, String projectName, JsonNode json) {
        Optional<AssistantChatSession> chatSessionOpt = sessionManager.getSession(projectName);
        if (chatSessionOpt.isEmpty()) {
            sendError(session, "No active session. Send 'start' first.");
            return;
        }

        AssistantChatSession chatSession = chatSessionOpt.get();
        String content = json.has("content") ? json.get("content").asText() : "";

        try {
            chatSession.sendUserMessage(session, content);
        } catch (IOException e) {
            log.error("Failed to send user message", e);
            sendError(session, "Failed to send message: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String projectName = extractProjectName(session);
        log.info("Assistant chat WebSocket disconnected for project: {} (status: {})",
                projectName, status);
        // Don't remove the session on disconnect - allow resume
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String projectName = extractProjectName(session);
        log.error("Assistant chat WebSocket transport error for project: {}", projectName, exception);
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
     * Expected path: /ws/assistant-chat/{projectName}
     */
    private String extractProjectName(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }

        String path = uri.getPath();
        String[] parts = path.split("/");
        // Path: /ws/assistant-chat/{projectName}
        // Parts: ["", "ws", "assistant-chat", "{projectName}"]
        if (parts.length >= 4 && "assistant-chat".equals(parts[2])) {
            return parts[3];
        }
        return null;
    }
}
