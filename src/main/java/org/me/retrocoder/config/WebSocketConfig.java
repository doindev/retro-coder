package org.me.retrocoder.config;

import org.me.retrocoder.websocket.AssistantChatHandler;
import org.me.retrocoder.websocket.ExpandProjectHandler;
import org.me.retrocoder.websocket.ProjectWebSocketHandler;
import org.me.retrocoder.websocket.SpecCreationHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import lombok.RequiredArgsConstructor;

/**
 * WebSocket configuration for real-time communication.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ProjectWebSocketHandler projectWebSocketHandler;
    private final SpecCreationHandler specCreationHandler;
    private final ExpandProjectHandler expandProjectHandler;
    private final AssistantChatHandler assistantChatHandler;

    /**
     * Configure WebSocket container to handle larger messages.
     * Default text buffer is 8KB which is too small for large prompts.
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // Set max text message buffer size to 1MB (for large prompts/responses)
        container.setMaxTextMessageBufferSize(1024 * 1024);
        // Set max binary message buffer size to 10MB (for image attachments)
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);
        // Set max session idle timeout to 30 minutes
        container.setMaxSessionIdleTimeout(30 * 60 * 1000L);
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Project updates WebSocket - use * for single path segment wildcard
        registry.addHandler(projectWebSocketHandler, "/ws/projects/*")
            .setAllowedOriginPatterns("*");

        // Spec creation WebSocket
        registry.addHandler(specCreationHandler, "/ws/spec-creation/*")
            .setAllowedOriginPatterns("*");

        // Expand project WebSocket
        registry.addHandler(expandProjectHandler, "/ws/expand-project/*")
            .setAllowedOriginPatterns("*");

        // Assistant chat WebSocket
        registry.addHandler(assistantChatHandler, "/ws/assistant-chat/*")
            .setAllowedOriginPatterns("*");
    }
}
