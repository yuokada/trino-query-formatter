package io.github.yuokada.subcommand.output;

import io.github.yuokada.core.QueryAnalysisResult;
import java.io.IOException;

/**
 * Printer for analysis results. Implementations define format-specific output.
 */
public interface AnalysisPrinter extends AutoCloseable {

    /**
     * Prints a single statement's analysis.
     *
     * @param result      The analysis result of the statement.
     * @param queryId     Optional statement index (1-based) for text mode labeling; may be null.
     * @param originalSql The original SQL text of the statement (used for AST printing and context).
     */
    void printStatement(QueryAnalysisResult result, Integer queryId, String originalSql);

    /**
     * Flushes and releases resources.
     *
     * @throws IOException when writing fails.
     */
    void close() throws IOException;
}

