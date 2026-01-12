package org.me.retrocoder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.model.dto.FeatureStatsDTO;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service for tracking progress and printing summaries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressService {

    private final FeatureService featureService;
    private final WebhookService webhookService;
    private final RegistryService registryService;

    private static final String PROGRESS_CACHE_FILE = ".progress_cache";

    /**
     * Get progress statistics for a project.
     */
    public FeatureStatsDTO getProgress(String projectName) {
        return featureService.getStats(projectName);
    }

    /**
     * Print session header to console.
     */
    public void printSessionHeader(int sessionNumber, boolean isInitializer) {
        String type = isInitializer ? "INITIALIZER" : "CODING";
        log.info("================================================================================");
        log.info("                         SESSION {} ({})", sessionNumber, type);
        log.info("================================================================================");
    }

    /**
     * Print progress summary to console.
     */
    public void printProgressSummary(String projectName) {
        FeatureStatsDTO stats = getProgress(projectName);

        log.info("--------------------------------------------------------------------------------");
        log.info("Progress: {}/{} passing ({:.1f}%), {} in progress",
            stats.getPassing(), stats.getTotal(), stats.getPercentage(), stats.getInProgress());

        if (stats.getPassing() == stats.getTotal() && stats.getTotal() > 0) {
            log.info("ALL FEATURES PASSING!");
        }
        log.info("--------------------------------------------------------------------------------");
    }

    /**
     * Send progress webhook if configured.
     */
    public void sendProgressWebhook(String projectName) {
        FeatureStatsDTO stats = getProgress(projectName);
        String projectPath = registryService.getProjectPath(projectName).orElse(null);

        if (projectPath != null && hasNewProgress(projectPath, stats)) {
            webhookService.sendProgressNotification(projectName, stats);
            updateProgressCache(projectPath, stats);
        }
    }

    /**
     * Check if there's new progress since last check.
     */
    private boolean hasNewProgress(String projectPath, FeatureStatsDTO stats) {
        Path cachePath = Paths.get(projectPath, PROGRESS_CACHE_FILE);

        if (!Files.exists(cachePath)) {
            return true;
        }

        try {
            int cachedPassing = Integer.parseInt(Files.readString(cachePath).trim());
            return stats.getPassing() > cachedPassing;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Update progress cache.
     */
    private void updateProgressCache(String projectPath, FeatureStatsDTO stats) {
        Path cachePath = Paths.get(projectPath, PROGRESS_CACHE_FILE);

        try {
            Files.writeString(cachePath, String.valueOf(stats.getPassing()));
        } catch (IOException e) {
            log.warn("Failed to update progress cache", e);
        }
    }
}
