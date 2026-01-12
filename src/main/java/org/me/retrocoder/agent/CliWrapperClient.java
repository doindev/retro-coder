package org.me.retrocoder.agent;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Claude client that wraps the Claude CLI.
 * Uses subprocess to execute the claude command.
 */
@Slf4j
public class CliWrapperClient implements ClaudeClient {

    private final String cliCommand;
    private volatile Process currentProcess;
    private volatile boolean stopRequested;

    public CliWrapperClient(String cliCommand) {
        this.cliCommand = cliCommand != null ? cliCommand : "claude";
        this.stopRequested = false;
    }

    @Override
    public String sendPrompt(String prompt, String projectPath, Consumer<String> onChunk) {
        StringBuilder response = new StringBuilder();
        stopRequested = false;

        try {
            List<String> command = new ArrayList<>();

            // On Windows, run through cmd.exe to find commands in PATH
            if (isWindows()) {
                command.add("cmd.exe");
                command.add("/c");
            }

            command.add(cliCommand);
            command.add("--print");
            command.add("--dangerously-skip-permissions");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(projectPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            currentProcess = process;

            // Write prompt to stdin (avoids Windows command line length limit)
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream()))) {
                writer.write(prompt);
                writer.flush();
            }

            // Read output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Check for stop request
                    if (stopRequested) {
                        log.info("Stop requested, terminating Claude CLI process");
                        process.destroyForcibly();
                        throw new RuntimeException("Agent stopped by user");
                    }

                    response.append(line).append("\n");
                    if (onChunk != null) {
                        onChunk.accept(line);
                    }

                    // Detect token/rate limit errors
                    String lineLower = line.toLowerCase();
                    if (lineLower.contains("rate limit") || lineLower.contains("rate_limit") ||
                        lineLower.contains("token limit") || lineLower.contains("too many requests") ||
                        lineLower.contains("429") || lineLower.contains("quota exceeded")) {
                        log.warn("Detected rate/token limit error: {}", line);
                        throw new RateLimitException("Rate limit detected: " + line);
                    }
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Claude CLI timed out");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.warn("Claude CLI exited with code: {}", exitCode);
            }

        } catch (RateLimitException e) {
            throw e; // Re-throw rate limit exceptions
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("stopped by user")) {
                throw new RuntimeException(e.getMessage(), e);
            }
            log.error("Error running Claude CLI", e);
            throw new RuntimeException("Failed to run Claude CLI: " + e.getMessage(), e);
        } finally {
            currentProcess = null;
        }

        return response.toString();
    }

    /**
     * Request the client to stop the current operation.
     */
    public void requestStop() {
        stopRequested = true;
        Process process = currentProcess;
        if (process != null && process.isAlive()) {
            log.info("Forcibly destroying Claude CLI process");
            process.destroyForcibly();
        }
    }

    @Override
    public boolean isReady() {
        try {
            List<String> command = new ArrayList<>();

            // On Windows, run through cmd.exe to find commands in PATH
            if (isWindows()) {
                command.add("cmd.exe");
                command.add("/c");
            }

            command.add(cliCommand);
            command.add("--version");

            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            log.warn("Claude CLI not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getType() {
        return "CLI_WRAPPER";
    }

    @Override
    public void close() {
        requestStop();
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
