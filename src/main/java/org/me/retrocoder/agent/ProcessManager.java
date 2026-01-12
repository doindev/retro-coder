package org.me.retrocoder.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
     * Stop a process.
     */
    public void stopProcess(String name) {
        Process process = processes.remove(name);
        if (process != null && process.isAlive()) {
            process.destroy();
            log.info("Stopped process: {}", name);
        }
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
