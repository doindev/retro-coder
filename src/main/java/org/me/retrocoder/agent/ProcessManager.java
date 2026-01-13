package org.me.retrocoder.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Manager for external processes.
 */
@Slf4j
@Component
public class ProcessManager {

    private final Map<String, Process> processes = new ConcurrentHashMap<>();

    /**
     * Start a process and return its PID.
     */
    public long startProcess(String name, ProcessBuilder builder, Consumer<String> outputHandler) {
        try {
            Process process = builder.start();
            processes.put(name, process);

            // Start output reader thread
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (outputHandler != null) {
                            outputHandler.accept(line);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error reading process output", e);
                }
            });
            outputThread.setDaemon(true);
            outputThread.start();

            return process.pid();
        } catch (Exception e) {
            log.error("Failed to start process: {}", name, e);
            throw new RuntimeException("Failed to start process", e);
        }
    }

    /**
     * Stop a process and all its descendants.
     */
    public void stopProcess(String name) {
        Process process = processes.remove(name);
        if (process != null && process.isAlive()) {
            killProcessTree(process);
            log.info("Stopped process tree: {}", name);
        }
    }

    /**
     * Kill an entire process tree (the process and all its descendants).
     * On Windows, process.destroy() only kills the parent process,
     * leaving child processes orphaned. This method ensures all
     * descendant processes are terminated first.
     */
    private void killProcessTree(Process process) {
        if (process == null) {
            return;
        }

        try {
            ProcessHandle handle = process.toHandle();
            long pid = handle.pid();
            log.debug("Killing process tree for PID: {}", pid);

            // First, recursively destroy all descendant processes
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

            // Verify termination - use taskkill as fallback on Windows
            if (process.isAlive() && isWindows()) {
                log.warn("Process {} still alive after destroyForcibly, attempting taskkill", pid);
                try {
                    new ProcessBuilder("taskkill", "/PID", String.valueOf(pid), "/T", "/F")
                        .redirectErrorStream(true)
                        .start()
                        .waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("Fallback taskkill failed: {}", e.getMessage());
                }
            }

            log.debug("Process tree killed for PID: {}", pid);
        } catch (Exception e) {
            log.error("Error killing process tree", e);
            // Fallback to simple destroyForcibly
            process.destroyForcibly();
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Check if a process is running.
     */
    public boolean isRunning(String name) {
        Process process = processes.get(name);
        return process != null && process.isAlive();
    }

    /**
     * Get process PID.
     */
    public Long getPid(String name) {
        Process process = processes.get(name);
        return process != null ? process.pid() : null;
    }

    /**
     * Stop all processes.
     */
    public void stopAll() {
        processes.keySet().forEach(this::stopProcess);
    }

    /**
     * Wait for process to finish.
     */
    public int waitFor(String name) throws InterruptedException {
        Process process = processes.get(name);
        if (process != null) {
            return process.waitFor();
        }
        return -1;
    }
}
