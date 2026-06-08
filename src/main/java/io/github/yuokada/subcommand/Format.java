package io.github.yuokada.subcommand;

import static com.google.common.base.Preconditions.checkState;
import static io.trino.sql.SqlFormatter.formatSql;

import io.github.yuokada.EntryCommand;
import io.github.yuokada.config.ConfigException;
import io.github.yuokada.config.FormatConfig;
import io.github.yuokada.config.LoadedProjectConfig;
import io.github.yuokada.core.CommentPreservingFormatter;
import io.github.yuokada.core.ExitCodes;
import io.github.yuokada.core.KeywordCaseTransformer;
import io.github.yuokada.core.UnifiedDiff;
import io.github.yuokada.core.KeywordCaseTransformer.KeywordCase;
import io.github.yuokada.subcommand.output.OutputEmitter;
import io.github.yuokada.subcommand.util.SqlFileCollector;
import io.github.yuokada.subcommand.util.SqlInput;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Statement;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Subcommand that formats one or more SQL statements.
 *
 * <p>Reads from a file, from {@code -} (stdin), or from stdin when no argument is given.
 * Formatted output goes to stdout or to a file specified with {@code --output}.
 */
@CommandLine.Command(name = "format", description = "Format SQL query")
public class Format implements Callable<Integer> {

    /**
     * The parent command (may be {@code null} when constructed directly in tests).
     */
    @ParentCommand
    private EntryCommand entryCommand;

    /**
     * Command specification for checking matched CLI options.
     */
    @Spec
    private CommandSpec spec;

    /**
     * Shared SQL parser instance.
     */
    private static final SqlParser SQL_PARSER = new SqlParser();

    /**
     * Input file path. Use {@code -} to read from stdin.
     */
    @Parameters(paramLabel = "<file>", defaultValue = "",
        description = "A query file. Use '-' for stdin.")
    private String sqlFile = "";

    /**
     * Directory containing SQL files to process recursively.
     */
    @CommandLine.Option(names = {"--dir"},
        description = "Process every *.sql file recursively under this directory.")
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
     * Output file path. When null or empty, writes to stdout.
     */
    @CommandLine.Option(names = {"-o", "--output"},
        description = "Write output to this file instead of stdout.")
    private String outputPath;

    /**
     * When true, check if input is already formatted without writing output.
     * Exits with {@link ExitCodes#WARNING} if any statement would be reformatted.
     */
    @CommandLine.Option(names = {"--check"},
        description = "Check if input is already formatted; exit 1 if not.")
    private boolean check;

    /**
     * When true, print a colored unified diff of what would change without writing output.
     * Exits with {@link ExitCodes#WARNING} when differences are found.
     */
    @CommandLine.Option(names = {"--diff"},
        description = "Show unified diff of formatting changes; exit 1 if not already formatted.")
    private boolean diff;

    /**
     * SQL keyword case mode. One of {@code upper} (default), {@code lower}, or {@code keep}.
     */
    @CommandLine.Option(names = {"--keyword-case"}, defaultValue = "upper",
        description = "SQL keyword case: upper (default), lower, or keep.")
    private String keywordCase = "upper";

    /**
     * Number of spaces per indentation level.
     * Trino's formatter uses 2 by default; this option rescales the output.
     */
    @CommandLine.Option(names = {"--indent-size"}, defaultValue = "2",
        description = "Spaces per indentation level (default: 2).")
    private int indentSize = 2;

    /**
     * Maximum line length in characters.
     * When {@code > 0}, lines exceeding this length are reported to stderr.
     * The formatted output is not truncated.
     */
    @CommandLine.Option(names = {"--max-line-length"}, defaultValue = "0",
        description = "Warn when formatted lines exceed this length. 0 = unlimited.")
    private int maxLineLength;

    /**
     * Optional directory parallelism override for tests.
     */
    private Integer directoryParallelismOverride;

