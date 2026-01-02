package io.github.yuokada.core.util;

import com.fasterxml.jackson.core.io.JsonStringEncoder;

/**
 * Minimal JSON utility focused on string escaping for safe embedding into JSON.
 * Uses Jackson's well-tested JsonStringEncoder for reliable JSON string escaping.
 */
public final class JsonUtil {

    /**
     * Shared singleton instance of JsonStringEncoder for efficient reuse.
     */
    private static final JsonStringEncoder ENCODER = JsonStringEncoder.getInstance();

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private JsonUtil() {
    }

    /**
     * Escapes a Java string for JSON string literal context using Jackson's JsonStringEncoder.
     * Converts control characters and quotes/backslashes to their escaped forms.
     *
     * @param s input string (nullable)
     * @return escaped string (never null)
     */
    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        char[] escaped = ENCODER.quoteAsString(s);
        return String.valueOf(escaped);
    }
}

