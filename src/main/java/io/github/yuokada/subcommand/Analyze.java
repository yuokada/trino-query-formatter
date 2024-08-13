package io.github.yuokada.subcommand;

import com.google.common.collect.ImmutableSet;
import io.github.yuokada.EntryCommand;
import io.trino.cli.lexer.DelimiterLexer;
import io.trino.cli.lexer.StatementSplitter;
import io.trino.grammar.sql.SqlBaseParser;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Expression;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@CommandLine.Command(name = "analyze", description = "Analyze SQL query")
public class Analyze implements Callable<Integer> {

  @ParentCommand
  private EntryCommand entryCommand;
  private static SqlParser sqlParser = new SqlParser();

  @Parameters(paramLabel = "<file>", defaultValue = "", description = "A query file.")
  String sqlFile;

  @Override
  public Integer call() throws IOException {
    if (!sqlFile.isEmpty()) {
      String sql = readFromFile(sqlFile);
      dumpToken(sql);
    } else {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
        StringBuilder buffer = new StringBuilder();
        while (reader.ready()) {
          buffer.append(reader.readLine() + "\n");
          String sql = buffer.toString();
          StatementSplitter splitter = new StatementSplitter(sql, ImmutableSet.of(";", "\\G"));
          for (StatementSplitter.Statement split : splitter.getCompleteStatements()) {
            String formatted = analyze(split.statement());
            System.out.println(formatted + ";");
          }

          // replace buffer with trailing partial statement
          buffer = new StringBuilder();
          String partial = splitter.getPartialStatement();
          if (!partial.isEmpty()) {
            buffer.append(partial).append('\n');
          }
        }
        String sql = buffer.toString();
        if (!sql.isEmpty()) {
          String formatted = analyze(sql);
          System.out.println(formatted + ";");
        }
      }
    }
    return ExitCode.OK;
  }

  private static void dumpToken(String sql) {
    ANTLRInputStream stream = new ANTLRInputStream(sql);
    TokenSource lexer = new DelimiterLexer(stream, ImmutableSet.of(";", "\\G"));
    var toContinue = true;
    while (toContinue) {
      Token token = lexer.nextToken();
      if (token.getType() == Token.EOF) {
        toContinue = false;
      } else if (token.getType() == SqlBaseParser.DELIMITER) {
        System.out.println(token.getText());
      } else {
        System.out.println(token.getText());
      }
    }
  }

  private static String readFromFile(String sqlFile) throws IOException {
    return String.join("\n", Files
        .readAllLines(Path.of(sqlFile), StandardCharsets.UTF_8));
  }

  private static String analyze(String sql) {
    Expression expression = sqlParser.createExpression(sql);
    return expression.toString();
  }
}
