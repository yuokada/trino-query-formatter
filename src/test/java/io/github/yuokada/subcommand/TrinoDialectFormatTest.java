package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying that the formatter handles Trino-specific SQL dialect constructs
 * (TRY, UNNEST, MAP, ARRAY, LAMBDA, TRY_CAST, ROW, JSON functions, etc.) without errors.
 */
class TrinoDialectFormatTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final java.io.InputStream originalIn = System.in;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setIn(originalIn);
    }

    private String formatSql(String sql) throws IOException {
        outContent.reset();
        System.setIn(new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8)));
        Format format = new Format();
        format.setSqlFile("-");
        format.call();
        return outContent.toString(StandardCharsets.UTF_8).trim();
    }

    // ---- TRY() ---------------------------------------------------------------

    @Test
    void testTry_parsesWithoutError() throws IOException {
        String sql = "SELECT TRY(CAST(val AS INTEGER)) FROM t;";
        String result = formatSql(sql);
        assertTrue(result.contains("TRY"), "TRY() should appear in output: " + result);
    }

    @Test
    void testTryArithmetic() throws IOException {
        String sql = "SELECT TRY(1 / 0) AS safe_div FROM t;";
        String result = formatSql(sql);
        assertTrue(result.contains("TRY"), "TRY() should appear in formatted output: " + result);
    }

    // ---- TRY_CAST() ----------------------------------------------------------

    @Test
    void testTryCast_parsesWithoutError() throws IOException {
        String sql = "SELECT TRY_CAST(val AS BIGINT) FROM t;";
        String result = formatSql(sql);
        assertTrue(result.contains("TRY_CAST"), "TRY_CAST should appear in output: " + result);
        assertTrue(result.contains("BIGINT"), "Target type should appear: " + result);
    }

    @Test
    void testTryCast_withFilter() throws IOException {
        String sql = "SELECT id, TRY_CAST(amount AS DOUBLE) AS amt FROM orders WHERE id > 0;";
        String result = formatSql(sql);
        assertTrue(result.contains("TRY_CAST"), "TRY_CAST should be preserved: " + result);
    }

    // ---- UNNEST() ------------------------------------------------------------

    @Test
    void testUnnest_basic() throws IOException {
        String sql = "SELECT v FROM t CROSS JOIN UNNEST(arr) AS u(v);";
        String result = formatSql(sql);
        assertTrue(result.contains("UNNEST"), "UNNEST should appear in output: " + result);
    }

    @Test
    void testUnnest_withOrdinality() throws IOException {
        String sql = "SELECT v, idx FROM t CROSS JOIN UNNEST(arr) WITH ORDINALITY AS u(v, idx);";
        String result = formatSql(sql);
        assertTrue(result.contains("UNNEST"), "UNNEST should appear: " + result);
        assertTrue(result.contains("ORDINALITY"), "WITH ORDINALITY should appear: " + result);
    }

    @Test
    void testUnnest_multipleArrays() throws IOException {
        String sql = "SELECT a, b FROM UNNEST(ARRAY[1,2,3], ARRAY['x','y','z']) AS t(a, b);";
        String result = formatSql(sql);
        assertTrue(result.contains("UNNEST"), "UNNEST should appear: " + result);
    }

    // ---- ARRAY[] / ARRAY constructor -----------------------------------------

    @Test
    void testArrayConstructor() throws IOException {
        String sql = "SELECT ARRAY[1, 2, 3] AS nums;";
        String result = formatSql(sql);
        assertTrue(result.contains("ARRAY"), "ARRAY should appear: " + result);
    }

    @Test
    void testArrayAgg() throws IOException {
        String sql = "SELECT ARRAY_AGG(id ORDER BY id) AS ids FROM t GROUP BY cat;";
        String result = formatSql(sql);
        assertTrue(result.contains("ARRAY_AGG"), "ARRAY_AGG should appear: " + result);
    }

    @Test
    void testArrayContains() throws IOException {
        String sql = "SELECT * FROM t WHERE CONTAINS(tags, 'premium');";
        String result = formatSql(sql);
        assertDoesNotThrow(() -> formatSql(sql),
            "CONTAINS() should parse without error");
    }

    // ---- MAP() ---------------------------------------------------------------

    @Test
    void testMapConstructor() throws IOException {
        String sql = "SELECT MAP(ARRAY['a','b'], ARRAY[1,2]) AS m;";
        String result = formatSql(sql);
        assertTrue(result.contains("MAP"), "MAP should appear: " + result);
    }

    @Test
    void testMapAgg() throws IOException {
        String sql = "SELECT MAP_AGG(k, v) FROM t;";
        String result = formatSql(sql);
        assertTrue(result.contains("MAP_AGG"), "MAP_AGG should appear: " + result);
    }

    @Test
    void testMapFromEntries() throws IOException {
        String sql = "SELECT MAP_FROM_ENTRIES(ARRAY[ROW('a',1),ROW('b',2)]) AS m;";
        String result = formatSql(sql);
        assertTrue(result.contains("MAP_FROM_ENTRIES"), "MAP_FROM_ENTRIES should appear: " + result);
    }

    // ---- ROW() ---------------------------------------------------------------

    @Test
    void testRowConstructor() throws IOException {
        String sql = "SELECT ROW(1, 'hello', TRUE) AS r;";
        String result = formatSql(sql);
        assertTrue(result.contains("ROW"), "ROW should appear: " + result);
    }

    @Test
    void testRowWithCast() throws IOException {
        String sql = "SELECT CAST(ROW(1, 2.0) AS ROW(x INTEGER, y DOUBLE)) AS point;";
        String result = formatSql(sql);
        assertTrue(result.contains("ROW"), "ROW type should appear: " + result);
    }

    // ---- LAMBDA expressions --------------------------------------------------

    @Test
    void testLambda_filter() throws IOException {
        String sql = "SELECT FILTER(arr, x -> x > 0) FROM t;";
        String result = formatSql(sql);
        assertTrue(result.contains("FILTER"), "FILTER should appear: " + result);
        assertTrue(result.contains("->"), "Lambda arrow should appear: " + result);
    }

    @Test
    void testLambda_transform() throws IOException {
        String sql = "SELECT TRANSFORM(arr, x -> x * 2) AS doubled FROM t;";
        String result = formatSql(sql);
        assertTrue(result.contains("TRANSFORM"), "TRANSFORM should appear: " + result);
        assertTrue(result.contains("->"), "Lambda arrow should appear: " + result);
    }

    @Test
    void testLambda_reduce() throws IOException {
        String sql = "SELECT REDUCE(arr, 0, (acc, x) -> acc + x, acc -> acc) AS total FROM t;";
        String result = formatSql(sql);
        assertTrue(result.contains("REDUCE"), "REDUCE should appear: " + result);
    }

    @Test
    void testLambda_zip_with() throws IOException {
        String sql = "SELECT ZIP_WITH(a1, a2, (x, y) -> x + y) FROM t;";
        String result = formatSql(sql);
        assertTrue(result.contains("ZIP_WITH"), "ZIP_WITH should appear: " + result);
    }

    // ---- JSON functions ------------------------------------------------------

    @Test
    void testJsonFormat() throws IOException {
        String sql = "SELECT JSON_FORMAT(JSON '{\"key\":\"val\"}') AS j;";
        String result = formatSql(sql);
        assertTrue(result.contains("JSON_FORMAT"), "JSON_FORMAT should appear: " + result);
    }

    @Test
    void testJsonExtractScalar() throws IOException {
        String sql = "SELECT JSON_EXTRACT_SCALAR(payload, '$.id') AS id FROM events;";
        String result = formatSql(sql);
        assertTrue(result.contains("JSON_EXTRACT_SCALAR"),
            "JSON_EXTRACT_SCALAR should appear: " + result);
    }

    @Test
    void testJsonArrayContains() throws IOException {
        String sql = "SELECT * FROM t WHERE JSON_ARRAY_CONTAINS(tags, 'admin');";
        String result = formatSql(sql);
        assertTrue(result.contains("JSON_ARRAY_CONTAINS"),
            "JSON_ARRAY_CONTAINS should appear: " + result);
    }

    // ---- Type-cast / TYPE syntax ---------------------------------------------

    @Test
    void testIntervalLiteral() throws IOException {
        String sql = "SELECT * FROM t WHERE ts > NOW() - INTERVAL '7' DAY;";
        String result = formatSql(sql);
        assertTrue(result.contains("INTERVAL"), "INTERVAL should appear: " + result);
    }

    @Test
    void testTimestampLiteral() throws IOException {
        String sql = "SELECT TIMESTAMP '2024-01-01 00:00:00' AS epoch;";
        String result = formatSql(sql);
        assertTrue(result.contains("TIMESTAMP"), "TIMESTAMP literal should appear: " + result);
    }

    @Test
    void testDecimalLiteral() throws IOException {
        String sql = "SELECT DECIMAL '3.14' AS pi;";
        String result = formatSql(sql);
        assertTrue(result.contains("DECIMAL"), "DECIMAL literal should appear: " + result);
    }

    // ---- Window functions ----------------------------------------------------

    @Test
    void testWindowFunctionWithFrame() throws IOException {
        String sql = "SELECT id, SUM(amt) OVER ("
            + "PARTITION BY cat ORDER BY ts ROWS BETWEEN 6 PRECEDING AND CURRENT ROW"
            + ") AS rolling7 FROM t;";
        String result = formatSql(sql);
        assertTrue(result.contains("PARTITION BY"), "PARTITION BY should appear: " + result);
        assertTrue(result.contains("ROWS BETWEEN"), "ROWS BETWEEN should appear: " + result);
    }

    @Test
    void testNthValue() throws IOException {
        String sql = "SELECT NTH_VALUE(val, 3) OVER (ORDER BY ts) AS third FROM t;";
        String result = formatSql(sql);
        assertTrue(result.contains("NTH_VALUE"), "NTH_VALUE should appear: " + result);
    }

    // ---- GROUPING SETS / ROLLUP / CUBE ----------------------------------------

    @Test
    void testGroupingSets() throws IOException {
        String sql = "SELECT cat, sub, SUM(amt) FROM t GROUP BY GROUPING SETS ((cat, sub), (cat), ());";
        String result = formatSql(sql);
        assertTrue(result.contains("GROUPING SETS"), "GROUPING SETS should appear: " + result);
    }

    @Test
    void testRollup() throws IOException {
        String sql = "SELECT cat, sub, SUM(amt) FROM t GROUP BY ROLLUP (cat, sub);";
        String result = formatSql(sql);
        assertTrue(result.contains("ROLLUP"), "ROLLUP should appear: " + result);
    }

    @Test
    void testCube() throws IOException {
        String sql = "SELECT cat, sub, SUM(amt) FROM t GROUP BY CUBE (cat, sub);";
        String result = formatSql(sql);
        assertTrue(result.contains("CUBE"), "CUBE should appear: " + result);
    }

    // ---- TABLESAMPLE ---------------------------------------------------------

    @Test
    void testTableSampleBernoulli() throws IOException {
        String sql = "SELECT * FROM orders TABLESAMPLE BERNOULLI (10);";
        String result = formatSql(sql);
        assertTrue(result.contains("TABLESAMPLE"), "TABLESAMPLE should appear: " + result);
        assertTrue(result.contains("BERNOULLI"), "BERNOULLI should appear: " + result);
    }

    // ---- EXCEPT / INTERSECT --------------------------------------------------

    @Test
    void testExcept() throws IOException {
        String sql = "SELECT id FROM a EXCEPT SELECT id FROM b;";
        String result = formatSql(sql);
        assertTrue(result.contains("EXCEPT"), "EXCEPT should appear: " + result);
    }

    @Test
    void testIntersect() throws IOException {
        String sql = "SELECT id FROM a INTERSECT SELECT id FROM b;";
        String result = formatSql(sql);
        assertTrue(result.contains("INTERSECT"), "INTERSECT should appear: " + result);
    }

    // ---- Keywords remain uppercase by default --------------------------------

    @Test
    void testKeywordsUppercase_default() throws IOException {
        // Trino-parser formats type names in lowercase; SQL keywords (SELECT, FROM, AS) are upper.
        String sql = "select try_cast(v as integer) from t;";
        String result = formatSql(sql);
        // TRY_CAST and AS are SQL keywords → uppercased; 'integer' is a type name → formatter
        // emits lowercase. SELECT and FROM are keywords → uppercased.
        assertTrue(result.contains("SELECT"), "SELECT should be uppercased: " + result);
        assertTrue(result.contains("FROM"), "FROM should be uppercased: " + result);
        assertTrue(result.contains("TRY_CAST"), "TRY_CAST should be uppercased: " + result);
        assertTrue(result.contains("AS"), "AS should be uppercased: " + result);
    }
}
