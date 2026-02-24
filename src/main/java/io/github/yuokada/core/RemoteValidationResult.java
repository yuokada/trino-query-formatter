package io.github.yuokada.core;

import java.util.Collections;
import java.util.List;

/**
 * Result of executing {@code EXPLAIN (TYPE VALIDATE)} against a remote Trino server.
 *
 * <p>A result is either:
 * <ul>
 *   <li><strong>valid</strong> — the server accepted the query; {@link #getFindings()} is empty.</li>
 *   <li><strong>failed</strong> — the server returned an error; {@link #getFindings()} contains
 *       one or more {@link LintFinding} entries (E002–E005, W004).</li>
 *   <li><strong>skipped</strong> — remote validation was not attempted (e.g. Phase 1 had errors
 *       or the server was unreachable); {@link #isSkipped()} returns {@code true}.</li>
 * </ul>
 */
public final class RemoteValidationResult {

    private final boolean skipped;
    private final List<LintFinding> findings;

    private RemoteValidationResult(boolean skipped, List<LintFinding> findings) {
        this.skipped = skipped;
        this.findings = Collections.unmodifiableList(findings);
    }

    /**
     * Creates a result representing a successful validation ({@code Valid = true}).
     *
     * @return result with no findings
     */
    public static RemoteValidationResult valid() {
        return new RemoteValidationResult(false, Collections.emptyList());
    }

    /**
     * Creates a result representing a validation failure with one or more lint findings.
     *
     * @param findings non-null list of findings derived from the Trino error response
     * @return result with findings
     */
    public static RemoteValidationResult failed(List<LintFinding> findings) {
        return new RemoteValidationResult(false, findings);
    }

    /**
     * Creates a result indicating that remote validation was skipped.
     *
     * <p>This is used when Phase 1 produced ERROR-level findings (no need to contact the server)
     * or when the server was unreachable / returned an internal error.
     *
     * @return skipped result with no findings
     */
    public static RemoteValidationResult skipped() {
        return new RemoteValidationResult(true, Collections.emptyList());
    }

    /**
     * Returns {@code true} when remote validation was not performed.
     *
     * @return {@code true} if skipped
     */
    public boolean isSkipped() {
        return this.skipped;
    }

    /**
     * Returns the lint findings produced by the remote validation.
     * Empty when validation passed or was skipped.
     *
     * @return unmodifiable list of findings (may be empty, never null)
     */
    public List<LintFinding> getFindings() {
        return this.findings;
    }
}
