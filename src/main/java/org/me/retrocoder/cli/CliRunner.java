package org.me.retrocoder.cli;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.Application;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * CLI entry point.
 * Runs when application is started with --cli flag.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CliRunner implements CommandLineRunner {

    private final InteractiveMenu interactiveMenu;

    @Override
    public void run(String... args) throws Exception {
        if (!Application.isCliMode()) {
            // Not in CLI mode, skip
            return;
        }

        log.info("Starting CLI mode");

        try {
            interactiveMenu.run();
        } catch (Exception e) {
            log.error("CLI error", e);
            ConsoleUtils.printError("An error occurred: " + e.getMessage());
        }

        // Exit after CLI mode
        System.exit(0);
    }
}
