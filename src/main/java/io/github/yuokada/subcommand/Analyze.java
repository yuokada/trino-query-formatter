package io.github.yuokada.subcommand;

import io.github.yuokada.EntryCommand;
import io.github.yuokada.core.AstView;
import io.github.yuokada.core.ExitCodes;
import io.github.yuokada.core.QueryAnalysisResult;
import io.github.yuokada.core.QueryAnalyzer;
import io.github.yuokada.core.UdfCatalog;
import io.github.yuokada.core.UdfDefinition;
import io.github.yuokada.subcommand.output.AnalysisPrinter;
import io.github.yuokada.subcommand.output.JsonAnalysisPrinter;
import io.github.yuokada.subcommand.output.OutputEmitter;
import io.github.yuokada.subcommand.output.TextAnalysisPrinter;
import io.github.yuokada.subcommand.util.SqlInput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@CommandLine.Command(name = "analyze", description = "Analyze SQL query")
public class Analyze implements Callable<Integer> {
    /** Error message emitted when more than one statement is supplied. */
    private static final String MULTIPLE_STATEMENTS_ERROR_MESSAGE =
        "analyze supports exactly one query; found multiple statements";
    /** Exit code returned when more than one statement is supplied. */
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

    /**
     * When true, unknown functions (not Trino built-ins) are flagged as W002 lint findings.
     */
    @CommandLine.Option(names = {"--validate-functions"}, defaultValue = "false",
        description = "Flag unknown functions (not Trino built-ins) as W002 lint warnings.")
    private boolean validateFunctions;

    /**
     * Comma-separated list of additional known function names, or {@code @<file>} to load from
     * a UTF-8 text file (one name per line; empty lines and lines starting with '#' are ignored).
     * Only effective when {@code --validate-functions} is also set.
     */
    @CommandLine.Option(names = {"--known-functions"},
        description = "Extra known function names (comma-separated or @file).")
    private String knownFunctionsInput;

    /**
     * Path to a YAML file containing UDF definitions for arity validation (W003).
     * Functions listed in the catalog are also treated as "known" when
     * {@code --validate-functions} is enabled, suppressing W002 for them.
     */
    @CommandLine.Option(names = {"--udf-catalog"},
        description = "YAML file with UDF definitions for arity validation (W003).")
    private String udfCatalogPath;

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

        Map<String, UdfDefinition> udfCatalog = loadUdfCatalog(this.udfCatalogPath);

        // Build the effective known-functions set.
        // null means W002 is disabled; non-null enables W002 for unrecognized functions.
        Set<String> effectiveKnown = null;
        if (this.validateFunctions) {
            effectiveKnown = parseKnownFunctions(this.knownFunctionsInput);
            // Functions in the UDF catalog are always treated as known (no W002).
            if (udfCatalog != null) {
                effectiveKnown.addAll(udfCatalog.keySet());
            }
        }

        String statement = statements.get(0);
        try (OutputEmitter emitter = new OutputEmitter(outputPath);
            AnalysisPrinter printer = isJsonFormat()
                ? new JsonAnalysisPrinter(emitter, isBasicDetails(), showAst, astLimit,
                    view, this.astDepth)
                : new TextAnalysisPrinter(emitter, isFullDetails(), showAst, view,
                    this.astDepth)) {
            QueryAnalysisResult result =
                QueryAnalyzer.analyze(statement, defaultCatalog, defaultSchema,
                    effectiveKnown, udfCatalog);
            printer.printStatement(result, null, statement);
        }
        return ExitCode.OK;
    }

    /**
     * Loads the UDF catalog from the given YAML file path.
     *
     * @param path raw option value (may be null or blank)
     * @return parsed UDF catalog map, or {@code null} when no path is specified
     * @throws IOException if the file cannot be read or is not valid YAML
     */
    private static Map<String, UdfDefinition> loadUdfCatalog(String path) throws IOException {
        if (path == null || path.isBlank()) {
            return null;
        }
        return UdfCatalog.load(Path.of(path));
    }

    /**
     * Parses the {@code --known-functions} input into a set of lowercase function names.
     *
     * <p>Accepts:
     * <ul>
     *   <li>Comma-separated list: {@code "my_udf,another_udf"}</li>
     *   <li>File reference: {@code "@/path/to/udfs.txt"} — one name per line,
     *       empty lines and lines starting with {@code #} are ignored.</li>
     * </ul>
     *
     * @param input raw option value (may be null)
     * @return parsed set of lowercase function names (never null)
     * @throws IOException if a referenced file cannot be read
     */
    private static Set<String> parseKnownFunctions(String input) throws IOException {
        Set<String> result = new HashSet<>();
        if (input == null || input.isBlank()) {
            return result;
        }
        if (input.startsWith("@")) {
            Path filePath = Path.of(input.substring(1));
            for (String line : Files.readAllLines(filePath)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    result.add(trimmed.toLowerCase());
                }
            }
        } else {
            for (String part : input.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed.toLowerCase());
                }
            }
        }
        return result;
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

    void setValidateFunctions(boolean validateFunctions) {
        this.validateFunctions = validateFunctions;
    }

    void setKnownFunctionsInput(String knownFunctionsInput) {
        this.knownFunctionsInput = knownFunctionsInput;
    }

    void setUdfCatalogPath(String udfCatalogPath) {
        this.udfCatalogPath = udfCatalogPath;
    }
}