    @Override
    public Integer call() throws IOException {
        try {
            applyConfigDefaults();
        } catch (ConfigException e) {
            System.err.println("Error: " + e.getMessage());
            return ExitCodes.ERROR;
        }
        if (this.dirPath != null && !this.dirPath.isBlank()) {
            return runDirectoryMode();
        }

        Integer validationResult = validateOptions();
        if (validationResult != null) {
            return validationResult;
        }
        boolean isStdin = this.sqlFile.isEmpty() || this.sqlFile.equals("-");

        KeywordCase kc;
        try {
            kc = KeywordCase.fromString(this.keywordCase);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return ExitCodes.ERROR;
        }

        if (this.indentSize < 1) {
            System.err.println("--indent-size must be >= 1, got: " + this.indentSize);
            return ExitCodes.ERROR;
        }

        if ((this.check || this.diff) && isStdin) {
            System.err.println("--check/--diff is not supported for stdin input");
            return ExitCodes.ERROR;
        }

        if (this.diff) {
            return runDiffMode(this.sqlFile, kc);
        }

        if (this.check) {
            return runCheckMode(this.sqlFile, kc);
        }

        String sourceName = isStdin ? "<stdin>" : this.sqlFile;
        if (isVerbose()) {
            System.err.println("Formatting: " + sourceName);
        }

        try (OutputEmitter emitter = new OutputEmitter(this.outputPath)) {
            int[] count = {0};
            if (isStdin) {
                SqlInput.forEachStatementFromStdin((idx, stmt) -> {
                    String result = formatStatement(stmt, kc);
                    warnLongLines(result, sourceName);
                    emitter.emit(result + ";");
                    count[0]++;
                });
            } else {
                SqlInput.forEachStatementFromFile(this.sqlFile, stmt -> {
                    String result = formatStatement(stmt, kc);
                    warnLongLines(result, sourceName);
                    emitter.emit(result + ";");
                    count[0]++;
                });
            }
            if (isVerbose()) {
                System.err.println("Formatted " + count[0] + " statement(s): " + sourceName);
            }
        }
        return ExitCodes.OK;
    }

    private Integer validateOptions() {
        boolean hasDir = this.dirPath != null && !this.dirPath.isBlank();
        boolean isStdin = this.sqlFile == null || this.sqlFile.isEmpty() || this.sqlFile.equals("-");
        boolean hasFile = !isStdin;
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
            if (!this.check && !this.diff) {
                System.err.println("Error: --dir mode requires --check or --diff.");
                return ExitCodes.ERROR;
            }
            if (this.outputPath != null && !this.outputPath.isBlank()) {
                System.err.println("Error: --output is not supported with --dir.");
                return ExitCodes.ERROR;
            }
            return null;
        }
        if (!isStdin) {
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
        return null;
    }

    private Integer runDirectoryMode() throws IOException {
        Integer validationResult = validateOptions();
        if (validationResult != null) {
            return validationResult;
        }

        KeywordCase kc;
        try {
            kc = KeywordCase.fromString(this.keywordCase);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return ExitCodes.ERROR;
        }

        Path baseDir = Path.of(this.dirPath).toAbsolutePath().normalize();
        List<Path> files = SqlFileCollector.collect(this.dirPath, this.excludePatterns);
        int[] counts = new int[3];
        processDirectoryFiles(baseDir, files, kc, result -> {
            if (!result.stdout().isEmpty()) {
                System.out.print(result.stdout());
            }
            if (!result.stderr().isEmpty()) {
                System.err.print(result.stderr());
            }
            if (result.exitCode() == ExitCodes.ERROR) {
                counts[2]++;
            } else if (result.exitCode() == ExitCodes.WARNING) {
                counts[1]++;
            } else {
                counts[0]++;
            }
        });

        int exitCode;
        if (counts[2] > 0) {
            exitCode = ExitCodes.ERROR;
        } else if (counts[1] > 0) {
            exitCode = ExitCodes.WARNING;
        } else {
            exitCode = ExitCodes.OK;
        }

        if (this.summary) {
            int total = counts[0] + counts[1] + counts[2];
            System.out.println("Files analyzed : " + total);
            System.out.println("  Clean        : " + counts[0]);
            System.out.println("  Warnings     : " + counts[1]);
            System.out.println("  Errors       : " + counts[2]);
            System.out.println("Exit code: " + exitCode);
        }
        return exitCode;
    }

    /**
     * Compares the expected formatted output of {@code path} with its current contents.
     * Prints to stderr and returns {@link ExitCodes#WARNING} when differences are found,
     * unless quiet mode is active.
     *
     * @param path the file path to check
     * @param kc   the keyword case mode to apply during formatting
     * @return {@link ExitCodes#OK} when already formatted, {@link ExitCodes#WARNING} otherwise
     * @throws IOException if the file cannot be read
     */
    private Integer runCheckMode(String path, KeywordCase kc) throws IOException {
        int exitCode = checkFormatted(path, kc);
        if (exitCode == ExitCodes.WARNING) {
            if (!isQuiet()) {
                System.err.println("Would reformat: " + path);
            }
        }
        return exitCode;
    }

    /**
     * Computes and prints a unified diff between the current contents of {@code path}
     * and the formatted output, then returns {@link ExitCodes#WARNING} when differences
     * are found or {@link ExitCodes#OK} when the file is already formatted.
     *
     * <p>Color is enabled automatically when a terminal is attached ({@code System.console() != null}).
     *
     * @param path the file path to diff
     * @param kc   the keyword case mode to apply during formatting
     * @return {@link ExitCodes#OK} when already formatted, {@link ExitCodes#WARNING} otherwise
     * @throws IOException if the file cannot be read
     */
    private Integer runDiffMode(String path, KeywordCase kc) throws IOException {
        String diffOutput = diffFormatted(path, kc);
        if (diffOutput == null) {
            return ExitCodes.OK;
        }
        System.out.print(diffOutput);
        return ExitCodes.WARNING;
    }

