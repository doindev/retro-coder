package org.me.retrocoder.agent;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Adapter that wraps a LangChain4j ChatLanguageModel to implement the ClaudeClient interface.
 * This allows using any LangChain4j-supported LLM as a coding agent.
 */
@Slf4j
public class LangChain4jClaudeClient implements ClaudeClient {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingModel;
    private final String providerName;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    /**
     * Create a client with both regular and streaming models.
     */
    public LangChain4jClaudeClient(ChatModel chatModel, StreamingChatModel streamingModel, String providerName) {
        this.chatModel = chatModel;
        this.streamingModel = streamingModel;
        this.providerName = providerName;
    }

    /**
     * Create a client with only a regular model (no streaming).
     */
    public LangChain4jClaudeClient(ChatModel chatModel, String providerName) {
        this(chatModel, null, providerName);
    }

    @Override
    public String sendPrompt(String prompt, String projectPath, Consumer<String> onChunk) {
        stopRequested.set(false);

        if (streamingModel != null) {
            return sendStreamingPrompt(prompt, onChunk);
        } else {
            return sendNonStreamingPrompt(prompt, onChunk);
        }
    }

    /**
     * Send prompt using streaming model for real-time output.
     */
    private String sendStreamingPrompt(String prompt, Consumer<String> onChunk) {
        StringBuilder fullResponse = new StringBuilder();
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            streamingModel.chat(prompt, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    if (stopRequested.get()) {
                        throw new RuntimeException("Agent stopped by user");
                    }
                    fullResponse.append(partialResponse);
                    if (onChunk != null) {
                        onChunk.accept(partialResponse);
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    future.complete(null);
                }

                @Override
                public void onError(Throwable error) {
                    future.completeExceptionally(error);
                }
            });

            // Wait for completion
            future.join();
            return fullResponse.toString();

        } catch (Exception e) {
            if (stopRequested.get() || (e.getMessage() != null && e.getMessage().contains("stopped by user"))) {
                throw new RuntimeException("Agent stopped by user");
            }
            log.error("Streaming prompt failed", e);
            throw new RuntimeException("Failed to send prompt: " + e.getMessage(), e);
        }
    }

    /**
     * Send prompt using non-streaming model.
     */
    private String sendNonStreamingPrompt(String prompt, Consumer<String> onChunk) {
        try {
            if (stopRequested.get()) {
                throw new RuntimeException("Agent stopped by user");
            }

            // For non-streaming, we get the full response at once
            ChatResponse response = chatModel.chat(UserMessage.from(prompt));
            String content = response.aiMessage().text();

            // Send the full response as a single chunk
            if (onChunk != null && content != null) {
                onChunk.accept(content);
            }

            return content;

        } catch (Exception e) {
            if (stopRequested.get()) {
                throw new RuntimeException("Agent stopped by user");
            }
            log.error("Non-streaming prompt failed", e);
            throw new RuntimeException("Failed to send prompt: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isReady() {
        return chatModel != null || streamingModel != null;
    }

    @Override
    public String getType() {
        return "langchain4j-" + providerName;
    }

    @Override
    public void close() {
        stopRequested.set(true);
        log.info("LangChain4j client closed for provider: {}", providerName);
    }

    /**
     * Request the client to stop processing.
     */
    public void requestStop() {
        stopRequested.set(true);
    }

    /**
     * Check if stop has been requested.
     */
    public boolean isStopRequested() {
        return stopRequested.get();
    }
}
