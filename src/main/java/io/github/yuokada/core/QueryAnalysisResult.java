package io.github.yuokada.core;

import io.github.yuokada.core.util.JsonUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable result of analyzing a single SQL statement.
 */
public final class QueryAnalysisResult {

    /**
     * The detected query type (e.g., Query, Insert, Delete).
     */
    private final String queryType;
    /**
     * Catalog names referenced by the statement.
     */
    private final Set<String> catalogs;
    /**
     * Fully-qualified table names referenced or targeted by the statement.
     */
    private final Set<String> tables;
    /**
     * True when the statement contains SELECT *.
     */
    private final boolean usesSelectStar;
    /**
     * True when LIMIT exists; null if not applicable.
     */
    private final Boolean hasLimit; // null when not applicable
    /**
     * True when DELETE has WHERE; null if not a DELETE.
     */
    private final Boolean hasWhereOnDelete; // null when not DELETE
    /**
     * Parse error message when parsing failed; otherwise null.
     */
    private final String parseError; // optional
    /**
     * CTE names defined in the statement.
     */
    private final Set<String> ctes = new LinkedHashSet<>();
    /**
     * Join descriptors like "INNER:on", "LEFT:on", "CROSS:none".
     */
    private final Set<String> joins = new LinkedHashSet<>();
    /**
     * Scalar (non-aggregate, non-window) function names.
     */
    private final Set<String> functionsScalar = new LinkedHashSet<>();
    /**
     * Aggregate function names (heuristic).
     */
    private final Set<String> functionsAggregate = new LinkedHashSet<>();
    /**
     * Window function names.
     */
    private final Set<String> functionsWindow = new LinkedHashSet<>();
    /**
     * Write target names such as INSERT/CREATE TABLE targets.
     */
    private final Set<String> writeTargets = new LinkedHashSet<>();
    /**
     * Function names that are neither Trino built-ins nor user-declared known functions.
     * Populated only when UDF validation is enabled.
     */
    private final Set<String> unknownFunctions = new LinkedHashSet<>();

    private QueryAnalysisResult(Builder b) {
        this.queryType = b.queryType;
        this.catalogs = Collections.unmodifiableSet(new LinkedHashSet<>(b.catalogs));
        this.tables = Collections.unmodifiableSet(new LinkedHashSet<>(b.tables));
        this.usesSelectStar = b.usesSelectStar;
        this.hasLimit = b.hasLimit;
        this.hasWhereOnDelete = b.hasWhereOnDelete;
        this.parseError = b.parseError;
        this.ctes.addAll(b.ctes);
        this.joins.addAll(b.joins);
        this.functionsScalar.addAll(b.functionsScalar);
        this.functionsAggregate.addAll(b.functionsAggregate);
        this.functionsWindow.addAll(b.functionsWindow);
        this.writeTargets.addAll(b.writeTargets);
        this.unknownFunctions.addAll(b.unknownFunctions);
    }

    /**
     * @return Detected query type.
     */
    public String getQueryType() {
        return queryType;
    }

    /**
     * @return Catalog names.
     */
    public Set<String> getCatalogs() {
        return catalogs;
    }

    /**
     * @return Fully-qualified table names.
     */
    public Set<String> getTables() {
        return tables;
    }

    /**
     * @return Whether SELECT * is used.
     */
    public boolean isUsesSelectStar() {
        return usesSelectStar;
    }

    /**
     * @return Whether LIMIT exists (nullable).
     */
    public Boolean getHasLimit() {
        return hasLimit;
    }

    /**
     * @return Whether DELETE has WHERE (nullable).
     */
    public Boolean getHasWhereOnDelete() {
        return hasWhereOnDelete;
    }

    /**
     * @return Parse error message or null.
     */
    public String getParseError() {
        return parseError;
    }

    /**
     * @return CTE names.
     */
    public Set<String> getCtes() {
        return Collections.unmodifiableSet(ctes);
    }

    /**
     * @return Join descriptors.
     */
    public Set<String> getJoins() {
        return Collections.unmodifiableSet(joins);
    }

    /**
     * @return Scalar function names.
     */
    public Set<String> getFunctionsScalar() {
        return Collections.unmodifiableSet(functionsScalar);
    }

    /**
     * @return Aggregate function names.
     */
    public Set<String> getFunctionsAggregate() {
        return Collections.unmodifiableSet(functionsAggregate);
    }

    /**
     * @return Window function names.
     */
    public Set<String> getFunctionsWindow() {
        return Collections.unmodifiableSet(functionsWindow);
    }

