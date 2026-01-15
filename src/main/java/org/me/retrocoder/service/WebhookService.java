package org.me.retrocoder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.config.RetrocoderProperties;
import org.me.retrocoder.model.dto.FeatureStatsDTO;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending webhook notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final RetrocoderProperties properties;
    private final WebClient.Builder webClientBuilder;

    /**
     * Send progress notification to configured webhook.
     */
    public void sendProgressNotification(String projectName, FeatureStatsDTO stats) {
        String webhookUrl = properties.getWebhook().getN8nUrl();

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("project", projectName);
            payload.put("passing", stats.getPassing());
            payload.put("total", stats.getTotal());
            payload.put("percentage", stats.getPercentage());
            payload.put("inProgress", stats.getInProgress());

            webClientBuilder.build()
                .post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                    response -> log.debug("Webhook sent successfully: {}", response),
                    error -> log.warn("Failed to send webhook: {}", error.getMessage())
                );
        } catch (Exception e) {
            log.warn("Failed to send progress webhook", e);
        }
    }

    /**
     * Send agent status notification.
     */
    public void sendAgentStatusNotification(String projectName, String status) {
        String webhookUrl = properties.getWebhook().getN8nUrl();

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("project", projectName);
            payload.put("event", "agent_status");
            payload.put("status", status);

            webClientBuilder.build()
                .post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                    response -> log.debug("Agent status webhook sent: {}", response),
                    error -> log.warn("Failed to send agent status webhook: {}", error.getMessage())
                );
        } catch (Exception e) {
            log.warn("Failed to send agent status webhook", e);
        }
    }

    /**
     * Send feature completion notification.
     */
    public void sendFeatureCompleteNotification(String projectName, String featureName) {
        String webhookUrl = properties.getWebhook().getN8nUrl();

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("project", projectName);
            payload.put("event", "feature_complete");
            payload.put("feature", featureName);

            webClientBuilder.build()
                .post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .subscribe(
                    response -> log.debug("Feature complete webhook sent: {}", response),
                    error -> log.warn("Failed to send feature complete webhook: {}", error.getMessage())
                );
        } catch (Exception e) {
            log.warn("Failed to send feature complete webhook", e);
        }
    }
}
