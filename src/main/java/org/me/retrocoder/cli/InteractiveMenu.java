package org.me.retrocoder.cli;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.agent.AgentRunner;
import org.me.retrocoder.model.ClaudeIntegrationMode;
import org.me.retrocoder.model.Project;
import org.me.retrocoder.model.dto.ProjectCreateDTO;
import org.me.retrocoder.service.ProjectService;
import org.me.retrocoder.service.RegistryService;
import org.springframework.stereotype.Component;

import static org.me.retrocoder.cli.ConsoleUtils.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Interactive CLI menu for project management.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InteractiveMenu {

    private final ProjectService projectService;
    private final RegistryService registryService;
    private final AgentRunner agentRunner;

    /**
     * Run the main menu loop.
     */
    public void run() {
        printHeader("AUTOCODER - Autonomous Coding Agent");

        while (true) {
            println("");
            println("What would you like to do?");
            println("");
            println("  1. Create a new project");
            println("  2. Select an existing project");
            println("  3. Run agent for a project");
            println("  4. List all projects");
            println("  5. Delete a project");
            println("  0. Exit");
            println("");

            int choice = readInt("Enter your choice: ", 0, 5);

            switch (choice) {
                case 0 -> {
                    println("Goodbye!");
                    return;
                }
                case 1 -> createProject();
                case 2 -> selectProject();
                case 3 -> runAgentMenu();
                case 4 -> listProjects();
                case 5 -> deleteProject();
            }
        }
    }

    private void createProject() {
        printHeader("Create New Project");

        String name = readLine("Project name: ");
        if (!Project.isValidName(name)) {
            printError("Invalid name. Use only alphanumeric characters, dashes, and underscores (1-50 chars).");
            return;
        }

        if (registryService.projectExists(name)) {
            printError("Project already exists: " + name);
            return;
        }

        String pathStr = readLine("Project path (absolute): ");
        Path path = Paths.get(pathStr);

        if (!Files.exists(path)) {
            if (confirm("Directory does not exist. Create it?")) {
                try {
                    Files.createDirectories(path);
                } catch (Exception e) {
                    printError("Failed to create directory: " + e.getMessage());
                    return;
                }
            } else {
                return;
            }
        }

        println("");
        println("Select Claude integration mode:");
        println("  1. CLI Wrapper (default)");
        println("  2. REST API");
        println("  3. Java SDK");
        int modeChoice = readInt("Choice: ", 1, 3);

        ClaudeIntegrationMode mode = switch (modeChoice) {
            case 2 -> ClaudeIntegrationMode.REST_API;
            case 3 -> ClaudeIntegrationMode.JAVA_SDK;
            default -> ClaudeIntegrationMode.CLI_WRAPPER;
        };

        try {
            ProjectCreateDTO dto = ProjectCreateDTO.builder()
                .name(name)
                .path(path.toAbsolutePath().toString())
                .integrationMode(mode)
                .build();

            projectService.createProject(dto);
            printSuccess("Project created: " + name);
            println("Prompts scaffolded in: " + path.resolve("prompts"));
        } catch (Exception e) {
            printError("Failed to create project: " + e.getMessage());
        }
    }

    private void selectProject() {
        List<Project> projects = registryService.listProjects();

        if (projects.isEmpty()) {
            println("No projects found. Create one first.");
            return;
        }

        printHeader("Select Project");
        for (int i = 0; i < projects.size(); i++) {
            Project p = projects.get(i);
            printf("  %d. %s (%s)%n", i + 1, p.getName(), p.getPath());
        }
        println("  0. Cancel");
        println("");

        int choice = readInt("Select project: ", 0, projects.size());
        if (choice == 0) {
            return;
        }

        Project selected = projects.get(choice - 1);
        println("");
        println("Selected: " + selected.getName());
        println("Path: " + selected.getPath());
        println("Integration: " + selected.getIntegrationMode());

        if (confirm("Run agent for this project?")) {
            runAgent(selected);
        }
    }

    private void runAgentMenu() {
        List<Project> projects = registryService.listProjects();

        if (projects.isEmpty()) {
            println("No projects found. Create one first.");
            return;
        }

        printHeader("Run Agent");
        for (int i = 0; i < projects.size(); i++) {
            Project p = projects.get(i);
            printf("  %d. %s%n", i + 1, p.getName());
        }
        println("  0. Cancel");
        println("");

        int choice = readInt("Select project: ", 0, projects.size());
        if (choice == 0) {
            return;
        }

        runAgent(projects.get(choice - 1));
    }

    private void runAgent(Project project) {
        println("");
        boolean yolo = confirm("Enable YOLO mode (skip testing)?");
        String model = registryService.getDefaultModel();

        println("");
        println("Starting agent for: " + project.getName());
        println("Mode: " + project.getIntegrationMode());
        println("YOLO: " + yolo);
        println("Model: " + model);
        printLine();

        try {
            agentRunner.runAgent(
                project.getName(),
                project.getPath(),
                yolo,
                model
            );
        } catch (Exception e) {
            printError("Agent error: " + e.getMessage());
            log.error("Agent failed", e);
        }
    }

    private void listProjects() {
        List<Project> projects = registryService.listProjects();

        printHeader("All Projects");

        if (projects.isEmpty()) {
            println("No projects found.");
            return;
        }

        for (Project p : projects) {
            println("");
            printf("  Name: %s%n", p.getName());
            printf("  Path: %s%n", p.getPath());
            printf("  Mode: %s%n", p.getIntegrationMode());
            printf("  Created: %s%n", p.getCreatedAt());
        }
    }

    private void deleteProject() {
        List<Project> projects = registryService.listProjects();

        if (projects.isEmpty()) {
            println("No projects found.");
            return;
        }

        printHeader("Delete Project");
        for (int i = 0; i < projects.size(); i++) {
            Project p = projects.get(i);
            printf("  %d. %s (%s)%n", i + 1, p.getName(), p.getPath());
        }
        println("  0. Cancel");
        println("");

        int choice = readInt("Select project to delete: ", 0, projects.size());
        if (choice == 0) {
            return;
        }

        Project selected = projects.get(choice - 1);

        if (!confirm("Are you sure you want to delete " + selected.getName() + "?")) {
            return;
        }

        boolean deleteFiles = confirm("Also delete project files?");

        try {
            projectService.deleteProject(selected.getName(), deleteFiles);
            printSuccess("Project deleted: " + selected.getName());
        } catch (Exception e) {
            printError("Failed to delete project: " + e.getMessage());
        }
    }
}
