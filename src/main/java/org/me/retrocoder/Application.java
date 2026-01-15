package org.me.retrocoder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.extern.slf4j.Slf4j;

/**
 * Main Spring Boot application for Retrocoder.
 * Supports two modes:
 * - Server mode (default): Runs web server with REST API and WebSocket
 * - CLI mode (--cli flag): Runs interactive command-line interface
 */
@Slf4j
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class Application {

    private static boolean cliMode = false;

    public static void main(String[] args) {
        // Check for CLI mode flag
        for (String arg : args) {
            if ("--cli".equals(arg) || "-c".equals(arg)) {
                cliMode = true;
                break;
            }
        }

        if (cliMode) {
            // CLI mode: disable web server
            System.setProperty("spring.main.web-application-type", "none");
        }

        SpringApplication.run(Application.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (cliMode) {
            log.info("Retro-Coder CLI mode started");
        } else {
            log.info("Retro-Coder server started on http://localhost:8888");
        }
    }

    public static boolean isCliMode() {
        return cliMode;
    }
}
