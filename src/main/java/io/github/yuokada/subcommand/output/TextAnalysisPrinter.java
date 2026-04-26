package io.github.yuokada.subcommand.output;

import io.github.yuokada.core.AstView;
import io.github.yuokada.core.LintFinding;
import io.github.yuokada.core.QueryAnalysisResult;
import io.github.yuokada.core.QueryAnalyzer;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Text-mode printer supporting basic and full detail levels.
 */
public final class TextAnalysisPrinter implements AnalysisPrinter {

    /**
     * Output sink for emitting formatted results.
     */
    private final OutputEmitter emitter;

    /**
     * When true, prints extended info such as tables and flags.
     */
    private final boolean fullDetails;

    /**
     * When true, also prints AST after each statement.
     */
    private final boolean showAst;

    /**
     * AST display mode used when {@link #showAst} is true.
     */
    private final AstView astView;

    /**
     * Maximum AST depth to display; 0 means unlimited.
     */
    private final int astDepth;

    /**
     * @param emitter     Output sink.
     * @param fullDetails When true, prints extended info such as tables and flags.
     * @param showAst     When true, also prints AST after each statement.
     * @param astView     AST display mode (TREE, OUTLINE, RAW).
     * @param astDepth    Maximum depth to display; 0 = unlimited.
     */
    public TextAnalysisPrinter(OutputEmitter emitter, boolean fullDetails, boolean showAst,
        AstView astView, int astDepth) {
        this.emitter = emitter;
        this.fullDetails = fullDetails;
        this.showAst = showAst;
        this.astView = astView;
        this.astDepth = astDepth;
    }

    @Override
    public void printStatement(QueryAnalysisResult result, Integer queryId, String originalSql)
        throws IOException {
        emitter.emit("=========================");
        if (fullDetails) {
            printFull(result, queryId);
        } else {
            printBasic(result.getCatalogs(), queryId);
        }
        if (showAst) {
            emitter.emit("AST (" + this.astView.name().toLowerCase() + "):");
            emitter.emit(QueryAnalyzer.dumpAst(originalSql, this.astView, this.astDepth));
        }
    }

    private void printBasic(Set<String> catalogs, Integer queryId) throws IOException {
        if (catalogs.isEmpty()) {
            emitter.emit("No catalogs found.");
            return;
        }
        String cats = String.join(",", catalogs.stream().sorted().toList());
        if (queryId != null) {
            emitter.emit(String.format("Catalogs of Query No %d: [%s]", queryId, cats));
        } else {
            emitter.emit(String.format("Catalogs: [%s]", cats));
        }
    }

    private void printFull(QueryAnalysisResult r, Integer queryId) throws IOException {
        printBasic(r.getCatalogs(), queryId);
        emitter.emit(String.format("QueryType: %s", r.getQueryType()));
        String tables = String.join(",", r.getTables().stream().sorted().toList());
        emitter.emit(String.format("Tables: [%s]", tables));
        String ctes = String.join(",", r.getCtes());
        if (!ctes.isEmpty()) {
            emitter.emit(String.format("CTEs: [%s]", ctes));
        }
        String joins = String.join(",", r.getJoins().stream().sorted().toList());
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
                String.join(",", r.getFunctionsScalar().stream().sorted().toList()),
                String.join(",", r.getFunctionsAggregate().stream().sorted().toList()),
                String.join(",", r.getFunctionsWindow().stream().sorted().toList())
            ));
        }
        if (!r.getUnknownFunctions().isEmpty()) {
            emitter.emit(String.format("UnknownFunctions: [%s]",
                String.join(",", r.getUnknownFunctions().stream().sorted().toList())));
        }
        if (!r.getWriteTargets().isEmpty()) {
            emitter.emit(
                String.format("WriteTargets: [%s]",
                    String.join(",", r.getWriteTargets().stream().sorted().toList())));
        }
        List<LintFinding> findings = r.getFindings();
        if (!findings.isEmpty()) {
            for (LintFinding f : findings) {
                emitter.emit("Lint: " + f.toString());
                emitter.emit("  Why: " + f.getHint());
                emitter.emit("  Fix: " + f.getFix());
            }
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
