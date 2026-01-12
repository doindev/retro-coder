package org.me.retrocoder.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Claude client using direct Anthropic REST API.
 */
@Slf4j
public class RestApiClient implements ClaudeClient {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public RestApiClient(WebClient.Builder webClientBuilder, String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;

        this.webClient = webClientBuilder
            .baseUrl(ANTHROPIC_API_URL)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
            .build();
    }

    @Override
    public String sendPrompt(String prompt, String projectPath, Consumer<String> onChunk) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("max_tokens", 4096);
        request.put("messages", List.of(
            Map.of("role", "user", "content", prompt)
        ));
        request.put("stream", true);

        StringBuilder response = new StringBuilder();

        try {
            Flux<String> eventStream = webClient.post()
                .uri("/v1/messages")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class);

            eventStream.doOnNext(chunk -> {
                // Parse SSE events
                if (chunk.startsWith("data: ")) {
                    String data = chunk.substring(6);
                    if (!"[DONE]".equals(data)) {
                        try {
                            // Parse JSON and extract text
                            // For simplicity, we're appending the raw chunk
                            response.append(data);
                            if (onChunk != null) {
                                onChunk.accept(data);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse chunk: {}", chunk);
                        }
                    }
                }
            }).blockLast();

        } catch (Exception e) {
            log.error("Error calling Anthropic API", e);
            throw new RuntimeException("Failed to call Anthropic API: " + e.getMessage(), e);
        }

        return response.toString();
    }

    @Override
    public boolean isReady() {
        return apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public String getType() {
        return "REST_API";
    }
}
