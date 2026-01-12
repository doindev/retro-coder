package org.me.retrocoder.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.agent.ClaudeClientFactory;
import org.me.retrocoder.service.ConversationService;
import org.me.retrocoder.service.RegistryService;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages assistant chat sessions.
 * Thread-safe registry for active sessions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantChatSessionManager {

    private final ClaudeClientFactory clientFactory;
    private final RegistryService registryService;
    private final ConversationService conversationService;

    private final Map<String, AssistantChatSession> sessions = new ConcurrentHashMap<>();

    /**
     * Get an existing session for a project.
     */
    public Optional<AssistantChatSession> getSession(String projectName) {
        return Optional.ofNullable(sessions.get(projectName));
    }

    /**
     * Create a new session for a project, closing any existing one.
     */
    public AssistantChatSession createSession(String projectName, Long conversationId) {
        // Get project path from registry
        String projectPath = registryService.getProjectPath(projectName)
                .orElseThrow(() -> new IllegalArgumentException("Project not found in registry: " + projectName));

        // Close existing session if any
        AssistantChatSession oldSession = sessions.remove(projectName);
        if (oldSession != null) {
            try {
                oldSession.close();
                log.info("Closed existing assistant session for project: {}", projectName);
            } catch (Exception e) {
                log.warn("Error closing old assistant session for {}: {}", projectName, e.getMessage());
            }
        }

        // Create new session
        AssistantChatSession session = new AssistantChatSession(
                projectName,
                projectPath,
                conversationId,
                clientFactory,
                conversationService
        );
        sessions.put(projectName, session);
        log.info("Created new assistant chat session for project: {}", projectName);

        return session;
    }

    /**
     * Remove and close a session.
     */
    public void removeSession(String projectName) {
        AssistantChatSession session = sessions.remove(projectName);
        if (session != null) {
            try {
                session.close();
                log.info("Removed assistant session for project: {}", projectName);
            } catch (Exception e) {
                log.warn("Error closing assistant session for {}: {}", projectName, e.getMessage());
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
        log.info("Cleaning up {} assistant chat sessions", sessions.size());
        for (Map.Entry<String, AssistantChatSession> entry : sessions.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                log.warn("Error closing assistant session for {}: {}", entry.getKey(), e.getMessage());
            }
        }
        sessions.clear();
    }
}
