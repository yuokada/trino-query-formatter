package io.github.yuokada.config;

/**
 * Exception thrown when project config loading fails.
 */
public class ConfigException extends Exception {

    /**
     * Creates a config exception with a message.
     *
     * @param message error message
     */
    public ConfigException(String message) {
        super(message);
    }

    /**
     * Creates a config exception with a message and cause.
     *
     * @param message error message
     * @param cause   root cause
     */
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
