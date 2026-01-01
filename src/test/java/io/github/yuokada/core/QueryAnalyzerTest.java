package io.github.yuokada.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class QueryAnalyzerTest {

    @Test
    void testCollectCatalogsWithSampleQuery1() {
        // Collect a single catalog from a simple SELECT
        String sql = """
            SELECT * FROM catalog1.schema.tbl1
            """;
        assertEquals(Set.of("catalog1"), QueryAnalyzer.collectCatalogs(sql));
    }

    @Test
    void testCollectCatalogsWithUnion() {
        // Collect catalogs across a UNION ALL
        String sql = """
            SELECT * FROM catalog1.schema.tbl1
            UNION ALL
            SELECT * FROM catalog2.schema.tbl1
            """;
        assertEquals(Set.of("catalog1", "catalog2"), QueryAnalyzer.collectCatalogs(sql));
    }

    @Test
    void testCollectCatalogsWithSampleQuery2() {
        // Collect catalog with functions, filters, and comments present
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
        // Collect catalog from a CTAS (CREATE TABLE AS SELECT) statement
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
        // Detect basic DML query types (SELECT/INSERT/DELETE/UPDATE)
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
        // Detect SHOW CATALOGS (with and without LIKE)
        String query = "SHOW CATALOGS";
        assertEquals("ShowCatalogs", QueryAnalyzer.detectQueryType(query));

        query = "SHOW CATALOGS LIKE 'foo'";
        assertEquals("ShowCatalogs", QueryAnalyzer.detectQueryType(query));
    }

    @Test
    void testCollectCatalogsWithJoin() {
        // Collect catalogs referenced across a JOIN
        String sql = """
            SELECT *
            FROM catalog1.schema.tbl1 t1
            JOIN catalog2.schema.tbl2 t2 ON t1.id = t2.id
            """;
        assertEquals(Set.of("catalog1", "catalog2"), QueryAnalyzer.collectCatalogs(sql));
    }

    @Test
    void testCollectCatalogsWithCteAndSubquery() {
        // Collect catalogs inside a CTE and an inline subquery
        String sql = """
            WITH a AS (
              SELECT * FROM catalog1.schema.t1
            )
            SELECT *
            FROM a
            JOIN (SELECT * FROM catalog2.schema.t2) b ON 1=1
            """;
        assertEquals(Set.of("catalog1", "catalog2"), QueryAnalyzer.collectCatalogs(sql));
    }

    @Test
    void testCollectCatalogsWithNoTable() {
        // No catalogs should be found when no table is referenced
        String sql = "SELECT 1";
        assertTrue(QueryAnalyzer.collectCatalogs(sql).isEmpty());
    }

    @Test
    void testDetectQueryTypeForTableDdls() {
        // Detect table-level DDL statements (CREATE/DROP TABLE)
        String query = "CREATE TABLE foo.bar (id int)";
        assertEquals("CreateTable", QueryAnalyzer.detectQueryType(query));

        query = "DROP TABLE foo.bar";
        assertEquals("DropTable", QueryAnalyzer.detectQueryType(query));
    }

    @Test
    void testDetectQueryTypeForCatalogDdls() {
        // Detect catalog-level DDL statements (CREATE/DROP CATALOG)
        String query = "CREATE CATALOG mycat USING tpch";
        assertEquals("CreateCatalog", QueryAnalyzer.detectQueryType(query));

        query = "DROP CATALOG mycat";
        assertEquals("DropCatalog", QueryAnalyzer.detectQueryType(query));
    }

    @Test
    void testDetectQueryTypeUnknownPath() {
        // Return Unknown for unsupported/other statement types
        String query = "CREATE VIEW v AS SELECT 1";
        assertEquals("Unknown: CreateView", QueryAnalyzer.detectQueryType(query));
    }
}