    private void processDirectoryFiles(Path baseDir, List<Path> files, KeywordCase kc,
        Consumer<DirectoryFormatResult> consumer) throws IOException {
        int parallelism = directoryParallelism(files.size());
        if (parallelism <= 1) {
            for (Path file : files) {
                consumer.accept(formatDirectoryFile(baseDir, file, kc));
            }
            return;
        }

        ForkJoinPool pool = new ForkJoinPool(parallelism);
        try {
            ArrayDeque<Future<DirectoryFormatResult>> pending = new ArrayDeque<>();
            int nextFile = 0;
            while (nextFile < files.size() && pending.size() < parallelism) {
                Path file = files.get(nextFile++);
                pending.add(pool.submit(() -> formatDirectoryFile(baseDir, file, kc)));
            }
            while (!pending.isEmpty()) {
                consumer.accept(pending.remove().get());
                if (nextFile < files.size()) {
                    Path file = files.get(nextFile++);
                    pending.add(pool.submit(() -> formatDirectoryFile(baseDir, file, kc)));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Parallel directory formatting interrupted", e);
        } catch (ExecutionException e) {
            throw new IOException("Parallel directory formatting failed", e.getCause());
        } finally {
            pool.shutdown();
        }
    }

    private DirectoryFormatResult formatDirectoryFile(Path baseDir, Path file, KeywordCase kc) {
        String relative = baseDir.relativize(file.toAbsolutePath().normalize()).toString();
        try {
            if (this.diff) {
                String diffOutput = diffFormatted(file.toString(), kc);
                String output = "===== " + relative + " =====" + System.lineSeparator()
                    + (diffOutput == null ? "" : diffOutput);
                int exitCode = diffOutput == null ? ExitCodes.OK : ExitCodes.WARNING;
                return new DirectoryFormatResult(exitCode, output, "");
            }
            int exitCode = checkFormatted(file.toString(), kc);
            String err = exitCode == ExitCodes.WARNING && !isQuiet()
                ? "Would reformat: " + relative + System.lineSeparator()
                : "";
            return new DirectoryFormatResult(exitCode, "", err);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage()
                : e.getClass().getSimpleName();
            return new DirectoryFormatResult(ExitCodes.ERROR, "",
                "Error: failed to process " + relative + ": " + message
                    + System.lineSeparator());
        }
    }

    private int checkFormatted(String path, KeywordCase kc) throws IOException {
        String original = normalizeNewlines(SqlInput.readFileUtf8(path)).stripTrailing();
        String formattedStr = formattedFile(path, kc);
        if (!formattedStr.equals(original)) {
            return ExitCodes.WARNING;
        }
        return ExitCodes.OK;
    }

    private String diffFormatted(String path, KeywordCase kc) throws IOException {
        String original = normalizeNewlines(SqlInput.readFileUtf8(path)).stripTrailing();
        String formattedStr = formattedFile(path, kc);
        if (formattedStr.equals(original)) {
            return null;
        }
        boolean colorize = System.console() != null;
        return UnifiedDiff.compute(
            Arrays.asList(original.split("\n", -1)),
            Arrays.asList(formattedStr.split("\n", -1)),
            path,
            path,
            3,
            colorize);
    }

    private String formattedFile(String path, KeywordCase kc) throws IOException {
        StringBuilder formatted = new StringBuilder();
        SqlInput.forEachStatementFromFile(path, stmt -> {
            formatted.append(formatStatement(stmt, kc)).append(";\n");
        });
        return normalizeNewlines(formatted.toString()).stripTrailing();
    }

    /**
     * Normalises line endings to {@code \n} so that files with CRLF or CR endings
     * compare correctly against the formatter's {@code \n}-only output.
     *
     * @param input the string to normalise
     * @return the string with all line endings converted to {@code \n}
     */
    private static String normalizeNewlines(String input) {
        return input.replace("\r\n", "\n").replace("\r", "\n");
    }

    /**
     * Formats a single SQL statement, applying keyword-case and indent-size transformations,
     * and re-inserting inline and block comments from the original SQL.
     *
     * <p>Pipeline:
     * <ol>
     *   <li>Strip comments → parse → Trino-format (uppercase, 2-space indent)</li>
     *   <li>Apply keyword-case transformation</li>
     *   <li>Re-insert comments from original SQL</li>
     *   <li>Apply indent-size scaling</li>
     * </ol>
     *
     * @param sql the SQL statement to format (without delimiter)
     * @param kc  the keyword case mode
     * @return the formatted SQL statement
     */
    private String formatStatement(String sql, KeywordCase kc) {
        String cleanSql = CommentPreservingFormatter.stripComments(sql);
        Statement statement = SQL_PARSER.createStatement(cleanSql);
        String formattedSql = formatSql(statement);
        checkState(
            statement.equals(SQL_PARSER.createStatement(formattedSql)),
            "Formatted SQL is different than original");
        String cased = KeywordCaseTransformer.transform(formattedSql, sql, kc);
        String withComments = CommentPreservingFormatter.reinsert(cased, sql);
        return applyIndentSize(withComments, this.indentSize);
    }

    /**
     * Rescales the leading indentation of each line from Trino's 2-space baseline
     * to the requested indent size.
     *
     * <p>Only leading spaces are affected; other whitespace and content are unchanged.
     * Lines whose leading-space count is not a multiple of 2 retain their original indent.
     *
     * @param sql  the SQL string with 2-space indentation
     * @param size the desired number of spaces per indentation level
     * @return the SQL string with rescaled indentation
     */
    private static String applyIndentSize(String sql, int size) {
        if (size == 2) {
            return sql;
        }
        String[] lines = sql.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int spaces = 0;
            while (spaces < line.length() && line.charAt(spaces) == ' ') {
                spaces++;
            }
            int level = spaces / 2;
            int remainder = spaces % 2;
            String rest = line.substring(spaces);
            sb.append(" ".repeat(size * level + remainder)).append(rest);
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Emits a stderr warning for each line in {@code sql} that exceeds
     * {@link #maxLineLength} characters. Does nothing when {@code maxLineLength <= 0}.
     *
     * @param sql        the formatted SQL to inspect
     * @param sourceName a human-readable source name used in the warning message
     */
    private void warnLongLines(String sql, String sourceName) {
        if (this.maxLineLength <= 0) {
            return;
        }
        String[] lines = sql.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int len = lines[i].length();
            if (len > this.maxLineLength) {
                System.err.printf(
                    "Warning: line %d exceeds max-line-length %d (%d chars): %s%n",
                    i + 1, this.maxLineLength, len, sourceName);
            }
        }
    }

    /**
     * Returns {@code true} if verbose output is enabled via the parent command.
     *
     * @return true when verbose mode is active
     */
    private boolean isVerbose() {
        return this.entryCommand != null && this.entryCommand.isVerbose();
    }

    /**
     * Returns {@code true} if quiet mode is enabled via the parent command.
     *
     * @return true when quiet mode is active
     */
    private boolean isQuiet() {
        return this.entryCommand != null && this.entryCommand.isQuiet();
    }

    private int directoryParallelism(int fileCount) {
        if (fileCount <= 1) {
            return fileCount;
        }
        int processors = this.directoryParallelismOverride == null
            ? Runtime.getRuntime().availableProcessors()
            : this.directoryParallelismOverride;
        return Math.min(fileCount, Math.max(1, processors));
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
        FormatConfig formatConfig = loadedConfig.getConfig().getFormat();
        if (!isOptionMatched("--check") && formatConfig.getCheck() != null) {
            this.check = formatConfig.getCheck();
        }
        if (!isOptionMatched("--diff") && formatConfig.getDiff() != null) {
            this.diff = formatConfig.getDiff();
        }
        if (!isOptionMatched("--keyword-case") && formatConfig.getKeywordCase() != null) {
            this.keywordCase = formatConfig.getKeywordCase();
        }
    }

    private boolean isOptionMatched(String name) {
        return this.spec != null
            && this.spec.commandLine() != null
            && this.spec.commandLine().getParseResult() != null
            && this.spec.commandLine().getParseResult().hasMatchedOption(name);
    }

    // Package-private setters to support testing without reflection.

    void setSqlFile(String value) {
        this.sqlFile = value;
    }

    void setOutputPath(String value) {
        this.outputPath = value;
    }

    void setCheck(boolean value) {
        this.check = value;
    }

    void setKeywordCase(String value) {
        this.keywordCase = value;
    }

    void setIndentSize(int value) {
        this.indentSize = value;
    }

    void setMaxLineLength(int value) {
        this.maxLineLength = value;
    }

    void setDiff(boolean value) {
        this.diff = value;
    }

    void setDirPath(String value) {
        this.dirPath = value;
    }

    void setExcludePatterns(List<String> value) {
        this.excludePatterns = value;
    }

    void setSummary(boolean value) {
        this.summary = value;
    }

    void setDirectoryParallelismOverride(int value) {
        this.directoryParallelismOverride = value;
    }

    private record DirectoryFormatResult(int exitCode, String stdout, String stderr) {
    }
}
