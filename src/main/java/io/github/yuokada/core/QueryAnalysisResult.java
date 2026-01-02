package io.github.yuokada.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable result of analyzing a single SQL statement.
 */
public final class QueryAnalysisResult {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

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
    private final Set<String> ctes;
    /**
     * Join descriptors like "INNER:on", "LEFT:on", "CROSS:none".
     */
    private final Set<String> joins;
    /**
     * Scalar (non-aggregate, non-window) function names.
     */
    private final Set<String> functionsScalar;
    /**
     * Aggregate function names (heuristic).
     */
    private final Set<String> functionsAggregate;
    /**
     * Window function names.
     */
    private final Set<String> functionsWindow;
    /**
     * Write target names such as INSERT/CREATE TABLE targets.
     */
    private final Set<String> writeTargets;

    private QueryAnalysisResult(Builder b) {
        this.queryType = b.queryType;
        this.catalogs = Collections.unmodifiableSet(new LinkedHashSet<>(b.catalogs));
        this.tables = Collections.unmodifiableSet(new LinkedHashSet<>(b.tables));
        this.usesSelectStar = b.usesSelectStar;
        this.hasLimit = b.hasLimit;
        this.hasWhereOnDelete = b.hasWhereOnDelete;
        this.parseError = b.parseError;
        this.ctes = Collections.unmodifiableSet(new LinkedHashSet<>(b.ctes));
        this.joins = Collections.unmodifiableSet(new LinkedHashSet<>(b.joins));
        this.functionsScalar = Collections.unmodifiableSet(new LinkedHashSet<>(b.functionsScalar));
        this.functionsAggregate = Collections.unmodifiableSet(new LinkedHashSet<>(b.functionsAggregate));
        this.functionsWindow = Collections.unmodifiableSet(new LinkedHashSet<>(b.functionsWindow));
        this.writeTargets = Collections.unmodifiableSet(new LinkedHashSet<>(b.writeTargets));
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
        return ctes;
    }

    /**
     * @return Join descriptors.
     */
    public Set<String> getJoins() {
        return joins;
    }

    /**
     * @return Scalar function names.
     */
    public Set<String> getFunctionsScalar() {
        return functionsScalar;
    }

    /**
     * @return Aggregate function names.
     */
    public Set<String> getFunctionsAggregate() {
        return functionsAggregate;
    }

    /**
     * @return Window function names.
     */
    public Set<String> getFunctionsWindow() {
        return functionsWindow;
    }

    /**
     * @return Write target names.
     */
    public Set<String> getWriteTargets() {
        return writeTargets;
    }

    /**
     * Builds a compact JSON string representing this result using Jackson.
     *
     * @return JSON representation
     */
    public String toJson() {
        try {
            ObjectNode json = MAPPER.createObjectNode();
            json.put("queryType", queryType);
            json.putPOJO("catalogs", new java.util.TreeSet<>(catalogs));
            json.putPOJO("tables", new java.util.TreeSet<>(tables));
            json.put("usesSelectStar", usesSelectStar);
            json.putPOJO("ctes", new java.util.TreeSet<>(ctes));
            json.putPOJO("joins", new java.util.TreeSet<>(joins));
            json.putPOJO("functionsScalar", new java.util.TreeSet<>(functionsScalar));
            json.putPOJO("functionsAggregate", new java.util.TreeSet<>(functionsAggregate));
            json.putPOJO("functionsWindow", new java.util.TreeSet<>(functionsWindow));
            json.putPOJO("writeTargets", new java.util.TreeSet<>(writeTargets));
            if (hasLimit != null) {
                json.put("hasLimit", hasLimit);
            }
            if (hasWhereOnDelete != null) {
                json.put("hasWhereOnDelete", hasWhereOnDelete);
            }
            if (parseError != null) {
                json.put("parseError", parseError);
            }
            return MAPPER.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize QueryAnalysisResult to JSON", e);
        }
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
         * @return built result
         */
        public QueryAnalysisResult build() {
            return new QueryAnalysisResult(this);
        }
    }
}
