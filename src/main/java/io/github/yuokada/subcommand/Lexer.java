package io.github.yuokada.subcommand;

import com.google.common.collect.ImmutableSet;
import io.github.yuokada.EntryCommand;
import io.github.yuokada.core.QueryAnalyzer;
import io.trino.cli.lexer.StatementSplitter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@CommandLine.Command(name = "lexer", description = "Lexer (experimental)")
public class Lexer implements Callable<Integer>, SubCommandUtil {

  @Parameters(paramLabel = "<file>", defaultValue = "", description = "A query file.")
  String sqlFile;
  @ParentCommand
  private EntryCommand entryCommand;

  private static void printResult(Set<String> catalogs) {
    SubCommandUtil.printCatalogResult(catalogs, null)   ;
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

  @Override
  public Integer call() throws IOException {
    if (!sqlFile.isEmpty()) {
      String sql = readFromFile(sqlFile);
      StatementSplitter splitter = toStatementSplitter(sql);
      for (StatementSplitter.Statement split : splitter.getCompleteStatements()) {
        Set<String> catalogs = QueryAnalyzer.collectCatalogs(split.statement());
        printResult(catalogs);
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
            Set<String> catalogs = QueryAnalyzer.collectCatalogs(split.statement());
            printResult(catalogs, queryCounter);
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
          Set<String> catalogs = QueryAnalyzer.collectCatalogs(sql);
          printResult(catalogs, queryCounter);
        }
      }
    }
    return ExitCode.OK;
  }
}
