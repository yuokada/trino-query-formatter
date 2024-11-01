package io.github.yuokada.core;

import io.trino.grammar.sql.SqlBaseLexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jboss.logging.Logger;

public class QueryLexer {

  static final Logger logger = Logger.getLogger(QueryLexer.class);

  public static void parse(String query) {
    CharStream input = CharStreams.fromString(query);
    SqlBaseLexer lexer = new SqlBaseLexer(input);
    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    tokenStream.fill();

    tokenStream.getTokens().forEach(token -> {
      int tokenType = token.getType();
      if (tokenType == SqlBaseLexer.SIMPLE_COMMENT || tokenType == SqlBaseLexer.BRACKETED_COMMENT) {
        logger.error(token.getText());
      }
    });
  }
}
