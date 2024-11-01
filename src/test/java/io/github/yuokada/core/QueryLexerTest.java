package io.github.yuokada.core;

import org.junit.jupiter.api.Test;

class QueryLexerTest {

  @Test
  void testCollectCatalogsWithSampleQuery1() {
    String sql = """
        -- @TD preview: feature_123
        SELECT * FROM catalog1.schema.tbl1
        """;
    QueryLexer.parse(sql);
  }

  @Test
  void testCollectCatalogsWithSampleQuery2() {
    String sql = """
        /*
          @TD preview: feature_123
        */
        SELECT * FROM catalog1.schema.tbl1
        """;
    QueryLexer.parse(sql);
  }

}
