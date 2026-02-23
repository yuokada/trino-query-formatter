package io.github.yuokada.core;

/**
 * Exit code constants for the CLI.
 */
public final class ExitCodes {

    /** Successful execution. */
    public static final int OK = 0;

    /** Warning: e.g., --check detected a file would be reformatted. */
    public static final int WARNING = 1;

    /** Error: e.g., parse error, file not found. */
    public static final int ERROR = 2;

    /** Unexpected exception. */
    public static final int EXCEPTION = 3;

    private ExitCodes() {}
}
