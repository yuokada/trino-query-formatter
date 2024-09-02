package io.github.yuokada.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class QueryAnalyzerTest {

  @Test
  void testCollectCatalogsWithSampleQuery1() {
    String sql = """
        SELECT * FROM catalog1.schema.tbl1
        """;
    assertEquals(Set.of("catalog1"), QueryAnalyzer.collectCatalogs(sql));
  }

  @Test
  void testCollectCatalogsWithSampleQuery2() {
    String sql = """
        SELECT
            TD_TIME_FORMAT(time, 'yyyy-MM-dd', 'JST')
            , COUNT(*) AS "ALL" -- sample comment
            , COUNT(*) FILTER (WHERE REGEXP_LIKE(message, 'io.trino.execution')) AS "Only io.trino.execution"
            , COUNT(*) FILTER (WHERE REGEXP_LIKE(message, 'io.trino.util')) AS "Only io.trino.util.CompilerUtils"
        FROM cat1.scm1.tb1
        WHERE TD_INTERVAL(time, '-7d')
        GROUP BY 1
        ORDER BY 1,2
        """;
    assertEquals(Set.of("cat1"), QueryAnalyzer.collectCatalogs(sql));
  }
}
