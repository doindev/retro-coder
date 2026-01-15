package org.me.retrocoder.agent;

/**
 * Exception thrown when a rate limit or token limit is detected.
 */
public class RateLimitException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
