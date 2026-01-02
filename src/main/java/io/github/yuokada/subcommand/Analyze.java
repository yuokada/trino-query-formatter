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
    private String format;

    /**
     * When true, also prints the AST of each statement.
     */
    @CommandLine.Option(
        names = {"--show-ast"},
        defaultValue = "false",
        description = "Show AST for each statement")
    private boolean showAst;

    /**
     * Detail level. Supported: basic, full. Default: basic.
     */
    @CommandLine.Option(
        names = {"--details"},
        defaultValue = "basic",
        description = "Detail level: basic|full")
    private String details;

    /**
     * If specified, writes the output to the given file instead of stdout.
     */
    @CommandLine.Option(names = {
        "--output"}, description = "Write output to the specified file path")
    private String outputPath;

    /**
     * Default catalog for unqualified or partially qualified names.
     */
    @CommandLine.Option(names = {
        "--catalog"}, description = "Default catalog for un/partially qualified names")
    private String defaultCatalog;

    /**
     * Default schema for unqualified names.
     */
    @CommandLine.Option(names = {"--schema"}, description = "Default schema for unqualified names")
    private String defaultSchema;

    /**
     * Maximum characters for embedded AST (JSON).
     */
    @CommandLine.Option(names = {
        "--ast-limit"}, defaultValue = "10000", description = "Maximum characters for embedded AST in JSON output")
    private int astLimit;

    /**
     * Package-private constructor for testing purposes.
     * Creates an Analyze instance with specified configuration.
     */
    Analyze(String sqlFile,
            String format,
            boolean showAst,
            String details,
            String outputPath,
            String defaultCatalog,
            String defaultSchema,
            int astLimit) {
        this.sqlFile = sqlFile;
        this.format = format;
        this.showAst = showAst;
        this.details = details;
        this.outputPath = outputPath;
        this.defaultCatalog = defaultCatalog;
        this.defaultSchema = defaultSchema;
        this.astLimit = astLimit;
    }

    /**
     * Default constructor for Picocli.
     */
    public Analyze() {
    }

    @Override
    public Integer call() throws IOException {
        // Create OutputEmitter in try-with-resources for automatic cleanup.
        // When outputPath is null, emitter writes to stdout without managing resources.
        // When outputPath is specified, emitter manages the file resource.
        try (OutputEmitter emitter = new OutputEmitter(outputPath);
             AnalysisPrinter printer = isJsonFormat()
                 ? new JsonAnalysisPrinter(emitter, isBasicDetails(), showAst, astLimit)
                 : new TextAnalysisPrinter(emitter, isFullDetails(), showAst)) {

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
        }
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
