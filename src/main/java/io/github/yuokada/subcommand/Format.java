package io.github.yuokada.subcommand;

import static com.google.common.base.Preconditions.checkState;
import static io.trino.sql.SqlFormatter.formatSql;

import io.github.yuokada.EntryCommand;
import io.github.yuokada.core.CommentPreservingFormatter;
import io.github.yuokada.core.ExitCodes;
import io.github.yuokada.core.KeywordCaseTransformer;
import io.github.yuokada.core.KeywordCaseTransformer.KeywordCase;
import io.github.yuokada.subcommand.output.OutputEmitter;
import io.github.yuokada.subcommand.util.SqlInput;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Statement;
import java.io.IOException;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

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
     * Shared SQL parser instance.
     */
    private static final SqlParser SQL_PARSER = new SqlParser();

    /**
     * Input file path. Use {@code -} to read from stdin.
     */
    @Parameters(paramLabel = "<file>", defaultValue = "",
        description = "A query file. Use '-' for stdin.")
    private String sqlFile;

    /**
     * Output file path. When null or empty, writes to stdout.
     */
    @CommandLine.Option(names = {"--output"},
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

    @Override
    public Integer call() throws IOException {
        boolean isStdin = this.sqlFile.isEmpty() || this.sqlFile.equals("-");

        KeywordCase kc;
        try {
            kc = KeywordCase.fromString(this.keywordCase);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return ExitCodes.ERROR;
        }

        if (this.check && isStdin) {
            System.err.println("--check is not supported for stdin input");
            return ExitCodes.ERROR;
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
        String original = SqlInput.readFileUtf8(path).stripTrailing();
        StringBuilder formatted = new StringBuilder();
        SqlInput.forEachStatementFromFile(path, stmt -> {
            formatted.append(formatStatement(stmt, kc)).append(";\n");
        });
        String formattedStr = formatted.toString().stripTrailing();
        if (!formattedStr.equals(original)) {
            if (!isQuiet()) {
                System.err.println("Would reformat: " + path);
            }
            return ExitCodes.WARNING;
        }
        return ExitCodes.OK;
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
}
