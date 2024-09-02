package io.github.yuokada.core;

import com.google.common.collect.ImmutableSet;
import io.trino.cli.lexer.DelimiterLexer;
import io.trino.grammar.sql.SqlBaseParser;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Node;
import io.trino.sql.tree.QualifiedName;
import io.trino.sql.tree.Statement;
import io.trino.sql.tree.Table;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import org.jboss.logging.Logger;

public class QueryAnalyzer {
  static final Logger logger = Logger.getLogger(QueryAnalyzer.class);

  private static final SqlParser sqlParser = new SqlParser();

  private static List<String> extractCatalogs(Node node) {
    List<String> catalogs = new ArrayList<>();
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
    ANTLRInputStream stream = new ANTLRInputStream(sql);
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

}
