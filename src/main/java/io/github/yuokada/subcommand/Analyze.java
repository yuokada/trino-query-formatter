package io.github.yuokada.subcommand;

import com.google.common.collect.ImmutableSet;
import io.github.yuokada.EntryCommand;
import io.github.yuokada.core.QueryAnalyzer;
import io.trino.cli.lexer.StatementSplitter;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Expression;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@CommandLine.Command(name = "analyze", description = "Analyze SQL query")
public class Analyze implements Callable<Integer>, SubCommandUtil {

  /**
     * The parent command.
     */
    @ParentCommand
    private EntryCommand entryCommand;
    private static final SqlParser sqlParser = new SqlParser();

    /**
     * The file to analyze.
     */
    @Parameters(paramLabel = "<file>", defaultValue = "", description = "A query file.")
    private String sqlFile;

    /**
     * Output format. Supported: text, json.
     */
    @CommandLine.Option(names = {"--format"}, defaultValue = "text", description = "Output format: text|json")
    String format;

    /**
     * When true, also prints the AST of each statement.
     */
    @CommandLine.Option(names = {"--show-ast"}, defaultValue = "false", description = "Show AST for each statement")
    boolean showAst;

  @Override
  public Integer call() throws IOException {
    if (!sqlFile.isEmpty()) {
      String sql = readFromFile(sqlFile);
      StatementSplitter splitter = new StatementSplitter(sql, ImmutableSet.of(";", "\\G"));
      for (StatementSplitter.Statement split : splitter.getCompleteStatements()) {
        if (isJsonFormat()) {
          var result = QueryAnalyzer.analyze(split.statement());
          printJsonWithOptionalAst(result, split.statement());
        } else {
          Set<String> catalogs = QueryAnalyzer.collectCatalogs(split.statement());
          printResult(catalogs);
          if (showAst) {
            printAst(split.statement());
          }
        }
      }
    } else {
      Integer queryCounter = 0;
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
        StringBuilder buffer = new StringBuilder();
        while (reader.ready()) {
          buffer.append(reader.readLine()).append("\n");
          String sql = buffer.toString();
          StatementSplitter splitter = new StatementSplitter(sql, ImmutableSet.of(";", "\\G"));
          for (StatementSplitter.Statement split : splitter.getCompleteStatements()) {
            queryCounter++;
            if (isJsonFormat()) {
              var result = QueryAnalyzer.analyze(split.statement());
              printJsonWithOptionalAst(result, split.statement());
            } else {
              Set<String> catalogs = QueryAnalyzer.collectCatalogs(split.statement());
              printResult(catalogs, queryCounter);
              if (showAst) {
                printAst(split.statement());
              }
            }
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
          queryCounter++;
          if (isJsonFormat()) {
            var result = QueryAnalyzer.analyze(sql);
            printJsonWithOptionalAst(result, sql);
          } else {
            Set<String> catalogs = QueryAnalyzer.collectCatalogs(sql);
            printResult(catalogs, queryCounter);
            if (showAst) {
              printAst(sql);
            }
          }
        }
      }
    }
    return ExitCode.OK;
  }

  private String readFromFile(String path) throws IOException {
    return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)));
  }

  private static void printResult(Set<String> catalogs) {
    printResult(catalogs, null);
  }

  private static void printResult(Set<String> catalogs, Integer queryId) {
    System.out.println("=========================");
    if (catalogs.isEmpty()) {
      System.out.println("No catalogs found.");
    } else {
      if (queryId != null) {
        System.out.printf("Catalogs of Query No %d: [%s]\n", queryId, String.join(",", catalogs));
      } else {
        System.out.printf("Catalogs: [%s]\n", String.join(",", catalogs));
      }
    }
  }

  private static String analyze(String sql) {
    Expression expression = sqlParser.createExpression(sql);
    return expression.toString();
  }

  /**
   * Prints analysis in JSON format (one object per statement).
   */
  private void printJson(io.github.yuokada.core.QueryAnalysisResult result) {
    // For JSON output, avoid printing the header separator to keep it machine-readable
    System.out.println(result.toJson());
  }

  /**
   * Prints JSON analysis and optionally includes the AST.
   * When --show-ast is enabled, embeds the AST as a field in the same JSON object.
   */
  private void printJsonWithOptionalAst(io.github.yuokada.core.QueryAnalysisResult result, String sql) {
    if (!showAst) {
      printJson(result);
      return;
    }
    String ast = io.github.yuokada.core.QueryAnalyzer.dumpAst(sql).replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\"", "\\\"");
    String json = result.toJson();
    // Merge: {"ast":"...", <rest of fields from result>}
    if (json.startsWith("{")) {
      String body = json.substring(1); // remove leading '{'
      System.out.println("{\"ast\":\"" + ast + "\"," + body);
    } else {
      // Fallback: print two separate objects
      System.out.println("{\"ast\":\"" + ast + "\"}");
      System.out.println(json);
    }
  }

  /**
   * Prints AST in text mode.
   */
  private void printAst(String sql) {
    System.out.println("AST:");
    System.out.println(io.github.yuokada.core.QueryAnalyzer.dumpAst(sql));
  }

  /**
   * Checks if the selected output format is JSON.
   */
  private boolean isJsonFormat() {
    return "json".equalsIgnoreCase(format);
  }
}
