package io.github.yuokada.subcommand;

import io.github.yuokada.EntryCommand;
import io.github.yuokada.core.AstView;
import io.github.yuokada.core.ExitCodes;
import io.github.yuokada.core.QueryAnalysisResult;
import io.github.yuokada.core.QueryAnalyzer;
import io.github.yuokada.subcommand.output.AnalysisPrinter;
import io.github.yuokada.subcommand.output.JsonAnalysisPrinter;
import io.github.yuokada.subcommand.output.OutputEmitter;
import io.github.yuokada.subcommand.output.TextAnalysisPrinter;
import io.github.yuokada.subcommand.util.SqlInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@CommandLine.Command(name = "analyze", description = "Analyze SQL query")
public class Analyze implements Callable<Integer> {
    private static final String MULTIPLE_STATEMENTS_ERROR_MESSAGE =
        "analyze supports exactly one query; found multiple statements";
    private static final int MULTIPLE_STATEMENTS_EXIT_CODE = 1;

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
        "--ast-limit"}, defaultValue = "10000",
        description = "Maximum characters for embedded AST in JSON output")
    private int astLimit;

    /**
     * AST display mode. Supported: tree (default), outline, raw.
     */
    @CommandLine.Option(names = {"--ast-view"}, defaultValue = "tree",
        description = "AST display mode: tree (default), outline, or raw.")
    private String astView = "tree";

    /**
     * Maximum AST depth to display. 0 means unlimited.
     */
    @CommandLine.Option(names = {"--ast-depth"}, defaultValue = "0",
        description = "Maximum AST depth to display. 0 = unlimited.")
    private int astDepth;

    @Override
    public Integer call() throws IOException {
        AstView view;
        try {
            view = AstView.fromString(this.astView);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return ExitCodes.ERROR;
        }

        List<String> statements = collectStatements();
        if (statements.size() > 1) {
            System.err.println(MULTIPLE_STATEMENTS_ERROR_MESSAGE);
            return MULTIPLE_STATEMENTS_EXIT_CODE;
        }

        if (statements.isEmpty()) {
            return ExitCode.OK;
        }

        String statement = statements.get(0);
        try (OutputEmitter emitter = new OutputEmitter(outputPath);
            AnalysisPrinter printer = isJsonFormat()
                ? new JsonAnalysisPrinter(emitter, isBasicDetails(), showAst, astLimit,
                    view, this.astDepth)
                : new TextAnalysisPrinter(emitter, isFullDetails(), showAst, view,
                    this.astDepth)) {
            QueryAnalysisResult result =
                QueryAnalyzer.analyze(statement, defaultCatalog, defaultSchema);
            printer.printStatement(result, null, statement);
        }
        return ExitCode.OK;
    }

    private List<String> collectStatements() throws IOException {
        List<String> statements = new ArrayList<>();
        if (!sqlFile.isEmpty()) {
            SqlInput.forEachStatementFromFile(sqlFile, statements::add);
            return statements;
        }
        SqlInput.forEachStatementFromStdin((idx, stmt) -> statements.add(stmt));
        return statements;
    }

    /**
     * Checks if the selected output format is JSON.
     *
     * @return true if JSON format is selected, false otherwise.
     */
    private boolean isJsonFormat() {
        return "json".equalsIgnoreCase(format);
    }

    /**
     * Returns true when --details=basic is selected.
     *
     * @return true if basic details mode is selected, false otherwise.
     */
    private boolean isBasicDetails() {
        return "basic".equalsIgnoreCase(details);
    }

    /**
     * Returns true when --details=full is selected.
     *
     * @return true if full details mode is selected, false otherwise.
     */
    private boolean isFullDetails() {
        return "full".equalsIgnoreCase(details);
    }

    // Package-private setters to support testing without reflection.
    // These methods should only be used in test code.

    void setSqlFile(String sqlFile) {
        this.sqlFile = sqlFile;
    }

    void setFormat(String format) {
        this.format = format;
    }

    void setShowAst() {
        this.showAst = true;
    }

    void setDetails(String details) {
        this.details = details;
    }

    void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    void setDefaultCatalog(String defaultCatalog) {
        this.defaultCatalog = defaultCatalog;
    }

    void setDefaultSchema(String defaultSchema) {
        this.defaultSchema = defaultSchema;
    }

    void setAstLimit(int astLimit) {
        this.astLimit = astLimit;
    }

    void setAstView(String astView) {
        this.astView = astView;
    }

    void setAstDepth(int astDepth) {
        this.astDepth = astDepth;
    }
}
