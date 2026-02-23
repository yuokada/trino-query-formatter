package io.github.yuokada.subcommand;

import static com.google.common.base.Preconditions.checkState;
import static io.trino.sql.SqlFormatter.formatSql;

import io.github.yuokada.EntryCommand;
import io.github.yuokada.core.CommentPreservingFormatter;
import io.github.yuokada.core.ExitCodes;
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
     * The parent command.
     */
    @ParentCommand
    private EntryCommand entryCommand;

    /**
     * Shared SQL parser instance.
     */
    private static final SqlParser sqlParser = new SqlParser();

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

    @Override
    public Integer call() throws IOException {
        boolean isStdin = sqlFile.isEmpty() || sqlFile.equals("-");

        if (check && isStdin) {
            System.err.println("--check is not supported for stdin input");
            return ExitCodes.ERROR;
        }

        if (check) {
            return runCheckMode(sqlFile);
        }

        try (OutputEmitter emitter = new OutputEmitter(outputPath)) {
            if (isStdin) {
                SqlInput.forEachStatementFromStdin((idx, stmt) -> {
                    emitter.emit(format(stmt) + ";");
                });
            } else {
                SqlInput.forEachStatementFromFile(sqlFile, stmt -> {
                    emitter.emit(format(stmt) + ";");
                });
            }
        }
        return ExitCodes.OK;
    }

    /**
     * Compares the formatted output of {@code path} with its current contents.
     * Prints to stderr and returns {@link ExitCodes#WARNING} when differences are found.
     *
     * @param path the file path to check
     * @return {@link ExitCodes#OK} when already formatted, {@link ExitCodes#WARNING} otherwise
     * @throws IOException if the file cannot be read
     */
    private Integer runCheckMode(String path) throws IOException {
        String original = SqlInput.readFileUtf8(path).stripTrailing();
        StringBuilder formatted = new StringBuilder();
        SqlInput.forEachStatementFromFile(path, stmt -> {
            formatted.append(format(stmt)).append(";\n");
        });
        String formattedStr = formatted.toString().stripTrailing();
        if (!formattedStr.equals(original)) {
            System.err.println("Would reformat: " + path);
            return ExitCodes.WARNING;
        }
        return ExitCodes.OK;
    }

    /**
     * Formats a single SQL statement string, preserving inline and block comments.
     *
     * @param sql the SQL statement to format (without delimiter)
     * @return the formatted SQL statement
     */
    private static String format(String sql) {
        String cleanSql = CommentPreservingFormatter.stripComments(sql);
        Statement statement = sqlParser.createStatement(cleanSql);
        String formattedSql = formatSql(statement);
        checkState(
            statement.equals(sqlParser.createStatement(formattedSql)),
            "Formatted SQL is different than original");
        return CommentPreservingFormatter.reinsert(formattedSql, sql);
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
}
