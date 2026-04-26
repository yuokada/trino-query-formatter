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
     * Supplemental rule guidance bundled with a finding.
     */
    private record Guidance(String hint, String fix) {
    }

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

    /** Short explanation of why the rule matters. */
    private final String hint;

    /** Concrete suggestion for remediating the finding. */
    private final String fix;

    /**
     * Constructs a new {@code LintFinding}.
     *
     * @param ruleId   short rule identifier
     * @param severity severity level
     * @param message  human-readable message
     */
    public LintFinding(String ruleId, Severity severity, String message) {
        Guidance guidance = guidanceFor(ruleId);
        this.ruleId = ruleId;
        this.severity = severity;
        this.message = message;
        this.hint = guidance.hint();
        this.fix = guidance.fix();
    }

    /**
     * Constructs a new {@code LintFinding} with explicit help content.
     *
     * @param ruleId   short rule identifier
     * @param severity severity level
     * @param message  human-readable message
     * @param hint     short explanation of why the rule matters
     * @param fix      actionable suggestion
     */
    public LintFinding(String ruleId, Severity severity, String message, String hint, String fix) {
        this.ruleId = ruleId;
        this.severity = severity;
        this.message = message;
        this.hint = hint;
        this.fix = fix;
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
     * Returns the short explanation of why the rule matters.
     *
     * @return hint string
     */
    public String getHint() {
        return this.hint;
    }

    /**
     * Returns the actionable suggestion for fixing the issue.
     *
     * @return fix string
     */
    public String getFix() {
        return this.fix;
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
            + "\"message\":\"" + JsonUtil.escape(this.message) + "\","
            + "\"hint\":\"" + JsonUtil.escape(this.hint) + "\","
            + "\"fix\":\"" + JsonUtil.escape(this.fix) + "\""
            + "}";
    }

    @Override
    public String toString() {
        return "[" + this.severity.name() + "] " + this.ruleId + ": " + this.message;
    }

    private static Guidance guidanceFor(String ruleId) {
        return switch (ruleId) {
            case "W001" -> new Guidance(
                "SELECT * couples queries to the table schema; adding a column can break consumers.",
                "List only the columns you need instead of using SELECT *.");
            case "W002" -> new Guidance(
                "The function may be a typo or an unregistered UDF.",
                "Check the spelling or register the function via --udf-catalog.");
            case "W003" -> new Guidance(
                "Calling a function with the wrong number of arguments fails at runtime.",
                "Update the call to match the declared function arity.");
            case "W004" -> new Guidance(
                "The Trino server reported a non-fatal issue that may indicate a problem.",
                "Review the server message and verify that the query intent is correct.");
            case "W005" -> new Guidance(
                "Positional references break when selected columns are reordered.",
                "Replace ORDER BY 1 style references with explicit column names.");
            case "W006" -> new Guidance(
                "Without ORDER BY, repeated executions may return rows in different order.",
                "Add ORDER BY before LIMIT, or document that the order is intentionally non-deterministic.");
            case "W007" -> new Guidance(
                "Unqualified names resolve against the session default catalog, not explicit catalogs in the query.",
                "Fully qualify table references as catalog.schema.table.");
            case "E001" -> new Guidance(
                "Omitting WHERE on DELETE removes every row permanently.",
                "Add a WHERE clause; WHERE 1=0 is useful as a dry-run safety check.");
            case "E002", "E003", "E004", "E005" -> new Guidance(
                "The remote Trino server reported a hard validation error.",
                "Review the server message to identify the missing or incompatible object.");
            default -> new Guidance("See the message text for details.", "Review the statement and retry.");
        };
    }
}
