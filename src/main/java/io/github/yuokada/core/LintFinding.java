package io.github.yuokada.core;

import io.github.yuokada.core.util.JsonUtil;

/**
 * An individual lint finding produced by evaluating a SQL statement against a built-in rule.
 *
 * <p>Each finding carries a rule identifier, a severity level, and a human-readable message.
 * Findings are derived from the flags already present in {@link QueryAnalysisResult} — no
 * separate analysis pass is required.
 *
 * <p>Current rules:
 * <ul>
 *   <li>{@code W001} — WARNING: {@code SELECT *} detected; prefer explicit column list.</li>
 *   <li>{@code E001} — ERROR: {@code DELETE} without {@code WHERE} will affect all rows.</li>
 * </ul>
 */
public final class LintFinding {

    /**
     * Severity levels for lint findings.
     */
    public enum Severity {

        /** Informational; no immediate action required. */
        INFO,

        /** Potential problem; should be reviewed. */
        WARNING,

        /** Definite problem; should be fixed before production use. */
        ERROR
    }

    /** Rule identifier (e.g. {@code "W001"}). */
    private final String ruleId;

    /** Severity of this finding. */
    private final Severity severity;

    /** Human-readable description of the finding. */
    private final String message;

    /**
     * Constructs a new {@code LintFinding}.
     *
     * @param ruleId   short rule identifier
     * @param severity severity level
     * @param message  human-readable message
     */
    public LintFinding(String ruleId, Severity severity, String message) {
        this.ruleId = ruleId;
        this.severity = severity;
        this.message = message;
    }

    /**
     * Returns the rule identifier.
     *
     * @return rule ID string
     */
    public String getRuleId() {
        return this.ruleId;
    }

    /**
     * Returns the severity level.
     *
     * @return severity
     */
    public Severity getSeverity() {
        return this.severity;
    }

    /**
     * Returns the human-readable message.
     *
     * @return message string
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Serialises this finding to a compact JSON object string.
     *
     * @return JSON representation, e.g. {@code {"ruleId":"W001","severity":"WARNING","message":"…"}}
     */
    public String toJson() {
        return "{"
            + "\"ruleId\":\"" + JsonUtil.escape(this.ruleId) + "\","
            + "\"severity\":\"" + this.severity.name() + "\","
            + "\"message\":\"" + JsonUtil.escape(this.message) + "\""
            + "}";
    }

    @Override
    public String toString() {
        return "[" + this.severity.name() + "] " + this.ruleId + ": " + this.message;
    }
}