    /**
     * @return Write target names.
     */
    public Set<String> getWriteTargets() {
        return Collections.unmodifiableSet(writeTargets);
    }

    /**
     * Returns function names that could not be matched to any Trino built-in or
     * user-declared known function. Non-empty only when UDF validation is enabled
     * (i.e., {@code --validate-functions} was passed to the analyze subcommand).
     *
     * @return unmodifiable set of unknown function names (lowercase)
     */
    public Set<String> getUnknownFunctions() {
        return Collections.unmodifiableSet(unknownFunctions);
    }

    /**
     * Returns lint findings derived from this analysis result.
     *
     * <p>Current rules:
     * <ul>
     *   <li>{@code W001} — WARNING: {@code SELECT *} detected; prefer explicit column list.</li>
     *   <li>{@code W002} — WARNING: unknown function detected; may be a custom UDF or typo.</li>
     *   <li>{@code E001} — ERROR: {@code DELETE} without {@code WHERE} will affect all rows.</li>
     * </ul>
     *
     * @return unmodifiable list of lint findings (may be empty)
     */
    public List<LintFinding> getFindings() {
        List<LintFinding> findings = new ArrayList<>();
        if (this.usesSelectStar) {
            findings.add(new LintFinding("W001", LintFinding.Severity.WARNING,
                "SELECT * detected; prefer explicit column list"));
        }
        for (String fn : this.unknownFunctions) {
            findings.add(new LintFinding("W002", LintFinding.Severity.WARNING,
                "Unknown function: " + fn + "; may be a custom UDF or typo"));
        }
        if (Boolean.FALSE.equals(this.hasWhereOnDelete)) {
            findings.add(new LintFinding("E001", LintFinding.Severity.ERROR,
                "DELETE without WHERE clause will affect all rows"));
        }
        return Collections.unmodifiableList(findings);
    }

    /**
     * Builds a compact JSON string representing this result.
     *
     * @return JSON representation
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendField(sb, "queryType", queryType, true);
        appendArray(sb, "catalogs", catalogs);
        appendArray(sb, "tables", tables);
        appendField(sb, "usesSelectStar", Boolean.toString(usesSelectStar), false);
        appendArray(sb, "ctes", ctes);
        appendArray(sb, "joins", joins);
        appendArray(sb, "functionsScalar", functionsScalar);
        appendArray(sb, "functionsAggregate", functionsAggregate);
        appendArray(sb, "functionsWindow", functionsWindow);
        appendArray(sb, "writeTargets", writeTargets);
        appendArray(sb, "unknownFunctions", unknownFunctions);
        appendFindingsArray(sb, getFindings());
        if (hasLimit != null) {
            appendField(sb, "hasLimit", Boolean.toString(hasLimit), false);
        }
        if (hasWhereOnDelete != null) {
            appendField(sb, "hasWhereOnDelete", Boolean.toString(hasWhereOnDelete), false);
        }
        if (parseError != null) {
            appendField(sb, "parseError", parseError, true);
        }
        // Remove trailing comma if present
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendArray(StringBuilder sb, String name, Set<String> values) {
        sb.append('"').append(JsonUtil.escape(name)).append('"').append(':');
        sb.append('[');
        boolean first = true;
        java.util.List<String> sorted = new java.util.ArrayList<>(values);
        java.util.Collections.sort(sorted);
        for (String v : sorted) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(JsonUtil.escape(v)).append('"');
            first = false;
        }
        sb.append(']').append(',');
    }

    private static void appendField(StringBuilder sb, String name, String value, boolean quote) {
        sb.append('"').append(JsonUtil.escape(name)).append('"').append(':');
        if (quote) {
            sb.append('"').append(JsonUtil.escape(value)).append('"');
        } else {
            sb.append(value);
        }
        sb.append(',');
    }

    private static void appendFindingsArray(StringBuilder sb, List<LintFinding> findings) {
        sb.append("\"findings\":[");
        boolean first = true;
        for (LintFinding f : findings) {
            if (!first) {
                sb.append(',');
            }
            sb.append(f.toJson());
            first = false;
        }
        sb.append(']').append(',');
    }

    /**
     * Builder for {@link QueryAnalysisResult}.
     */
    public static final class Builder {

