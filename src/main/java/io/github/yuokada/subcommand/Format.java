package io.github.yuokada.subcommand;

import static com.google.common.base.Preconditions.checkState;
import static io.trino.sql.SqlFormatter.formatSql;

import io.github.yuokada.EntryCommand;
import io.github.yuokada.subcommand.util.SqlInput;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Statement;
import java.io.IOException;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@CommandLine.Command(name = "format", description = "Format SQL query")
public class Format implements Callable<Integer> {

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
            SqlInput.forEachStatementFromFile(sqlFile, stmt -> {
                String formatted = format(stmt);
                System.out.println(formatted + ";");
            });
        } else {
            SqlInput.forEachStatementFromStdin((idx, stmt) -> {
                String formatted = format(stmt);
                System.out.println(formatted + ";");
            });
        }
        return ExitCode.OK;
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
