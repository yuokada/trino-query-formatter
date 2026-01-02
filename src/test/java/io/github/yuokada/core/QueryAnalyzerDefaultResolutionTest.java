package io.github.yuokada.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for default catalog/schema resolution in QueryAnalyzer.analyze.
 */
class QueryAnalyzerDefaultResolutionTest {

    @Test
    void resolvesUnqualifiedTable_withCatalogAndSchema() {
        String sql = "SELECT * FROM t";
        QueryAnalysisResult r = QueryAnalyzer.analyze(sql, "c1", "s1");
        assertTrue(r.getTables().contains("c1.s1.t"));
        assertEquals(Set.of("c1"), r.getCatalogs());
    }

    @Test
    void resolvesSemiQualifiedTable_withCatalogOnly() {
        String sql = "SELECT * FROM s.t";
        QueryAnalysisResult r = QueryAnalyzer.analyze(sql, "c1", null);
        assertTrue(r.getTables().contains("c1.s.t"));
        assertEquals(Set.of("c1"), r.getCatalogs());
    }

    @Test
    void keepsFullyQualifiedTable_unchanged() {
        String sql = "SELECT * FROM c2.s2.t2";
        QueryAnalysisResult r = QueryAnalyzer.analyze(sql, "c1", "s1");
        assertTrue(r.getTables().contains("c2.s2.t2"));
        assertEquals(Set.of("c2"), r.getCatalogs());
    }

    @Test
    void resolvesWriteTarget_forInsert() {
        String sql = "INSERT INTO s.out SELECT * FROM t";
        QueryAnalysisResult r = QueryAnalyzer.analyze(sql, "c1", "s1");
        assertTrue(r.getWriteTargets().contains("c1.s.out"));
        assertTrue(r.getTables().contains("c1.s1.t"));
        assertEquals(Set.of("c1"), r.getCatalogs());
    }

    @Test
    void resolvesWriteTarget_forCreateTableAsSelect() {
        String sql = "CREATE TABLE out AS SELECT 1";
        QueryAnalysisResult r = QueryAnalyzer.analyze(sql, "c1", "s1");
        assertTrue(r.getWriteTargets().contains("c1.s1.out"));
        assertEquals(Set.of("c1"), r.getCatalogs());
    }
}
