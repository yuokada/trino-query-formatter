package io.github.yuokada.subcommand;

import com.google.common.collect.ImmutableSet;
import io.trino.cli.lexer.StatementSplitter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * A utility interface for subcommands.
 */
public interface SubCommandUtil {
  default String readFromFile(String sqlFile) throws IOException {
    return String.join("\n", Files
        .readAllLines(Path.of(sqlFile), StandardCharsets.UTF_8));
  }

  default StatementSplitter toStatementSplitter(String sql) {
    return new StatementSplitter(sql, ImmutableSet.of(";", "\\G"));
  }

  static void printCatalogResult(Set<String> catalogs, Integer queryId) {
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

}
