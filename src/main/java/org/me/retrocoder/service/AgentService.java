package org.me.retrocoder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.agent.ClaudeClient;
import org.me.retrocoder.agent.ClaudeClientFactory;
import org.me.retrocoder.agent.LangChain4jClientFactory;
import org.me.retrocoder.agent.RateLimitException;
import org.me.retrocoder.model.AgentPhase;
import org.me.retrocoder.model.AgentRole;
import org.me.retrocoder.model.AgentStatus;
import org.me.retrocoder.model.AiAgentConfig;
import org.me.retrocoder.model.dto.AgentStatusDTO;
import org.me.retrocoder.websocket.WebSocketSessionManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing agent processes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final RegistryService registryService;
    private final WebSocketSessionManager webSocketSessionManager;
    private final ClaudeClientFactory clientFactory;
    private final LangChain4jClientFactory langChain4jClientFactory;
    private final AiAgentConfigService aiAgentConfigService;
    private final PromptService promptService;
    private final FeatureService featureService;

    private final Map<String, Thread> runningAgents = new ConcurrentHashMap<>();
    private final Map<String, AgentStatus> agentStatuses = new ConcurrentHashMap<>();
    private final Map<String, Boolean> stopRequested = new ConcurrentHashMap<>();
    private final Map<String, ClaudeClient> runningClients = new ConcurrentHashMap<>();

    private static final String LOCK_FILE = ".agent.lock";
    private static final long RATE_LIMIT_PAUSE_MS = 60000; // 1 minute pause on rate limit
    private static final int MAX_RATE_LIMIT_RETRIES = 3;
    private static final long SESSION_DELAY_MS = 3000; // 3 second delay between sessions
    private static final int MAX_BUILD_VALIDATION_CYCLES = 10; // Prevent infinite build-fix loops

    /**
     * Get agent status for a project.
     */
    public AgentStatusDTO getStatus(String projectName) {
        AgentStatus status = agentStatuses.getOrDefault(projectName, AgentStatus.STOPPED);
        Thread agentThread = runningAgents.get(projectName);

        if (agentThread != null && agentThread.isAlive()) {
            return AgentStatusDTO.builder()
                .status(status)
                .pid(ProcessHandle.current().pid())
                .build();
        } else if (agentThread != null) {
            // Thread died
            runningAgents.remove(projectName);
            agentStatuses.put(projectName, AgentStatus.CRASHED);
            return AgentStatusDTO.crashed("Agent thread terminated unexpectedly");
        }

        // Check for orphaned lock file
        String projectPath = registryService.getProjectPath(projectName).orElse(null);
        if (projectPath != null && hasLockFile(projectPath)) {
            return AgentStatusDTO.crashed("Found orphaned lock file - agent may have crashed");
        }

        return AgentStatusDTO.stopped();
    }

    /**
     * Start agent for a project.
     */
    public void startAgent(String projectName, boolean yoloMode, String model) {
        String projectPath = registryService.getProjectPath(projectName)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectName));

        if (runningAgents.containsKey(projectName)) {
            throw new IllegalStateException("Agent already running for project: " + projectName);
        }

        try {
            // Create lock file
            createLockFile(projectPath);

            // Reset stop flag
            stopRequested.put(projectName, false);

            // Start agent in a new thread
            Thread agentThread = new Thread(() -> runAgentLoop(projectName, projectPath, yoloMode));
            agentThread.setName("agent-" + projectName);
            agentThread.start();

            runningAgents.put(projectName, agentThread);
            agentStatuses.put(projectName, AgentStatus.RUNNING);
            webSocketSessionManager.broadcastAgentStatus(projectName, AgentStatus.RUNNING);

            log.info("Agent started for project: {} (yolo={}, model={})", projectName, yoloMode, model);

        } catch (Exception e) {
            log.error("Failed to start agent for project: {}", projectName, e);
            agentStatuses.put(projectName, AgentStatus.CRASHED);
            removeLockFile(projectPath);
            throw new RuntimeException("Failed to start agent", e);
        }
    }

    /**
     * Main agent loop that runs through phases: INITIALIZING -> CODING -> BUILD_VALIDATING -> (repeat if errors) -> COMPLETE.
     * Continues running sessions until build is successful with no errors or stopped by user.
     */
    private void runAgentLoop(String projectName, String projectPath, boolean yoloMode) {
        ClaudeClient client = null;
        int rateLimitRetries = 0;
        int sessionNumber = 0;
        int buildValidationCycles = 0;
        AgentPhase currentPhase = AgentPhase.INITIALIZING;

        try {
            // Get the AI agent configuration for the coding role
            AiAgentConfig agentConfig = aiAgentConfigService.getAgentForRole(AgentRole.CODING, projectName);
            if (agentConfig != null) {
                log.info("Using AI agent '{}' ({}) for project: {}",
                    agentConfig.getName(), agentConfig.getProviderType(), projectName);
                client = langChain4jClientFactory.createClaudeClient(agentConfig);
            } else {
                log.info("No AI agent configured, using default Claude CLI for project: {}", projectName);
                client = clientFactory.createDefaultClient();
            }
            runningClients.put(projectName, client);

            // Main loop - continue until complete or stopped
            while (!Boolean.TRUE.equals(stopRequested.get(projectName)) && currentPhase != AgentPhase.COMPLETE) {
                sessionNumber++;

                // Determine current phase based on feature state
                var stats = featureService.getStats(projectName);
                int totalFeatures = stats.getTotal();
                int passingFeatures = stats.getPassing();

                // Phase determination logic
                if (totalFeatures == 0) {
                    // No features yet - we're in initializer phase
                    currentPhase = AgentPhase.INITIALIZING;
                } else if (passingFeatures < totalFeatures) {
                    // Features exist but not all complete - coding phase
                    currentPhase = AgentPhase.CODING;
                } else {
                    // All features complete - switch to build validation
                    currentPhase = AgentPhase.BUILD_VALIDATING;
                }

                // Handle BUILD_VALIDATING phase
                if (currentPhase == AgentPhase.BUILD_VALIDATING) {
                    buildValidationCycles++;

                    // Check if we've exceeded max cycles
                    if (buildValidationCycles > MAX_BUILD_VALIDATION_CYCLES) {
                        log.warn("Max build validation cycles ({}) exceeded for project: {}",
                            MAX_BUILD_VALIDATION_CYCLES, projectName);
                        webSocketSessionManager.broadcastLog(projectName,
                            String.format("\n*** WARNING: Max build validation cycles (%d) exceeded. Stopping. ***",
                                MAX_BUILD_VALIDATION_CYCLES));
                        currentPhase = AgentPhase.COMPLETE;
                        break;
                    }

                    log.info("Build validation cycle {} for project: {}", buildValidationCycles, projectName);
                    webSocketSessionManager.broadcastLog(projectName,
                        String.format("\n*** BUILD VALIDATION CYCLE %d - Checking for compilation errors ***",
                            buildValidationCycles));

                    // Get count of pending bugfix features before running validator
                    int bugfixCountBefore = featureService.getPendingBugfixCount(projectName);

                    // Run build validator agent
                    String buildValidatorPrompt;
                    try {
                        buildValidatorPrompt = promptService.getBuildValidatorPrompt(projectPath);
                        buildValidatorPrompt = adaptPromptForJava(buildValidatorPrompt, projectName);
                    } catch (Exception e) {
                        // Fallback to loading from classpath if not in project prompts
                        log.warn("Build validator prompt not found in project, using template");
                        buildValidatorPrompt = promptService.loadPrompt("build_validator_prompt", projectPath);
                        buildValidatorPrompt = adaptPromptForJava(buildValidatorPrompt, projectName);
                    }

                    // Clean up temp files before session
                    cleanupTempClaudeFiles(projectPath);

                    try {
                        final ClaudeClient finalClient = client;
                        @SuppressWarnings("unused")
                        String response = finalClient.sendPrompt(buildValidatorPrompt, projectPath, chunk -> {
                            webSocketSessionManager.broadcastLog(projectName, chunk);
                            if (Boolean.TRUE.equals(stopRequested.get(projectName))) {
                                throw new RuntimeException("Agent stopped by user");
                            }
                        });

                        // Clean up temp files after session
                        cleanupTempClaudeFiles(projectPath);

                        // Check if new bugfix features were created
                        int bugfixCountAfter = featureService.getPendingBugfixCount(projectName);
                        int newBugfixes = bugfixCountAfter - bugfixCountBefore;

                        if (newBugfixes > 0) {
                            log.info("Build validator created {} bugfix features for project: {}",
                                newBugfixes, projectName);
                            webSocketSessionManager.broadcastLog(projectName,
                                String.format("\n*** BUILD VALIDATION FOUND %d ERRORS - Switching to CODING phase to fix them ***",
                                    newBugfixes));
                            // Features are now incomplete, loop will switch to CODING phase
                        } else {
                            // No new bugfixes - build is clean!
                            log.info("Build validation successful - no errors found for project: {}", projectName);
                            webSocketSessionManager.broadcastLog(projectName,
                                "\n*** BUILD SUCCESSFUL - No compilation errors! Project complete! ***");
                            currentPhase = AgentPhase.COMPLETE;
                        }

                        rateLimitRetries = 0;

                    } catch (RateLimitException e) {
                        rateLimitRetries = handleRateLimit(projectName, rateLimitRetries);
                        if (rateLimitRetries < 0) throw e; // Max retries exceeded
                        continue; // Retry the session
                    }

                    // Small delay before next session
                    if (!Boolean.TRUE.equals(stopRequested.get(projectName)) && currentPhase != AgentPhase.COMPLETE) {
                        Thread.sleep(SESSION_DELAY_MS);
                    }
                    continue;
                }

                // Handle INITIALIZING and CODING phases
                log.info("Agent session {} for {}: {} (features={}, passing={})",
                    sessionNumber, projectName,
                    currentPhase == AgentPhase.INITIALIZING ? "INITIALIZER" : "CODING",
                    totalFeatures, passingFeatures);

                // Load appropriate prompt
                String promptName = currentPhase == AgentPhase.INITIALIZING ? "initializer_prompt.md"
                    : (yoloMode ? "coding_prompt_yolo.md" : "coding_prompt.md");

                String prompt;
                try {
                    prompt = promptService.getPromptContent(projectPath, promptName);
                    prompt = adaptPromptForJava(prompt, projectName);
                } catch (Exception e) {
                    log.error("Failed to load prompt: {}", promptName, e);
                    webSocketSessionManager.broadcastLog(projectName, "ERROR: Failed to load prompt: " + promptName);
                    throw e;
                }

                webSocketSessionManager.broadcastLog(projectName,
                    String.format("\n--- Starting %s session #%d (features: %d/%d complete) ---",
                        currentPhase == AgentPhase.INITIALIZING ? "initializer" : "coding",
                        sessionNumber, passingFeatures, totalFeatures));

                // Clean up temp files before session
                cleanupTempClaudeFiles(projectPath);

                try {
                    final ClaudeClient finalClient = client;
                    @SuppressWarnings("unused")
                    String response = finalClient.sendPrompt(prompt, projectPath, chunk -> {
                        webSocketSessionManager.broadcastLog(projectName, chunk);
                        if (Boolean.TRUE.equals(stopRequested.get(projectName))) {
                            throw new RuntimeException("Agent stopped by user");
                        }
                    });

                    log.info("Agent session {} completed for project: {}", sessionNumber, projectName);
                    webSocketSessionManager.broadcastLog(projectName,
                        "\n--- Session #" + sessionNumber + " complete ---");

                    // Clean up temp files after session
                    cleanupTempClaudeFiles(projectPath);

                    rateLimitRetries = 0;

                    // Small delay before next session
                    if (!Boolean.TRUE.equals(stopRequested.get(projectName))) {
                        Thread.sleep(SESSION_DELAY_MS);
                    }

                } catch (RateLimitException e) {
                    rateLimitRetries = handleRateLimit(projectName, rateLimitRetries);
                    if (rateLimitRetries < 0) throw e; // Max retries exceeded
                    // Continue to retry the session
                }
            }

            // Update status
            agentStatuses.put(projectName, AgentStatus.STOPPED);
            webSocketSessionManager.broadcastAgentStatus(projectName, AgentStatus.STOPPED);

            if (currentPhase == AgentPhase.COMPLETE) {
                webSocketSessionManager.broadcastLog(projectName,
                    "\n========================================");
                webSocketSessionManager.broadcastLog(projectName,
                    "  PROJECT COMPLETE - All features implemented and build successful!");
                webSocketSessionManager.broadcastLog(projectName,
                    "========================================\n");
            }

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("stopped by user")) {
                log.info("Agent stopped by user for project: {}", projectName);
                agentStatuses.put(projectName, AgentStatus.STOPPED);
                webSocketSessionManager.broadcastAgentStatus(projectName, AgentStatus.STOPPED);
            } else {
                log.error("Agent crashed for project: {}", projectName, e);
                webSocketSessionManager.broadcastLog(projectName, "ERROR: " + e.getMessage());
                agentStatuses.put(projectName, AgentStatus.CRASHED);
                webSocketSessionManager.broadcastAgentStatus(projectName, AgentStatus.CRASHED);
            }
        } finally {
            runningClients.remove(projectName);
            if (client != null) {
                client.close();
            }
            runningAgents.remove(projectName);
            removeLockFile(projectPath);
            cleanupTempClaudeFiles(projectPath);
        }
    }

    /**
     * Handle rate limit with retry logic.
     * Returns the new retry count, or -1 if max retries exceeded.
     */
    private int handleRateLimit(String projectName, int currentRetries) throws InterruptedException {
        currentRetries++;
        if (currentRetries >= MAX_RATE_LIMIT_RETRIES) {
            log.error("Max rate limit retries ({}) exceeded for project: {}", MAX_RATE_LIMIT_RETRIES, projectName);
            webSocketSessionManager.broadcastLog(projectName, "ERROR: Rate limit - max retries exceeded. Stopping agent.");
            return -1;
        }

        log.warn("Rate limit hit for project: {}, pausing {} seconds before retry ({}/{})",
            projectName, RATE_LIMIT_PAUSE_MS / 1000, currentRetries, MAX_RATE_LIMIT_RETRIES);
        webSocketSessionManager.broadcastLog(projectName,
            String.format("Rate limit hit. Pausing %d seconds before retry (%d/%d)...",
                RATE_LIMIT_PAUSE_MS / 1000, currentRetries, MAX_RATE_LIMIT_RETRIES));

        Thread.sleep(RATE_LIMIT_PAUSE_MS);

        if (Boolean.TRUE.equals(stopRequested.get(projectName))) {
            throw new RuntimeException("Agent stopped by user");
        }

        webSocketSessionManager.broadcastLog(projectName, "Resuming after rate limit pause...");
        return currentRetries;
    }

    /**
     * Stop agent for a project.
     */
    public void stopAgent(String projectName) {
        // Signal the agent to stop
        stopRequested.put(projectName, true);

        // Close the client to terminate the subprocess immediately
        ClaudeClient client = runningClients.get(projectName);
        if (client != null) {
            log.info("Closing Claude client to terminate subprocess for project: {}", projectName);
            client.close();
        }

        Thread agentThread = runningAgents.get(projectName);
        if (agentThread != null && agentThread.isAlive()) {
            // Wait briefly for graceful shutdown
            try {
                agentThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Force interrupt if still running
            if (agentThread.isAlive()) {
                agentThread.interrupt();
            }

            log.info("Agent stopped for project: {}", projectName);
        }

        runningAgents.remove(projectName);
        runningClients.remove(projectName);
        agentStatuses.put(projectName, AgentStatus.STOPPED);
        webSocketSessionManager.broadcastAgentStatus(projectName, AgentStatus.STOPPED);

        // Remove lock file and cleanup temp files
        registryService.getProjectPath(projectName).ifPresent(path -> {
            removeLockFile(path);
            cleanupTempClaudeFiles(path);
        });
    }

    /**
     * Pause agent for a project.
     * Note: Pause/resume is not fully implemented for CLI wrapper mode.
     * The agent will complete its current operation before checking pause status.
     */
    public void pauseAgent(String projectName) {
        Thread agentThread = runningAgents.get(projectName);
        if (agentThread == null || !agentThread.isAlive()) {
            throw new IllegalStateException("No agent running for project: " + projectName);
        }

        agentStatuses.put(projectName, AgentStatus.PAUSED);
        webSocketSessionManager.broadcastAgentStatus(projectName, AgentStatus.PAUSED);
        log.info("Agent paused for project: {}", projectName);
    }

    /**
     * Resume agent for a project.
     */
    public void resumeAgent(String projectName) {
        Thread agentThread = runningAgents.get(projectName);
        if (agentThread == null || !agentThread.isAlive()) {
            throw new IllegalStateException("No agent running for project: " + projectName);
        }

        agentStatuses.put(projectName, AgentStatus.RUNNING);
        webSocketSessionManager.broadcastAgentStatus(projectName, AgentStatus.RUNNING);
        log.info("Agent resumed for project: {}", projectName);
    }

    /**
     * Clean up orphaned lock files and temporary Claude CLI files on startup.
     */
    public void cleanupOrphanedLocks() {
        registryService.listProjects().forEach(project -> {
            String projectPath = project.getPath();

            // Clean up orphaned lock files
            if (hasLockFile(projectPath) && !runningAgents.containsKey(project.getName())) {
                removeLockFile(projectPath);
                log.info("Cleaned up orphaned lock file for project: {}", project.getName());
            }

            // Always clean up any leftover temp Claude CLI files
            cleanupTempClaudeFiles(projectPath);
        });
    }

    /**
     * Stop all running agents on shutdown.
     */
    public void stopAllAgents() {
        runningAgents.keySet().forEach(this::stopAgent);
    }

    /**
     * Adapt the prompt for Java environment by replacing MCP tool references with curl API calls.
     */
    private String adaptPromptForJava(String prompt, String projectName) {
        String apiBase = "http://localhost:8888/api/projects/" + projectName;

        // Add instructions for using REST API instead of MCP tools
        String apiInstructions = """

            ## IMPORTANT: Using REST API for Feature Management

            This environment uses REST API calls instead of MCP tools. Use curl to interact with features:

            **Create features in bulk:**
            ```bash
            curl -X POST %s/features/bulk \\
              -H "Content-Type: application/json" \\
              -d '[{"category": "functional", "name": "Feature name", "description": "Description", "steps": ["Step 1", "Step 2"]}]'
            ```

            **Get feature stats:**
            ```bash
            curl %s/stats
            ```

            **Get next pending feature:**
            ```bash
            curl "%s/features?status=pending&limit=1"
            ```

            **Mark feature as IN PROGRESS before working on it (replace ID):**
            ```bash
            curl -X POST %s/features/[FEATURE_ID]/in-progress
            ```

            **Mark feature as passing when complete (replace ID):**
            ```bash
            curl -X POST %s/features/[FEATURE_ID]/passing
            ```

            **Skip a feature (replace ID):**
            ```bash
            curl -X PATCH %s/features/[FEATURE_ID]/skip
            ```

            **WORKFLOW: For each feature you work on:**
            1. First, mark it as in-progress: `curl -X POST %s/features/[ID]/in-progress`
            2. Implement the feature
            3. Test the feature
            4. Mark it as passing: `curl -X POST %s/features/[ID]/passing`

            """.formatted(apiBase, apiBase, apiBase, apiBase, apiBase, apiBase, apiBase, apiBase);

        // Replace MCP tool references
        prompt = prompt.replace("feature_create_bulk tool", "REST API (curl POST to /features/bulk)");
        prompt = prompt.replace("feature_get_stats tool", "REST API (curl GET /stats)");
        prompt = prompt.replace("feature_get_next tool", "REST API (curl GET /features?status=pending&limit=1)");
        prompt = prompt.replace("feature_mark_passing tool", "REST API (curl POST /features/[ID]/passing)");
        prompt = prompt.replace("feature_skip tool", "REST API (curl PATCH /features/[ID]/skip)");

        // Insert API instructions after the first heading
        int insertPos = prompt.indexOf("\n\n", prompt.indexOf("#"));
        if (insertPos > 0) {
            prompt = prompt.substring(0, insertPos) + apiInstructions + prompt.substring(insertPos);
        } else {
            prompt = apiInstructions + prompt;
        }

        return prompt;
    }

    private boolean hasLockFile(String projectPath) {
        return Files.exists(Paths.get(projectPath, LOCK_FILE));
    }

    private void createLockFile(String projectPath) throws IOException {
        Path lockFile = Paths.get(projectPath, LOCK_FILE);
        Files.writeString(lockFile, String.valueOf(ProcessHandle.current().pid()));
    }

    private void removeLockFile(String projectPath) {
        try {
            Files.deleteIfExists(Paths.get(projectPath, LOCK_FILE));
        } catch (IOException e) {
            log.warn("Failed to remove lock file for project: {}", projectPath, e);
        }
    }

    /**
     * Clean up temporary Claude CLI files (tmpclaude-*-cwd) from a project directory.
     * These files are created by the Claude CLI process and should be removed when the agent stops.
     * Also cleans up 'nul' files which can be erroneously created on Windows.
     */
    private void cleanupTempClaudeFiles(String projectPath) {
        try {
            Path projectDir = Paths.get(projectPath);
            if (!Files.exists(projectDir)) {
                return;
            }

            // Find and delete all temp Claude CLI files in the project directory
            // Matches patterns like: tmpclaude-*-cwd, tomclaude-*-cwd, or any *claude-*-cwd variant
            // Also cleans up 'nul' files (Windows reserved name that shouldn't exist as a file)
            try (var stream = Files.list(projectDir)) {
                stream.filter(path -> {
                    String fileName = path.getFileName().toString().toLowerCase();
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

            // Also check common subdirectories where Claude CLI might create temp files
            cleanupTempClaudeFilesInSubdir(projectDir, "ui");
            cleanupTempClaudeFilesInSubdir(projectDir, "src");

        } catch (IOException e) {
            log.warn("Failed to cleanup temp Claude files for project: {}", projectPath, e);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
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
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
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
     * Clean up temporary Claude CLI files from a subdirectory.
     * Also cleans up 'nul' files which can be erroneously created on Windows.
     */
    private void cleanupTempClaudeFilesInSubdir(Path projectDir, String subdir) {
        Path subdirPath = projectDir.resolve(subdir);
        if (!Files.exists(subdirPath) || !Files.isDirectory(subdirPath)) {
            return;
        }

        try (var stream = Files.list(subdirPath)) {
            stream.filter(path -> {
                String fileName = path.getFileName().toString().toLowerCase();
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
                    log.debug("Cleaned up temp file in {}: {}", subdir, path);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to cleanup temp files in subdirectory: {}", subdirPath, e);
        }
    }
}
