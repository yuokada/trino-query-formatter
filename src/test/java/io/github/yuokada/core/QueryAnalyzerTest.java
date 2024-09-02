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
  void testCollectCatalogsWithUnion() {
    String sql = """
        SELECT * FROM catalog1.schema.tbl1
        UNION ALL
        SELECT * FROM catalog2.schema.tbl1
        """;
    assertEquals(Set.of("catalog1", "catalog2"), QueryAnalyzer.collectCatalogs(sql));
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

  @Test
  void testCollectCatalogsWithSampleQuery3() {
    String sql = """
        CREATE TABLE cat1.db1.tbl1 AS SELECT
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

  @Test
  void testDetectQueryTypeForBasic() {
    String query = """
        SELECT * FROM catalog1.schema.tbl1
        UNION ALL
        SELECT * FROM catalog2.schema.tbl1
        """;
    assertEquals("Query", QueryAnalyzer.detectQueryType(query));

    query = "INSERT INTO foo SELECT * FROM catalog1.schema.tbl1";
    assertEquals("Insert", QueryAnalyzer.detectQueryType(query));

    query = "DELETE FROM catalog1.schema.tbl1 WHERE 1 = 1";
    assertEquals("Delete", QueryAnalyzer.detectQueryType(query));

    query = "Update catalog1.schema.tbl1 SET status = 'DONE' WHERE id = 1";
    assertEquals("Update", QueryAnalyzer.detectQueryType(query));
  }

  @Test
  void testDetectQueryTypeForCatalogQueries() {
    String query = "SHOW CATALOGS";
    assertEquals("ShowCatalogs", QueryAnalyzer.detectQueryType(query));

    query = "SHOW CATALOGS LIKE 'foo'";
    assertEquals("ShowCatalogs", QueryAnalyzer.detectQueryType(query));
  }
}
