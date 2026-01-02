package io.github.yuokada.subcommand;

import io.github.yuokada.EntryCommand;
import io.github.yuokada.core.QueryAnalysisResult;
import io.github.yuokada.core.QueryAnalyzer;
import io.github.yuokada.subcommand.output.AnalysisPrinter;
import io.github.yuokada.subcommand.output.JsonAnalysisPrinter;
import io.github.yuokada.subcommand.output.OutputEmitter;
import io.github.yuokada.subcommand.output.TextAnalysisPrinter;
import io.github.yuokada.subcommand.util.SqlInput;
import java.io.IOException;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@CommandLine.Command(name = "analyze", description = "Analyze SQL query")
public class Analyze implements Callable<Integer> {

    /**
     * The parent command.
     */
    @ParentCommand
    private EntryCommand entryCommand;

    // No parser needed here; QueryAnalyzer handles parsing

    /**
     * The file to analyze.
     */
    @Parameters(paramLabel = "<file>", defaultValue = "", description = "A query file.")
    private String sqlFile;

    /**
     * Output format. Supported: text, json.
     */
    @CommandLine.Option(
        names = {"--format"},
        defaultValue = "text",
        description = "Output format: text|json")
    String format;

    /**
     * When true, also prints the AST of each statement.
     */
    @CommandLine.Option(
        names = {"--show-ast"},
        defaultValue = "false",
        description = "Show AST for each statement")
    boolean showAst;

    /**
     * Detail level. Supported: basic, full. Default: basic.
     */
    @CommandLine.Option(
        names = {"--details"},
        defaultValue = "basic",
        description = "Detail level: basic|full")
    String details;

    /**
     * If specified, writes the output to the given file instead of stdout.
     */
    @CommandLine.Option(names = {
        "--output"}, description = "Write output to the specified file path")
    String outputPath;

    /**
     * Default catalog for unqualified or partially qualified names.
     */
    @CommandLine.Option(names = {
        "--catalog"}, description = "Default catalog for un/partially qualified names")
    String defaultCatalog;

    /**
     * Default schema for unqualified names.
     */
    @CommandLine.Option(names = {"--schema"}, description = "Default schema for unqualified names")
    String defaultSchema;

    /**
     * Maximum characters for embedded AST (JSON).
     */
    @CommandLine.Option(names = {
        "--ast-limit"}, defaultValue = "10000", description = "Maximum characters for embedded AST in JSON output")
    int astLimit;

    @Override
    public Integer call() throws IOException {
        OutputEmitter emitter = new OutputEmitter(outputPath);
        AnalysisPrinter printer = isJsonFormat()
            ? new JsonAnalysisPrinter(emitter, isBasicDetails(), showAst, astLimit)
            : new TextAnalysisPrinter(emitter, isFullDetails(), showAst);

        if (!sqlFile.isEmpty()) {
            SqlInput.forEachStatementFromFile(sqlFile, stmt -> {
                QueryAnalysisResult result =
                    QueryAnalyzer.analyze(stmt, defaultCatalog, defaultSchema);
                printer.printStatement(result, null, stmt);
            });
        } else {
            SqlInput.forEachStatementFromStdin((idx, stmt) -> {
                QueryAnalysisResult result =
                    QueryAnalyzer.analyze(stmt, defaultCatalog, defaultSchema);
                printer.printStatement(result, idx, stmt);
            });
        }

        printer.close();
        return ExitCode.OK;
    }

    /**
     * Checks if the selected output format is JSON.
     */
    private boolean isJsonFormat() {
        return "json".equalsIgnoreCase(format);
    }

    /**
     * Returns true when --details=basic is selected.
     */
    private boolean isBasicDetails() {
        return "basic".equalsIgnoreCase(details);
    }

    /**
     * Returns true when --details=full is selected.
     */
    private boolean isFullDetails() {
        return "full".equalsIgnoreCase(details);
    }
}
