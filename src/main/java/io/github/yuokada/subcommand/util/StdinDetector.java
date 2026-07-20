package io.github.yuokada.subcommand.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility for checking stdin availability without blocking command execution.
 */
public final class StdinDetector {

    private static final long CHECK_TIMEOUT_MILLIS = 50L;

    private StdinDetector() {
    }

    /**
     * Returns true when stdin appears to have buffered data.
     *
     * @return true when data is available on stdin
     */
    public static boolean hasData() {
        InputStream in = System.in;
        if (in == null) {
            return false;
        }
        // available() can block in some environments (e.g. Maven Surefire where System.in
        // is socket-backed). Run the check in a daemon thread with a short timeout to
        // keep this non-blocking in all contexts.
        AtomicBoolean hasData = new AtomicBoolean(false);
        Thread checker = new Thread(() -> {
            try {
                hasData.set(in.available() > 0);
            } catch (IOException ignored) {
                // leave as false
            }
        }, "stdin-checker");
        checker.setDaemon(true);
        checker.start();
        try {
            checker.join(CHECK_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return hasData.get();
    }
}
