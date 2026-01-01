package io.github.yuokada.core;

import java.util.Collections;
import java.util.LinkedHashSet;
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

    private QueryAnalysisResult(Builder b) {
        this.queryType = b.queryType;
        this.catalogs = Collections.unmodifiableSet(new LinkedHashSet<>(b.catalogs));
        this.tables = Collections.unmodifiableSet(new LinkedHashSet<>(b.tables));
        this.usesSelectStar = b.usesSelectStar;
        this.hasLimit = b.hasLimit;
        this.hasWhereOnDelete = b.hasWhereOnDelete;
        this.parseError = b.parseError;
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
        sb.append('"').append(escape(name)).append('"').append(':');
        sb.append('[');
        boolean first = true;
        for (String v : values) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(escape(v)).append('"');
            first = false;
        }
        sb.append(']').append(',');
    }

    private static void appendField(StringBuilder sb, String name, String value, boolean quote) {
        sb.append('"').append(escape(name)).append('"').append(':');
        if (quote) {
            sb.append('"').append(escape(value)).append('"');
        } else {
            sb.append(value);
        }
        sb.append(',');
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
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
         * @return built result
         */
        public QueryAnalysisResult build() {
            return new QueryAnalysisResult(this);
        }
    }
}
