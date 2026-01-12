package org.me.retrocoder.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.model.dto.FeatureStatsDTO;
import org.me.retrocoder.service.FeatureService;
import org.me.retrocoder.service.RegistryService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;

/**
 * WebSocket handler for project real-time updates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final FeatureService featureService;
    private final RegistryService registryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String projectName = extractProjectName(session);
        log.info("WebSocket connection attempt for project: {}", projectName);

        if (projectName == null) {
            log.warn("WebSocket connection rejected: no project name in path");
            try {
                session.close(CloseStatus.BAD_DATA.withReason("No project name"));
            } catch (Exception e) {
                log.warn("Error closing session", e);
            }
            return;
        }

        boolean exists = registryService.projectExists(projectName);
        log.info("Project '{}' exists: {}", projectName, exists);

        if (!exists) {
            log.warn("WebSocket connection rejected: project '{}' not found", projectName);
            try {
                session.close(CloseStatus.BAD_DATA.withReason("Project not found: " + projectName));
            } catch (Exception e) {
                log.warn("Error closing session", e);
            }
            return;
        }

        sessionManager.registerSession(projectName, session);
        log.info("WebSocket connected for project: {}", projectName);

        // Send initial progress
        try {
            FeatureStatsDTO stats = featureService.getStats(projectName);
            sessionManager.broadcastProgress(projectName, stats.getPassing(), stats.getInProgress(), stats.getTotal());
        } catch (Exception e) {
            log.warn("Failed to send initial progress for project: {}", projectName, e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String projectName = extractProjectName(session);
        if (projectName == null) {
            return;
        }

        try {
            JsonNode json = objectMapper.readTree(message.getPayload());
            String type = json.has("type") ? json.get("type").asText() : null;

            if ("ping".equals(type)) {
                sessionManager.sendPong(session);
            }
        } catch (Exception e) {
            log.warn("Failed to parse WebSocket message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String projectName = extractProjectName(session);
        if (projectName != null) {
            sessionManager.unregisterSession(projectName, session);
            log.info("WebSocket disconnected for project: {} ({})", projectName, status.getReason());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String projectName = extractProjectName(session);
        log.error("WebSocket transport error for project {}: {}", projectName, exception.getMessage());

        if (projectName != null) {
            sessionManager.unregisterSession(projectName, session);
        }
    }

    private String extractProjectName(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }

        String path = uri.getPath();
        // Path format: /ws/projects/{projectName}
        String[] parts = path.split("/");
        if (parts.length >= 4 && "projects".equals(parts[2])) {
            return parts[3];
        }
        return null;
    }
}
