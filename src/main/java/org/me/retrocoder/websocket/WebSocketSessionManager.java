package org.me.retrocoder.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.model.AgentStatus;
import org.me.retrocoder.model.dto.WebSocketMessageDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manager for WebSocket sessions.
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    private final Map<String, Set<WebSocketSession>> projectSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Register a session for a project.
     */
    public void registerSession(String projectName, WebSocketSession session) {
        projectSessions.computeIfAbsent(projectName, k -> new CopyOnWriteArraySet<>()).add(session);
        log.debug("Session registered for project {}: {}", projectName, session.getId());
    }

    /**
     * Unregister a session.
     */
    public void unregisterSession(String projectName, WebSocketSession session) {
        Set<WebSocketSession> sessions = projectSessions.get(projectName);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                projectSessions.remove(projectName);
            }
        }
        log.debug("Session unregistered for project {}: {}", projectName, session.getId());
    }

    /**
     * Broadcast message to all sessions for a project.
     */
    public void broadcast(String projectName, WebSocketMessageDTO message) {
        Set<WebSocketSession> sessions = projectSessions.get(projectName);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        synchronized (session) {
                            session.sendMessage(textMessage);
                        }
                    } catch (IOException e) {
                        log.warn("Failed to send message to session {}", session.getId(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize message", e);
        }
    }

    /**
     * Broadcast progress update.
     */
    public void broadcastProgress(String projectName, int passing, int inProgress, int total) {
        broadcast(projectName, WebSocketMessageDTO.progress(passing, inProgress, total));
    }

    /**
     * Broadcast feature update.
     */
    public void broadcastFeatureUpdate(String projectName, Long featureId, boolean passes) {
        broadcast(projectName, WebSocketMessageDTO.featureUpdate(featureId, passes));
    }

    /**
     * Broadcast log message.
     */
    public void broadcastLog(String projectName, String line) {
        broadcast(projectName, WebSocketMessageDTO.log(line));
    }

    /**
     * Broadcast agent status.
     */
    public void broadcastAgentStatus(String projectName, AgentStatus status) {
        broadcast(projectName, WebSocketMessageDTO.agentStatus(status));
    }

    /**
     * Send pong response to a specific session.
     */
    public void sendPong(WebSocketSession session) {
        try {
            String json = objectMapper.writeValueAsString(WebSocketMessageDTO.pong());
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.warn("Failed to send pong to session {}", session.getId(), e);
        }
    }

    /**
     * Get number of active sessions for a project.
     */
    public int getSessionCount(String projectName) {
        Set<WebSocketSession> sessions = projectSessions.get(projectName);
        return sessions != null ? sessions.size() : 0;
    }

    /**
     * Check if project has active sessions.
     */
    public boolean hasActiveSessions(String projectName) {
        return getSessionCount(projectName) > 0;
    }
}
