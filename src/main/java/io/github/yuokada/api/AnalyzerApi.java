package io.github.yuokada.api;

import io.github.yuokada.core.QueryAnalysisResult;
import io.github.yuokada.core.QueryAnalyzer;
import io.github.yuokada.core.UdfDefinition;
import java.util.Map;
import java.util.Set;

/**
 * Library-facing API for analyzing a single SQL statement.
 */
public final class AnalyzerApi {

    private AnalyzerApi() {
    }

    /**
     * Analyzes one SQL statement.
     *
     * @param sql SQL statement
     * @param defaultCatalog optional default catalog
     * @param defaultSchema optional default schema
     * @param knownFunctions optional known function names
     * @param udfCatalog optional UDF catalog
     * @return analysis result
     */
    public static QueryAnalysisResult analyzeStatement(
        String sql,
        String defaultCatalog,
        String defaultSchema,
        Set<String> knownFunctions,
        Map<String, UdfDefinition> udfCatalog) {
        return QueryAnalyzer.analyze(
            sql, defaultCatalog, defaultSchema, knownFunctions, udfCatalog);
    }
}
