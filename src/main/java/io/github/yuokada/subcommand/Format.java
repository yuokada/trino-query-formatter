package io.github.yuokada.subcommand;

import static com.google.common.base.Preconditions.checkState;
import static io.trino.sql.SqlFormatter.formatSql;

import com.google.common.collect.ImmutableSet;
import io.github.yuokada.EntryCommand;
import io.trino.cli.lexer.StatementSplitter;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Statement;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@CommandLine.Command(name = "format", description = "Format SQL query")
public class Format implements Callable<Integer>, SubCommandUtil {

    /**
     * The parent command.
     */
    @ParentCommand
    private EntryCommand entryCommand;
    private static final SqlParser sqlParser = new SqlParser();

    /**
     * The file to format.
     */
    @Parameters(paramLabel = "<file>", defaultValue = "", description = "A query file.")
    private String sqlFile;

    @Override
    public Integer call() throws IOException {
        if (!sqlFile.isEmpty()) {
            String sql = readFromFile(sqlFile);
            StatementSplitter splitter = new StatementSplitter(sql, ImmutableSet.of(";", "\\G"));
            for (StatementSplitter.Statement split : splitter.getCompleteStatements()) {
                String formatted = format(split.statement());
                System.out.println(formatted + ";");
            }
        } else {
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                StringBuilder buffer = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line).append('\n');
                    String sql = buffer.toString();
                    StatementSplitter splitter = new StatementSplitter(sql,
                        ImmutableSet.of(";", "\\G"));
                    for (StatementSplitter.Statement split : splitter.getCompleteStatements()) {
                        String formatted = format(split.statement());
                        System.out.println(formatted + ";");
                    }
                    // Replace buffer with trailing partial statement
                    buffer = new StringBuilder();
                    String partial = splitter.getPartialStatement();
                    if (!partial.isEmpty()) {
                        buffer.append(partial).append('\n');
                    }
                }
                String sql = buffer.toString();
                if (!sql.isEmpty()) {
                    String formatted = format(sql);
                    System.out.println(formatted + ";");
                }
            }
        }
        return ExitCode.OK;
    }

    private String readFromFile(String path) throws IOException {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }

    private static String format(String sql) {
        Statement statement = sqlParser.createStatement(sql);
        String formattedSql = formatSql(statement);
        checkState(
            statement.equals(sqlParser.createStatement(formattedSql)),
            "Formatted SQL is different than original");
        return formattedSql;
    }
}
