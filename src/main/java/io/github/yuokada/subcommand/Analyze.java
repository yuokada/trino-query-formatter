package io.github.yuokada.subcommand;

import io.github.yuokada.EntryCommand;
import io.github.yuokada.config.AnalyzeConfig;
import io.github.yuokada.config.ConfigException;
import io.github.yuokada.config.LoadedProjectConfig;
import io.github.yuokada.core.AstView;
import io.github.yuokada.core.ExitCodes;
import io.github.yuokada.core.LintFinding;
import io.github.yuokada.core.QueryAnalysisResult;
import io.github.yuokada.core.QueryAnalyzer;
import io.github.yuokada.core.TrinoRemoteValidator;
import io.github.yuokada.core.UdfCatalog;
import io.github.yuokada.core.UdfDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yuokada.subcommand.output.AnalysisPrinter;
import io.github.yuokada.subcommand.output.JsonAnalysisPrinter;
import io.github.yuokada.subcommand.output.OutputEmitter;
import io.github.yuokada.subcommand.output.TextAnalysisPrinter;
import io.github.yuokada.subcommand.util.SqlFileCollector;
import io.github.yuokada.subcommand.util.SqlInput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@CommandLine.Command(name = "analyze", description = "Analyze SQL query")
public class Analyze implements Callable<Integer> {
    /** Error message emitted when more than one statement is supplied. */
    private static final String MULTIPLE_STATEMENTS_ERROR_MESSAGE =
        "analyze supports exactly one query; found multiple statements";
    /** Exit code returned when more than one statement is supplied. */
    private static final int MULTIPLE_STATEMENTS_EXIT_CODE = 1;
    /**
     * Shared JSON mapper for directory-mode JSON output.
     */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * The parent command.
     */
    @ParentCommand
    private EntryCommand entryCommand;

    /**
     * Command specification for checking matched CLI options.
     */
    @Spec
    private CommandSpec spec;

    /**
     * The file to analyze.
     */
    @Parameters(paramLabel = "<file>", defaultValue = "", description = "A query file.")
    private String sqlFile = "";

    /**
     * Directory containing SQL files to analyze recursively.
     */
    @CommandLine.Option(names = {"--dir"},
        description = "Analyze every *.sql file recursively under this directory.")
    private String dirPath;

    /**
     * Glob exclude patterns for directory mode. Repeatable.
     */
    @CommandLine.Option(names = {"--exclude"},
        description = "Exclude glob pattern for --dir mode. Repeatable.")
    private List<String> excludePatterns = new ArrayList<>();

    /**
     * Print compact summary at the end of directory runs.
     */
    @CommandLine.Option(names = {"--summary"},
        description = "Print a compact summary in --dir mode.")
    private boolean summary;

    /**
     * Output format. Supported: text, json.
     */
    @CommandLine.Option(
        names = {"--format"},
        defaultValue = "text",
        description = "Output format: text|json")
    private String format = "text";

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
    private String details = "basic";

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

    /**
     * Trino server connection options for Phase 2 remote EXPLAIN (TYPE VALIDATE).
     * Remote validation is only performed when {@code --server} is specified.
     */
    @CommandLine.ArgGroup(heading = "%nRemote Validation Options:%n", validate = false)
    private TrinoConnectionOptions serverOptions = new TrinoConnectionOptions();

    @Override
    public Integer call() throws IOException {
        try {
            applyConfigDefaults();
        } catch (ConfigException e) {
            System.err.println("Error: " + e.getMessage());
            return ExitCodes.ERROR;
        }

        AstView view;
        try {
            view = AstView.fromString(this.astView);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return ExitCodes.ERROR;
        }

        Integer validationResult = validateOptions();
        if (validationResult != null) {
            return validationResult;
        }

        if (this.dirPath != null && !this.dirPath.isBlank()) {
            return runDirectoryMode(view);
        }

        List<String> statements = collectStatements();
        if (statements.size() > 1) {
            System.err.println(MULTIPLE_STATEMENTS_ERROR_MESSAGE);
            return MULTIPLE_STATEMENTS_EXIT_CODE;
        }

        if (statements.isEmpty()) {
            return ExitCode.OK;
        }
        if (isJsonFormat() && isBasicDetails()) {
            System.err.println(
                "Warning: --details basic suppresses extended fields; use --details full for complete JSON.");
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
            result = validateRemotelyIfNeeded(result, statement);
            printer.printStatement(result, null, statement);
        }
        return ExitCode.OK;
    }

