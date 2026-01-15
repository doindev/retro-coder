package org.me.retrocoder.model;

/**
 * Represents the current phase of the agent execution lifecycle.
 */
public enum AgentPhase {

    /**
     * Initial phase where features are created from app_spec.txt.
     */
    INITIALIZING,

    /**
     * Main coding phase where features are implemented.
     */
    CODING,

    /**
     * Build validation phase where the project is compiled and errors are detected.
     * If errors are found, bugfix features are created and the agent returns to CODING.
     */
    BUILD_VALIDATING,

    /**
     * Terminal phase - all features complete and build is successful.
     */
    COMPLETE
}
