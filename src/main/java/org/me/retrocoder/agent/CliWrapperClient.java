package org.me.retrocoder.agent;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Claude client that wraps the Claude CLI.
 * Uses subprocess to execute the claude command.
 */
@Slf4j
public class CliWrapperClient implements ClaudeClient {

    private final String cliCommand;
    private volatile Process currentProcess;
    private volatile boolean stopRequested;

    // Patterns to detect Claude CLI activity
    private static final Pattern READ_PATTERN = Pattern.compile("(?:Read|Reading)(?:\\s+file)?[:\\s]+([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WRITE_PATTERN = Pattern.compile("(?:Write|Writing|Wrote)(?:\\s+(?:to\\s+)?file)?[:\\s]+([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BASH_PATTERN = Pattern.compile("(?:Bash|Running|Executing)[:\\s]+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOOL_USE_PATTERN = Pattern.compile("⏺\\s*(\\w+)");
    private static final Pattern THINKING_PATTERN = Pattern.compile("(?:thinking|analyzing|considering|planning)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEATURE_PATTERN = Pattern.compile("feature[s]?(?:\\.json)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_WRITE_PATTERN = Pattern.compile("\"path\"\\s*:\\s*\"([^\"]+)\"");

    public CliWrapperClient(String cliCommand) {
        this.cliCommand = cliCommand != null ? cliCommand : "claude";
        this.stopRequested = false;
    }

    /**
     * Parse a line and return a more descriptive message if a pattern is detected.
     */
    private String parseLineForActivity(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        // Check for tool use indicator (⏺ symbol)
        Matcher toolMatcher = TOOL_USE_PATTERN.matcher(line);
        if (toolMatcher.find()) {
            String tool = toolMatcher.group(1);
            return switch (tool.toLowerCase()) {
                case "read" -> "📖 Reading file...";
                case "write" -> "✏️ Writing file...";
                case "bash" -> "🔧 Running command...";
                case "glob" -> "🔍 Searching for files...";
                case "grep" -> "🔍 Searching in files...";
                case "edit" -> "✏️ Editing file...";
                case "todowrite" -> "📋 Updating task list...";
                default -> "🔧 " + tool + "...";
            };
        }

        // Check for file read operations
        Matcher readMatcher = READ_PATTERN.matcher(line);
        if (readMatcher.find()) {
            String file = readMatcher.group(1);
            return "📖 Reading: " + truncatePath(file);
        }

        // Check for file write operations
        Matcher writeMatcher = WRITE_PATTERN.matcher(line);
        if (writeMatcher.find()) {
            String file = writeMatcher.group(1);
            return "✏️ Writing: " + truncatePath(file);
        }

        // Check for bash/command execution
        Matcher bashMatcher = BASH_PATTERN.matcher(line);
        if (bashMatcher.find()) {
            String cmd = bashMatcher.group(1);
            return "🔧 Running: " + truncateCommand(cmd);
        }

        // Check for features.json activity
        if (FEATURE_PATTERN.matcher(line).find()) {
            if (line.toLowerCase().contains("writ") || line.toLowerCase().contains("creat")) {
                return "📋 Writing features.json...";
            } else if (line.toLowerCase().contains("read") || line.toLowerCase().contains("load")) {
                return "📋 Loading features...";
            }
        }

        // Check for JSON path patterns (often seen when writing files)
        Matcher jsonPathMatcher = JSON_WRITE_PATTERN.matcher(line);
        if (jsonPathMatcher.find()) {
            String path = jsonPathMatcher.group(1);
            if (path.contains("features.json")) {
                return "📋 Updating features.json...";
            }
        }

        // Check for thinking/analyzing patterns
        if (THINKING_PATTERN.matcher(line).find()) {
            return "🤔 Analyzing...";
        }

        return null;
    }

    /**
     * Truncate a file path to show only the last 2 components.
     */
    private String truncatePath(String path) {
        if (path == null) return "";
        String[] parts = path.replace("\\", "/").split("/");
        if (parts.length <= 2) return path;
        return ".../" + parts[parts.length - 2] + "/" + parts[parts.length - 1];
    }

    /**
     * Truncate a command to a reasonable length.
     */
    private String truncateCommand(String cmd) {
        if (cmd == null) return "";
        cmd = cmd.trim();
        if (cmd.length() <= 50) return cmd;
        return cmd.substring(0, 47) + "...";
    }

    @Override
    public String sendPrompt(String prompt, String projectPath, Consumer<String> onChunk) {
        StringBuilder response = new StringBuilder();
        stopRequested = false;

        // Clean up any existing temp files BEFORE starting a new process
        // This prevents accumulation if previous processes left files behind
        cleanupTempClaudeFiles(projectPath);

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

            // Notify that we're starting
            if (onChunk != null) {
                onChunk.accept("🚀 Starting Claude agent...");
            }

            // Read output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                String lastActivity = null;
                int lineCount = 0;
                boolean receivedFirstOutput = false;

                while ((line = reader.readLine()) != null) {
                    lineCount++;

                    // Check for stop request
                    if (stopRequested) {
                        log.info("Stop requested, terminating Claude CLI process tree");
                        killProcessTree(process);
                        throw new RuntimeException("Agent stopped by user");
                    }

                    // Notify on first output
                    if (!receivedFirstOutput && onChunk != null) {
                        receivedFirstOutput = true;
                        onChunk.accept("📡 Receiving response from Claude...");
                    }

                    response.append(line).append("\n");

                    // Parse for activity and emit descriptive message
                    String activity = parseLineForActivity(line);
                    if (activity != null && !activity.equals(lastActivity) && onChunk != null) {
                        onChunk.accept(activity);
                        lastActivity = activity;
                    }

                    // Also emit the raw line
                    if (onChunk != null) {
                        onChunk.accept(line);
                    }

                    // Periodic progress indicator every 50 lines
                    if (lineCount % 50 == 0 && onChunk != null) {
                        onChunk.accept("⏳ Processing... (" + lineCount + " lines)");
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

                // Final message
                if (onChunk != null) {
                    onChunk.accept("✅ Claude finished (" + lineCount + " lines processed)");
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                log.warn("Claude CLI timed out, killing process tree");
                killProcessTree(process);
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
            // Clean up temp Claude CLI files immediately after process ends
            cleanupTempClaudeFiles(projectPath);
        }

        return response.toString();
    }

    /**
     * Clean up temporary Claude CLI files from a directory.
     * These files have names like tmpclaude-*-cwd or similar patterns.
     * Also cleans up 'nul' files which can be erroneously created on Windows.
     */
    private void cleanupTempClaudeFiles(String projectPath) {
        try {
            Path projectDir = Path.of(projectPath);
            if (!Files.exists(projectDir)) {
                return;
            }

            try (var stream = Files.list(projectDir)) {
                stream.filter(path -> {
                    String fileName = path.getFileName().toString().toLowerCase();
                    // Clean up temp Claude files and 'nul' files (Windows reserved name that shouldn't exist as a file)
                    return (fileName.contains("claude-") && fileName.endsWith("-cwd"))
                            || fileName.equals("nul");
                }).forEach(path -> {
                    try {
                        String fileName = path.getFileName().toString().toLowerCase();
                        if (fileName.equals("nul") && isWindows()) {
                            // On Windows, 'nul' is a reserved name and requires special deletion
                            deleteWindowsReservedFile(path);
                        } else {
                            Files.deleteIfExists(path);
                        }
                        log.debug("Cleaned up temp file: {}", path);
                    } catch (IOException e) {
                        log.warn("Failed to delete temp file: {}", path, e);
                    }
                });
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup temp Claude files: {}", e.getMessage());
        }
    }

    /**
     * Delete a file with a Windows reserved name (like 'nul', 'con', 'aux', etc.).
     * These require using the \\?\ or \\.\ prefix to bypass reserved name handling.
     */
    private void deleteWindowsReservedFile(Path path) throws IOException {
        String absolutePath = path.toAbsolutePath().toString();
        String deleteCommand = "del /f /q \"\\\\.\\" + absolutePath + "\"";
        try {
            Process process = new ProcessBuilder("cmd.exe", "/c", deleteCommand)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Timeout deleting reserved file: {}", path);
            } else if (process.exitValue() != 0) {
                // Try standard deletion as fallback
                Files.deleteIfExists(path);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while deleting reserved file: " + path, e);
        } catch (Exception e) {
            // Try standard deletion as fallback
            Files.deleteIfExists(path);
        }
    }

    /**
     * Request the client to stop the current operation.
     */
    public void requestStop() {
        stopRequested = true;
        Process process = currentProcess;
        if (process != null && process.isAlive()) {
            log.info("Killing Claude CLI process tree");
            killProcessTree(process);
        }
    }

    /**
     * Kill an entire process tree (the process and all its descendants).
     * On Windows, process.destroyForcibly() only kills the parent cmd.exe,
     * leaving child processes (like Claude CLI) orphaned. This method
     * ensures all descendant processes are terminated first.
     */
    private void killProcessTree(Process process) {
        if (process == null) {
            return;
        }

        try {
            ProcessHandle handle = process.toHandle();
            long pid = handle.pid();
            log.debug("Killing process tree for PID: {}", pid);

            // First, recursively destroy all descendant processes (children, grandchildren, etc.)
            handle.descendants().forEach(descendant -> {
                try {
                    log.debug("Killing descendant process PID: {}", descendant.pid());
                    descendant.destroyForcibly();
                } catch (Exception e) {
                    log.warn("Failed to kill descendant process {}: {}", descendant.pid(), e.getMessage());
                }
            });

            // Give descendants a moment to terminate
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Now destroy the main process
            process.destroyForcibly();

            // Wait briefly for the process to terminate
            try {
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Verify termination
            if (process.isAlive()) {
                log.warn("Process {} still alive after destroyForcibly, attempting taskkill", pid);
                // On Windows, use taskkill as a fallback with /T flag to kill tree
                if (isWindows()) {
                    try {
                        new ProcessBuilder("taskkill", "/PID", String.valueOf(pid), "/T", "/F")
                            .redirectErrorStream(true)
                            .start()
                            .waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.warn("Fallback taskkill failed: {}", e.getMessage());
                    }
                }
            }

            log.info("Process tree killed for PID: {}", pid);
        } catch (Exception e) {
            log.error("Error killing process tree", e);
            // Fallback to simple destroyForcibly
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