        /**
         * Query type value.
         */
        private String queryType;
        /**
         * Collected catalog names.
         */
        private final Set<String> catalogs = new LinkedHashSet<>();
        /**
         * Collected table names.
         */
        private final Set<String> tables = new LinkedHashSet<>();
        /**
         * SELECT * flag.
         */
        private boolean usesSelectStar;
        /**
         * LIMIT flag (nullable).
         */
        private Boolean hasLimit;
        /**
         * DELETE has WHERE flag (nullable).
         */
        private Boolean hasWhereOnDelete;
        /**
         * Parse error message (nullable).
         */
        private String parseError;
        /**
         * CTE names.
         */
        private final Set<String> ctes = new LinkedHashSet<>();
        /**
         * Join descriptors.
         */
        private final Set<String> joins = new LinkedHashSet<>();
        /**
         * Scalar function names.
         */
        private final Set<String> functionsScalar = new LinkedHashSet<>();
        /**
         * Aggregate function names.
         */
        private final Set<String> functionsAggregate = new LinkedHashSet<>();
        /**
         * Window function names.
         */
        private final Set<String> functionsWindow = new LinkedHashSet<>();
        /**
         * Write targets.
         */
        private final Set<String> writeTargets = new LinkedHashSet<>();
        /**
         * Unknown function names (not in Trino built-ins or user-declared known functions).
         */
        private final Set<String> unknownFunctions = new LinkedHashSet<>();

        /**
         * @param v query type value
         * @return Builder instance
         */
        public Builder queryType(String v) {
            this.queryType = v;
            return this;
        }

        /**
         * @param v catalog to add
         * @return Builder instance
         */
        public Builder addCatalog(String v) {
            if (Objects.nonNull(v)) {
                catalogs.add(v);
            }
            return this;
        }

        /**
         * @param v catalogs to add
         * @return Builder instance
         */
        public Builder addAllCatalogs(Set<String> v) {
            if (v != null) {
                catalogs.addAll(v);
            }
            return this;
        }

        /**
         * @param v table to add
         * @return Builder instance
         */
        public Builder addTable(String v) {
            if (Objects.nonNull(v)) {
                tables.add(v);
            }
            return this;
        }

        /**
         * @param v tables to add
         * @return Builder instance
         */
        public Builder addAllTables(Set<String> v) {
            if (v != null) {
                tables.addAll(v);
            }
            return this;
        }

        /**
         * @param v uses select star
         * @return Builder instance
         */
        public Builder usesSelectStar(boolean v) {
            this.usesSelectStar = v;
            return this;
        }

        /**
         * @param v has limit
         * @return Builder instance
         */
        public Builder hasLimit(Boolean v) {
            this.hasLimit = v;
            return this;
        }

        /**
         * @param v has where on delete
         * @return Builder instance
         */
        public Builder hasWhereOnDelete(Boolean v) {
            this.hasWhereOnDelete = v;
            return this;
        }

        /**
         * @param v parse error
         * @return Builder instance
         */
        public Builder parseError(String v) {
            this.parseError = v;
            return this;
        }

        /**
         * @param v CTE name to add
         * @return Builder instance
         */
        public Builder addCte(String v) {
            if (Objects.nonNull(v)) {
                ctes.add(v);
            }
            return this;
        }

        /**
         * @param v Join descriptor to add
         * @return Builder instance
         */
        public Builder addJoin(String v) {
            if (Objects.nonNull(v)) {
                joins.add(v);
            }
            return this;
        }

        /**
         * @param v Scalar function name to add
         * @return Builder instance
         */
        public Builder addFunctionScalar(String v) {
            if (Objects.nonNull(v)) {
                functionsScalar.add(v);
            }
            return this;
        }

        /**
         * @param v Aggregate function name to add
         * @return Builder instance
         */
        public Builder addFunctionAggregate(String v) {
            if (Objects.nonNull(v)) {
                functionsAggregate.add(v);
            }
            return this;
        }

        /**
         * @param v Window function name to add
         * @return Builder instance
         */
        public Builder addFunctionWindow(String v) {
            if (Objects.nonNull(v)) {
                functionsWindow.add(v);
            }
            return this;
        }

        /**
         * @param v Write target name to add
         * @return Builder instance
         */
        public Builder addWriteTarget(String v) {
            if (Objects.nonNull(v)) {
                writeTargets.add(v);
            }
            return this;
        }

        /**
         * @param v unknown function name to add (lowercase)
         * @return Builder instance
         */
        public Builder addUnknownFunction(String v) {
            if (Objects.nonNull(v)) {
                unknownFunctions.add(v);
            }
            return this;
        }

        /**
         * @return built result
         */
        public QueryAnalysisResult build() {
            return new QueryAnalysisResult(this);
        }
    }
}
