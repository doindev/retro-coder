package org.me.retrocoder.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.agent.ClaudeClientFactory;
import org.me.retrocoder.service.FeatureService;
import org.me.retrocoder.service.RegistryService;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages expand project chat sessions.
 * Thread-safe registry for active sessions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpandChatSessionManager {

    private final ClaudeClientFactory clientFactory;
    private final RegistryService registryService;
    private final FeatureService featureService;

    private final Map<String, ExpandChatSession> sessions = new ConcurrentHashMap<>();

    /**
     * Get an existing session for a project.
     */
    public Optional<ExpandChatSession> getSession(String projectName) {
        return Optional.ofNullable(sessions.get(projectName));
    }

    /**
     * Create a new session for a project, closing any existing one.
     */
    public ExpandChatSession createSession(String projectName) {
        // Get project path from registry
        String projectPath = registryService.getProjectPath(projectName)
                .orElseThrow(() -> new IllegalArgumentException("Project not found in registry: " + projectName));

        // Close existing session if any
        ExpandChatSession oldSession = sessions.remove(projectName);
        if (oldSession != null) {
            try {
                oldSession.close();
                log.info("Closed existing expand session for project: {}", projectName);
            } catch (Exception e) {
                log.warn("Error closing old expand session for {}: {}", projectName, e.getMessage());
            }
        }

        // Create new session
        ExpandChatSession session = new ExpandChatSession(projectName, projectPath, clientFactory, featureService);
        sessions.put(projectName, session);
        log.info("Created new expand chat session for project: {}", projectName);

        return session;
    }

    /**
     * Remove and close a session.
     */
    public void removeSession(String projectName) {
        ExpandChatSession session = sessions.remove(projectName);
        if (session != null) {
            try {
                session.close();
                log.info("Removed expand session for project: {}", projectName);
            } catch (Exception e) {
                log.warn("Error closing expand session for {}: {}", projectName, e.getMessage());
            }
        }
    }

    /**
     * List all active session project names.
     */
    public List<String> listSessions() {
        return List.copyOf(sessions.keySet());
    }

    /**
     * Check if a session exists for a project.
     */
    public boolean hasSession(String projectName) {
        return sessions.containsKey(projectName);
    }

    /**
     * Get session count.
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * Cleanup all sessions on shutdown.
     */
    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up {} expand chat sessions", sessions.size());
        for (Map.Entry<String, ExpandChatSession> entry : sessions.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                log.warn("Error closing expand session for {}: {}", entry.getKey(), e.getMessage());
            }
        }
        sessions.clear();
    }
}