    private Integer validateOptions() {
        boolean hasDir = this.dirPath != null && !this.dirPath.isBlank();
        boolean hasFile = this.sqlFile != null && !this.sqlFile.isEmpty();
        if (hasDir && hasFile) {
            System.err.println("Error: provide either <file> or --dir, not both.");
            return ExitCodes.ERROR;
        }
        if (hasDir) {
            Path dir = Path.of(this.dirPath);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                System.err.println("Error: directory not found: " + this.dirPath);
                return ExitCodes.ERROR;
            }
            if (stdinHasData()) {
                System.err.println("Error: provide either --dir or stdin, not both.");
                return ExitCodes.ERROR;
            }
        }
        if (hasFile) {
            Path sqlPath = Path.of(this.sqlFile);
            if (!Files.exists(sqlPath)) {
                System.err.println("Error: SQL file not found: " + this.sqlFile);
                return ExitCodes.ERROR;
            }
            if (stdinHasData()) {
                System.err.println("Error: provide either <file> or stdin, not both.");
                return ExitCodes.ERROR;
            }
        }
        if (this.udfCatalogPath != null && !this.udfCatalogPath.isBlank()) {
            Path udfPath = Path.of(this.udfCatalogPath);
            if (!Files.exists(udfPath)) {
                System.err.println("Error: UDF catalog file not found: " + this.udfCatalogPath);
                return ExitCodes.ERROR;
            }
            if (!this.validateFunctions) {
                System.err.println(
                    "Info: --udf-catalog implies function existence checking; pass "
                        + "--validate-functions to also enable W002.");
            }
        }
        if (this.serverOptions != null && this.serverOptions.isEnabled() && !isJsonFormat()
            && !isFullDetails()) {
            System.err.println(
                "Warning: remote findings are only shown in --details full mode for text output.");
        }
        return null;
    }

    private Integer runDirectoryMode(AstView view) throws IOException {
        Path baseDir = Path.of(this.dirPath).toAbsolutePath().normalize();
        List<Path> files = SqlFileCollector.collect(this.dirPath, this.excludePatterns);
        SummaryCounter counter = new SummaryCounter();

        Map<String, UdfDefinition> udfCatalog = loadUdfCatalog(this.udfCatalogPath);
        Set<String> effectiveKnown = null;
        if (this.validateFunctions) {
            effectiveKnown = parseKnownFunctions(this.knownFunctionsInput);
            if (udfCatalog != null) {
                effectiveKnown.addAll(udfCatalog.keySet());
            }
        }

        try (OutputEmitter emitter = new OutputEmitter(outputPath)) {
            if (isJsonFormat()) {
                runDirectoryJson(view, baseDir, files, counter, effectiveKnown, udfCatalog, emitter);
            } else {
                runDirectoryText(view, baseDir, files, counter, effectiveKnown, udfCatalog, emitter);
            }
            if (this.summary && !isJsonFormat()) {
                emitTextSummary(emitter, counter);
            }
        }
        return counter.toExitCode();
    }

    private void runDirectoryText(AstView view, Path baseDir, List<Path> files, SummaryCounter counter,
        Set<String> effectiveKnown, Map<String, UdfDefinition> udfCatalog, OutputEmitter emitter)
        throws IOException {
        AnalysisPrinter printer =
            new TextAnalysisPrinter(emitter, isFullDetails(), showAst, view, this.astDepth);
        for (Path file : files) {
            DirectoryAnalysis analysis = analyzeDirectoryFile(baseDir, file, effectiveKnown,
                udfCatalog);
            try {
                emitter.emit("===== " + analysis.label() + " =====");
                if (analysis.errorMessage() != null) {
                    emitter.emit("Error: Failed to analyze " + analysis.label() + ": "
                        + analysis.errorMessage());
                    counter.addError();
                    continue;
                }
                if (analysis.multipleStatements()) {
                    emitter.emit("Error: " + MULTIPLE_STATEMENTS_ERROR_MESSAGE);
                    counter.addError();
                    continue;
                }
                if (analysis.empty()) {
                    emitter.emit("No complete statements.");
                    counter.addClean();
                    continue;
                }
                printer.printStatement(analysis.result(), null, analysis.statement());
                counter.addResult(analysis.result());
            } catch (IOException | RuntimeException e) {
                emitter.emit("Error: Failed to analyze " + analysis.label() + ": "
                    + errorMessage(e));
                counter.addError();
            }
        }
    }

    private void runDirectoryJson(AstView view, Path baseDir, List<Path> files, SummaryCounter counter,
        Set<String> effectiveKnown, Map<String, UdfDefinition> udfCatalog, OutputEmitter emitter)
        throws IOException {
        emitter.emit("[");
        boolean hasItem = false;
        for (Path file : files) {
            DirectoryAnalysis analysis = analyzeDirectoryFile(baseDir, file, effectiveKnown,
                udfCatalog);
            String itemJson;
            try {
                itemJson = buildDirectoryJsonItem(analysis, view);
            } catch (RuntimeException e) {
                itemJson = buildErrorDirectoryJson(analysis.label(), errorMessage(e));
            }
            if (hasItem) {
                emitter.emit("," + itemJson);
            } else {
                emitter.emit(itemJson);
                hasItem = true;
            }
            if (analysis.errorMessage() != null || analysis.multipleStatements()) {
                counter.addError();
            } else if (analysis.empty()) {
                counter.addClean();
            } else {
                counter.addResult(analysis.result());
            }
        }
        if (this.summary) {
            String summaryJson = counter.toJsonSummaryItem();
            if (hasItem) {
                emitter.emit("," + summaryJson);
            } else {
                emitter.emit(summaryJson);
                hasItem = true;
            }
        }
        emitter.emit("]");
    }

    private String buildJsonResult(QueryAnalysisResult result, String originalSql, AstView view) {
        if (isBasicDetails()) {
            ObjectNode node = JSON_MAPPER.createObjectNode();
            if (this.showAst) {
                node.put("ast", limitAst(QueryAnalyzer.dumpAst(originalSql, view, this.astDepth)));
            }
            node.put("queryType", result.getQueryType());
            node.putArray("catalogs").addAll(
                result.getCatalogs().stream()
                    .sorted()
                    .map(JSON_MAPPER.getNodeFactory()::textNode)
                    .toList());
            if (result.getParseError() != null) {
                node.put("parseError", result.getParseError());
            }
            return toJsonString(node);
        }
        try {
            JsonNode node = JSON_MAPPER.readTree(result.toJson());
            if (!(node instanceof ObjectNode objectNode)) {
                throw new IllegalStateException("analysis JSON is not an object");
            }
            if (this.showAst) {
                objectNode.put("ast",
                    limitAst(QueryAnalyzer.dumpAst(originalSql, view, this.astDepth)));
            }
            return toJsonString(objectNode);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build analysis JSON", e);
        }
    }

    private String limitAst(String ast) {
        if (ast == null) {
            return "";
        }
        if (ast.length() <= this.astLimit) {
            return ast;
        }
        return ast.substring(0, this.astLimit) + "\n... (truncated)";
    }

    private DirectoryAnalysis analyzeDirectoryFile(Path baseDir, Path file,
        Set<String> effectiveKnown, Map<String, UdfDefinition> udfCatalog) {
        String label = baseDir.relativize(file.toAbsolutePath().normalize()).toString();
        try {
            List<String> statements = collectStatementsFromFile(file.toString());
            if (statements.size() > 1) {
                return DirectoryAnalysis.multipleStatements(label);
            }
            if (statements.isEmpty()) {
                return DirectoryAnalysis.empty(label);
            }
            String statement = statements.get(0);
            QueryAnalysisResult result = QueryAnalyzer.analyze(
                statement, defaultCatalog, defaultSchema, effectiveKnown, udfCatalog);
            result = validateRemotelyIfNeeded(result, statement);
            return DirectoryAnalysis.result(label, statement, result, SummarySeverity.from(result));
        } catch (IOException | RuntimeException e) {
            return DirectoryAnalysis.error(label, errorMessage(e));
        }
    }

    private QueryAnalysisResult validateRemotelyIfNeeded(QueryAnalysisResult result, String statement) {
        if (result.getParseError() != null) {
            return result;
        }
        return TrinoRemoteValidator.validate(
            result, statement, this.serverOptions, this.defaultCatalog, this.defaultSchema);
    }

    private String buildDirectoryJsonItem(DirectoryAnalysis analysis, AstView view) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        node.put("file", analysis.label());
        if (analysis.errorMessage() != null) {
            node.put("severity", "ERROR");
            node.put("error", analysis.errorMessage());
            return toJsonString(node);
        }
        if (analysis.empty()) {
            node.put("severity", "OK");
            node.put("status", "empty");
            return toJsonString(node);
        }
        node.put("severity", analysis.severity().name());
        try {
            JsonNode resultNode = JSON_MAPPER.readTree(buildJsonResult(analysis.result(),
                analysis.statement(), view));
            node.set("result", resultNode);
            return toJsonString(node);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build directory JSON", e);
        }
    }

    private String buildErrorDirectoryJson(String label, String message) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        node.put("file", label);
        node.put("severity", "ERROR");
        node.put("error", message);
        return toJsonString(node);
    }

    private static String toJsonString(ObjectNode node) {
        try {
            return JSON_MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialise JSON", e);
        }
    }

    private static String errorMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    private void emitTextSummary(OutputEmitter emitter, SummaryCounter counter) throws IOException {
        emitter.emit("Files analyzed : " + counter.totalFiles);
        emitter.emit("  Clean        : " + counter.cleanFiles);
        emitter.emit("  Warnings     : " + counter.warningFiles
            + formatRules(counter.warningRules));
        emitter.emit("  Errors       : " + counter.errorFiles + formatRules(counter.errorRules));
        emitter.emit("Exit code: " + counter.toExitCode());
    }

    private static String formatRules(Set<String> rules) {
        if (rules.isEmpty()) {
            return "";
        }
        return " (" + String.join(", ", rules) + ")";
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
            for (String line : Files.readAllLines(filePath, StandardCharsets.UTF_8)) {
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
        if (this.sqlFile != null && !this.sqlFile.isEmpty()) {
            SqlInput.forEachStatementFromFile(sqlFile, statements::add);
            return statements;
        }
        SqlInput.forEachStatementFromStdin((idx, stmt) -> statements.add(stmt));
        return statements;
    }

    private List<String> collectStatementsFromFile(String filePath) throws IOException {
        List<String> statements = new ArrayList<>();
        SqlInput.forEachStatementFromFile(filePath, statements::add);
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

    private static boolean stdinHasData() {
        InputStream in = System.in;
        if (in == null) {
            return false;
        }
        // available() can block in some environments (e.g. Maven Surefire where System.in
        // is socket-backed). Run the check in a daemon thread with a short timeout to
        // keep this non-blocking in all contexts.
        AtomicBoolean hasData = new AtomicBoolean(false);
        Thread checker = new Thread(() -> {
            try {
                hasData.set(in.available() > 0);
            } catch (IOException ignored) {
                // leave as false
            }
        }, "stdin-checker");
        checker.setDaemon(true);
        checker.start();
        try {
            checker.join(50L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return hasData.get();
    }

    private void applyConfigDefaults() throws ConfigException {
        if (this.entryCommand == null) {
            return;
        }
        LoadedProjectConfig loadedConfig = this.entryCommand.getLoadedConfig();
        if (loadedConfig == null) {
            return;
        }
        AnalyzeConfig analyzeConfig = loadedConfig.getConfig().getAnalyze();
        if (!isOptionMatched("--format") && analyzeConfig.getFormat() != null) {
            this.format = analyzeConfig.getFormat();
        }
        if (!isOptionMatched("--details") && analyzeConfig.getDetails() != null) {
            this.details = analyzeConfig.getDetails();
        }
        if (!isOptionMatched("--validate-functions")
            && analyzeConfig.getValidateFunctions() != null) {
            this.validateFunctions = analyzeConfig.getValidateFunctions();
        }
        if (!isOptionMatched("--udf-catalog") && analyzeConfig.getUdfCatalog() != null) {
            this.udfCatalogPath = loadedConfig.resolvePath(analyzeConfig.getUdfCatalog()).toString();
        }
        if (!isOptionMatched("--server") && analyzeConfig.getServer() != null) {
            this.serverOptions.setServer(analyzeConfig.getServer());
        }
    }

    private boolean isOptionMatched(String name) {
        return this.spec != null
            && this.spec.commandLine() != null
            && this.spec.commandLine().getParseResult() != null
            && this.spec.commandLine().getParseResult().hasMatchedOption(name);
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

    void setServerOptions(TrinoConnectionOptions serverOptions) {
        this.serverOptions = serverOptions;
    }

    void setDirPath(String dirPath) {
        this.dirPath = dirPath;
    }

    void setExcludePatterns(List<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    void setSummary(boolean summary) {
        this.summary = summary;
    }

    private enum SummarySeverity {
        OK,
        WARNING,
        ERROR;

        static SummarySeverity from(QueryAnalysisResult result) {
            if (result.getParseError() != null) {
                return ERROR;
            }
            SummarySeverity severity = OK;
            for (LintFinding finding : result.getFindings()) {
                if (finding.getSeverity() == LintFinding.Severity.ERROR) {
                    return ERROR;
                }
                if (finding.getSeverity() == LintFinding.Severity.WARNING) {
                    severity = WARNING;
                }
            }
            return severity;
        }
    }

    private static final class SummaryCounter {
        private int totalFiles;
        private int cleanFiles;
        private int warningFiles;
        private int errorFiles;
        private final Set<String> warningRules = new LinkedHashSet<>();
        private final Set<String> errorRules = new LinkedHashSet<>();

        private void addClean() {
            this.totalFiles++;
            this.cleanFiles++;
        }

        private void addError() {
            this.totalFiles++;
            this.errorFiles++;
        }

        private void addResult(QueryAnalysisResult result) {
            this.totalFiles++;
            SummarySeverity severity = SummarySeverity.from(result);
            if (severity == SummarySeverity.ERROR) {
                this.errorFiles++;
            } else if (severity == SummarySeverity.WARNING) {
                this.warningFiles++;
            } else {
                this.cleanFiles++;
            }
            for (LintFinding finding : result.getFindings()) {
                if (finding.getSeverity() == LintFinding.Severity.ERROR) {
                    this.errorRules.add(finding.getRuleId());
                } else if (finding.getSeverity() == LintFinding.Severity.WARNING) {
                    this.warningRules.add(finding.getRuleId());
                }
            }
        }

        private int toExitCode() {
            if (this.errorFiles > 0) {
                return ExitCodes.ERROR;
            }
            if (this.warningFiles > 0) {
                return ExitCodes.WARNING;
            }
            return ExitCodes.OK;
        }

        private String toJsonSummaryItem() {
            ObjectNode root = JSON_MAPPER.createObjectNode();
            ObjectNode summary = root.putObject("summary");
            summary.put("filesAnalyzed", this.totalFiles);
            summary.put("clean", this.cleanFiles);
            summary.put("warnings", this.warningFiles);
            summary.put("errors", this.errorFiles);
            summary.putArray("warningRules").addAll(
                this.warningRules.stream()
                    .sorted()
                    .map(JSON_MAPPER.getNodeFactory()::textNode)
                    .toList());
            summary.putArray("errorRules").addAll(
                this.errorRules.stream()
                    .sorted()
                    .map(JSON_MAPPER.getNodeFactory()::textNode)
                    .toList());
            summary.put("exitCode", toExitCode());
            return toJsonString(root);
        }
    }

    private record DirectoryAnalysis(
        String label,
        String statement,
        QueryAnalysisResult result,
        SummarySeverity severity,
        String errorMessage,
        boolean empty,
        boolean multipleStatements) {

        static DirectoryAnalysis result(String label, String statement, QueryAnalysisResult result,
            SummarySeverity severity) {
            return new DirectoryAnalysis(label, statement, result, severity, null, false, false);
        }

        static DirectoryAnalysis empty(String label) {
            return new DirectoryAnalysis(label, null, null, SummarySeverity.OK, null, true, false);
        }

        static DirectoryAnalysis multipleStatements(String label) {
            return new DirectoryAnalysis(label, null, null, SummarySeverity.ERROR,
                MULTIPLE_STATEMENTS_ERROR_MESSAGE, false, true);
        }

        static DirectoryAnalysis error(String label, String errorMessage) {
            return new DirectoryAnalysis(label, null, null, SummarySeverity.ERROR, errorMessage,
                false, false);
        }

    }
}
