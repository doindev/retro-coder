package org.me.retrocoder.cli;

import java.io.Console;
import java.util.Scanner;

/**
 * Console/terminal utility methods.
 */
public class ConsoleUtils {

    private static final Console console = System.console();
    private static final Scanner scanner = new Scanner(System.in);

    /**
     * Print a line to console.
     */
    public static void println(String message) {
        System.out.println(message);
    }

    /**
     * Print without newline.
     */
    public static void print(String message) {
        System.out.print(message);
    }

    /**
     * Print a formatted line.
     */
    public static void printf(String format, Object... args) {
        System.out.printf(format, args);
    }

    /**
     * Read a line from console.
     */
    public static String readLine(String prompt) {
        print(prompt);
        return scanner.nextLine();
    }

    /**
     * Read a password (hidden input if possible).
     */
    public static String readPassword(String prompt) {
        if (console != null) {
            char[] password = console.readPassword(prompt);
            return new String(password);
        }
        return readLine(prompt);
    }

    /**
     * Clear the screen (works on most terminals).
     */
    public static void clearScreen() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            // Fallback: print blank lines
            for (int i = 0; i < 50; i++) {
                println("");
            }
        }
    }

    /**
     * Print a horizontal line.
     */
    public static void printLine() {
        println("================================================================================");
    }

    /**
     * Print a header.
     */
    public static void printHeader(String title) {
        printLine();
        println("  " + title);
        printLine();
    }

    /**
     * Print an error message.
     */
    public static void printError(String message) {
        println("ERROR: " + message);
    }

    /**
     * Print a success message.
     */
    public static void printSuccess(String message) {
        println("SUCCESS: " + message);
    }

    /**
     * Print a warning message.
     */
    public static void printWarning(String message) {
        println("WARNING: " + message);
    }

    /**
     * Confirm a yes/no question.
     */
    public static boolean confirm(String question) {
        String answer = readLine(question + " (y/n): ");
        return answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes");
    }

    /**
     * Read an integer with validation.
     */
    public static int readInt(String prompt, int min, int max) {
        while (true) {
            try {
                String input = readLine(prompt);
                int value = Integer.parseInt(input.trim());
                if (value >= min && value <= max) {
                    return value;
                }
                printf("Please enter a number between %d and %d%n", min, max);
            } catch (NumberFormatException e) {
                println("Invalid number. Please try again.");
            }
        }
    }
}
