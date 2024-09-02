package io.github.yuokada.core;

import com.google.common.collect.ImmutableSet;
import io.trino.cli.lexer.DelimiterLexer;
import io.trino.grammar.sql.SqlBaseParser;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.CreateCatalog;
import io.trino.sql.tree.CreateTable;
import io.trino.sql.tree.Delete;
import io.trino.sql.tree.DropCatalog;
import io.trino.sql.tree.DropTable;
import io.trino.sql.tree.Insert;
import io.trino.sql.tree.Node;
import io.trino.sql.tree.QualifiedName;
import io.trino.sql.tree.Query;
import io.trino.sql.tree.ShowCatalogs;
import io.trino.sql.tree.Statement;
import io.trino.sql.tree.Table;
import io.trino.sql.tree.Update;
import java.util.HashSet;
import java.util.Set;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import org.jboss.logging.Logger;

public class QueryAnalyzer {

  static final Logger logger = Logger.getLogger(QueryAnalyzer.class);

  private static final SqlParser sqlParser = new SqlParser();

  private static Set<String> extractCatalogs(Node node) {
    Set<String> catalogs = new HashSet<>();
    if (node instanceof Table table) {
      QualifiedName name = table.getName();
      if (name.getPrefix().isPresent()) {
        catalogs.add(name.getPrefix().get().getOriginalParts().get(0).toString());
      }
    }
    for (Node child : node.getChildren()) {
      catalogs.addAll(extractCatalogs(child));
    }
    return catalogs;
  }

  public static Set<String> collectCatalogs(String sql) {
    Statement statement = sqlParser.createStatement(sql);
    Set<String> catalogs = new HashSet<>();
    for (Node child : statement.getChildren()) {
      catalogs.addAll(extractCatalogs(child));
    }
    return catalogs;
  }

  public static void dumpToken(String sql) {
    CharStream stream = CharStreams.fromString(sql);
    TokenSource lexer = new DelimiterLexer(stream, ImmutableSet.of(";", "\\G"));
    var toContinue = true;
    while (toContinue) {
      Token token = lexer.nextToken();
      logger.debug("Token: " + token);
      if (token.getType() == Token.EOF) {
        toContinue = false;
      } else if (token.getType() == SqlBaseParser.DELIMITER) {
        System.out.println(token.getText());
      } else if (token.getType() == SqlBaseParser.EMPTY
                 || token.getType() == SqlBaseParser.COMMENT
                 || token.getType() == SqlBaseParser.WS) {
        // Do nothing
        logger.debug("Skip: " + token);
      } else {
        // System.out.println("\"" + token + "\"" );
        System.out.println(token.getText());
      }
    }
  }

  public static String detectQueryType(String sql) {
    Statement statement = sqlParser.createStatement(sql);
    if (statement instanceof Query
        || statement instanceof Insert
        || statement instanceof Delete
        || statement instanceof Update) {
      return statement.getClass().getSimpleName();
    } else if (statement instanceof CreateTable) {
      return statement.getClass().getSimpleName();
    } else if (statement instanceof DropTable) {
      return statement.getClass().getSimpleName();
    } else if (statement instanceof ShowCatalogs
               || statement instanceof CreateCatalog
               || statement instanceof DropCatalog
    ) {
      return statement.getClass().getSimpleName();
    } else {
      return String.format("Unknown: %s", statement.getClass().getSimpleName());
    }
  }
}
