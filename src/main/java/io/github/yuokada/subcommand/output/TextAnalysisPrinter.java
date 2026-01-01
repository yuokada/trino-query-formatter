package io.github.yuokada.subcommand.output;

import io.github.yuokada.core.QueryAnalysisResult;
import io.github.yuokada.core.QueryAnalyzer;
import java.io.IOException;
import java.util.Set;

/**
 * Text-mode printer supporting basic and full detail levels.
 */
public final class TextAnalysisPrinter implements AnalysisPrinter {

    private final OutputEmitter emitter;
    private final boolean fullDetails;
    private final boolean showAst;

    /**
     * @param emitter     Output sink.
     * @param fullDetails When true, prints extended info such as tables and flags.
     * @param showAst     When true, also prints AST after each statement.
     */
    public TextAnalysisPrinter(OutputEmitter emitter, boolean fullDetails, boolean showAst) {
        this.emitter = emitter;
        this.fullDetails = fullDetails;
        this.showAst = showAst;
    }

    @Override
    public void printStatement(QueryAnalysisResult result, Integer queryId, String originalSql) {
        emitter.emit("=========================");
        if (fullDetails) {
            printFull(result, queryId);
        } else {
            printBasic(result.getCatalogs(), queryId);
        }
        if (showAst) {
            emitter.emit("AST:");
            emitter.emit(QueryAnalyzer.dumpAst(originalSql));
        }
    }

    private void printBasic(Set<String> catalogs, Integer queryId) {
        if (catalogs.isEmpty()) {
            emitter.emit("No catalogs found.");
            return;
        }
        String cats = String.join(",", catalogs);
        if (queryId != null) {
            emitter.emit(String.format("Catalogs of Query No %d: [%s]", queryId, cats));
        } else {
            emitter.emit(String.format("Catalogs: [%s]", cats));
        }
    }

    private void printFull(QueryAnalysisResult r, Integer queryId) {
        printBasic(r.getCatalogs(), queryId);
        emitter.emit(String.format("QueryType: %s", r.getQueryType()));
        String tables = String.join(",", r.getTables());
        emitter.emit(String.format("Tables: [%s]", tables));
        String ctes = String.join(",", r.getCtes());
        if (!ctes.isEmpty()) {
            emitter.emit(String.format("CTEs: [%s]", ctes));
        }
        String joins = String.join(",", r.getJoins());
        if (!joins.isEmpty()) {
            emitter.emit(String.format("Joins: [%s]", joins));
        }
        String flags = String.format(
            "Flags: usesSelectStar=%s, hasLimit=%s, hasWhereOnDelete=%s",
            r.isUsesSelectStar(),
            r.getHasLimit(),
            r.getHasWhereOnDelete()
        );
        emitter.emit(flags);
        if (!r.getFunctionsScalar().isEmpty() || !r.getFunctionsAggregate().isEmpty()
            || !r.getFunctionsWindow().isEmpty()) {
            emitter.emit(String.format(
                "Functions: scalar=[%s], aggregate=[%s], window=[%s]",
                String.join(",", r.getFunctionsScalar()),
                String.join(",", r.getFunctionsAggregate()),
                String.join(",", r.getFunctionsWindow())
            ));
        }
        if (!r.getWriteTargets().isEmpty()) {
            emitter.emit(
                String.format("WriteTargets: [%s]", String.join(",", r.getWriteTargets())));
        }
        if (r.getParseError() != null) {
            emitter.emit(String.format("ParseError: %s", r.getParseError()));
        }
    }

    @Override
    public void close() throws IOException {
        emitter.close();
    }
}
