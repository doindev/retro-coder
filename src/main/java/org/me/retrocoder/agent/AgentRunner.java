package org.me.retrocoder.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.service.FeatureService;
import org.me.retrocoder.service.ProgressService;
import org.me.retrocoder.service.PromptService;
import org.me.retrocoder.websocket.WebSocketSessionManager;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Main agent execution loop.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRunner {

    private final ClaudeClientFactory clientFactory;
    private final FeatureService featureService;
    private final PromptService promptService;
    private final ProgressService progressService;
    private final WebSocketSessionManager webSocketSessionManager;

    private static final long AUTO_CONTINUE_DELAY_MS = 3000;
    private static final int MAX_ITERATIONS = -1; // -1 for unlimited

    /**
     * Run agent for a project.
     */
    @SuppressWarnings("unused")
	public void runAgent(String projectName, String projectPath, boolean yoloMode, String model) {
        log.info("Starting agent for project: {} (yolo={}, model={})", projectName, yoloMode, model);

        // Determine if this is initializer or coding agent
        boolean hasFeatures = featureService.hasFeatures(projectName);
        boolean isInitializer = !hasFeatures;

        int sessionNumber = 1;
        int iteration = 0;

        ClaudeClient client = clientFactory.createDefaultClient();

        try {
            while (MAX_ITERATIONS < 0 || iteration < MAX_ITERATIONS) {
                iteration++;

                // Create session
                AgentSession session = AgentSession.builder()
                    .projectName(projectName)
                    .projectPath(projectPath)
                    .sessionNumber(sessionNumber)
                    .isInitializer(isInitializer)
                    .yoloMode(yoloMode)
                    .model(model)
                    .startTime(LocalDateTime.now())
                    .build();

                // Print session header
                progressService.printSessionHeader(sessionNumber, isInitializer);

                // Send status message based on agent type
                if (isInitializer) {
                    webSocketSessionManager.broadcastLog(projectName, "🔧 INITIALIZER AGENT: Analyzing project and creating features.json...");
                    webSocketSessionManager.broadcastLog(projectName, "📂 Project path: " + projectPath);
                } else {
                    webSocketSessionManager.broadcastLog(projectName, "💻 CODING AGENT: Implementing features...");
                }

                // Load prompt
                String prompt;
                if (isInitializer) {
                    webSocketSessionManager.broadcastLog(projectName, "📝 Loading initializer prompt...");
                    prompt = promptService.getInitializerPrompt(projectPath);
                    webSocketSessionManager.broadcastLog(projectName, "✅ Prompt loaded, sending to Claude...");
                } else if (yoloMode) {
                    prompt = promptService.getCodingPromptYolo(projectPath);
                } else {
                    prompt = promptService.getCodingPrompt(projectPath);
                }

                // Send to Claude
                String response = client.sendPrompt(prompt, projectPath, line -> {
                    // Stream to WebSocket
                    webSocketSessionManager.broadcastLog(projectName, line);
                    log.debug("[Agent] {}", line);
                });

                session.setLastResponse(response);
                session.end();

                // Print progress
                progressService.printProgressSummary(projectName);

                // Send webhook if configured
                progressService.sendProgressWebhook(projectName);

                // Check if we should continue
                if (isInitializer && featureService.hasFeatures(projectName)) {
                    log.info("Initializer complete, switching to coding agent");
                    webSocketSessionManager.broadcastLog(projectName, "🎉 INITIALIZER COMPLETE: features.json created successfully!");
                    webSocketSessionManager.broadcastLog(projectName, "🔄 Switching to CODING AGENT mode...");
                    isInitializer = false;
                }

                // Auto-continue delay
                log.info("Waiting {} seconds before next session...", AUTO_CONTINUE_DELAY_MS / 1000);
                Thread.sleep(AUTO_CONTINUE_DELAY_MS);

                sessionNumber++;
            }
        } catch (InterruptedException e) {
            log.info("Agent interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Agent error", e);
            throw new RuntimeException("Agent failed: " + e.getMessage(), e);
        } finally {
            client.close();
        }
    }
}
