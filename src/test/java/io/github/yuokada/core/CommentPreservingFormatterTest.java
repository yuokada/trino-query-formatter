package io.github.yuokada.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommentPreservingFormatterTest {

    @Test
    void testStripComments_noComments() {
        String sql = "SELECT id FROM foo";
        String result = CommentPreservingFormatter.stripComments(sql);
        // No comments: content is preserved (whitespace may differ slightly)
        assertTrue(result.contains("SELECT"));
        assertTrue(result.contains("id"));
        assertTrue(result.contains("FROM"));
        assertTrue(result.contains("foo"));
        assertFalse(result.contains("--"));
    }

    @Test
    void testStripComments_lineComment() {
        String sql = "SELECT -- my comment\nid FROM foo";
        String result = CommentPreservingFormatter.stripComments(sql);
        assertFalse(result.contains("--"), "Line comment should be stripped");
        assertFalse(result.contains("my comment"), "Comment text should be removed");
        assertTrue(result.contains("SELECT"));
        assertTrue(result.contains("id"));
    }

    @Test
    void testStripComments_blockComment() {
        String sql = "SELECT /* hint */ id FROM foo";
        String result = CommentPreservingFormatter.stripComments(sql);
        assertFalse(result.contains("/*"), "Block comment should be stripped");
        assertFalse(result.contains("hint"), "Comment text should be removed");
        assertTrue(result.contains("SELECT"));
        assertTrue(result.contains("id"));
    }

    @Test
    void testReinsert_noComments() {
        String formatted = "SELECT\n  id\nFROM\n  foo";
        String original = "SELECT id FROM foo";
        String result = CommentPreservingFormatter.reinsert(formatted, original);
        assertEquals(formatted, result, "No comments: formatted SQL should be returned unchanged");
    }

    @Test
    void testReinsert_trailingLineComment() {
        String original = "SELECT -- my comment\nid FROM foo";
        String formatted = "SELECT\n  id\nFROM\n  foo";
        String result = CommentPreservingFormatter.reinsert(formatted, original);
        assertTrue(result.contains("-- my comment"), "Trailing comment should be re-inserted");
        // Comment should appear on the SELECT line
        String selectLine = result.lines()
            .filter(l -> l.trim().startsWith("SELECT"))
            .findFirst().orElse("");
        assertTrue(selectLine.contains("-- my comment"),
            "Trailing comment should be on SELECT line, got: " + result);
    }

    @Test
    void testReinsert_leadingBlockComment() {
        String original = "/* copyright */\nSELECT id FROM foo";
        String formatted = "SELECT\n  id\nFROM\n  foo";
        String result = CommentPreservingFormatter.reinsert(formatted, original);
        assertTrue(result.contains("/* copyright */"), "Leading block comment should be preserved");
        assertTrue(result.startsWith("/* copyright */"), "Leading comment should be at the start");
    }

    @Test
    void testReinsert_inlineBlockComment() {
        String original = "SELECT /*+ BROADCAST(t2) */ id FROM foo";
        String formatted = "SELECT\n  id\nFROM\n  foo";
        String result = CommentPreservingFormatter.reinsert(formatted, original);
        assertTrue(result.contains("/*+ BROADCAST(t2) */"),
            "Inline block comment should be preserved");
    }

    @Test
    void testReinsert_standaloneBlockComment() {
        String original = "SELECT id,\n/* block */\nname FROM foo";
        String formatted = "SELECT\n  id,\n  name\nFROM\n  foo";
        String result = CommentPreservingFormatter.reinsert(formatted, original);
        assertTrue(result.contains("/* block */"),
            "Standalone block comment should be preserved");
    }

    @Test
    void testReinsert_multipleComments() {
        String original = "/* lead */ SELECT -- sel\nid FROM foo -- from";
        String formatted = "SELECT\n  id\nFROM\n  foo";
        String result = CommentPreservingFormatter.reinsert(formatted, original);
        assertTrue(result.contains("/* lead */"), "Leading comment should appear");
        assertTrue(result.contains("-- sel"), "Trailing SELECT comment should appear");
        assertTrue(result.contains("-- from"), "Trailing FROM comment should appear");
    }

    @Test
    void testReinsert_cteWithLineComment() {
        // CTE with a trailing comment on the SELECT inside the CTE
        String original = "WITH cte AS (\n  SELECT -- cte comment\n  id FROM base\n)\nSELECT id FROM cte";
        String result = formatWithComments(original);
        assertTrue(result.contains("-- cte comment"), "CTE trailing comment should be preserved");
        assertTrue(result.contains("WITH"), "WITH keyword should be present");
        assertTrue(result.contains("cte"), "CTE name should be present");
    }

    @Test
    void testReinsert_multipleJoinsWithComments() {
        // Multiple JOINs each with a trailing comment
        String original =
            "SELECT t1.id, t2.name, t3.val\n"
            + "FROM t1\n"
            + "JOIN t2 ON t1.id = t2.id -- join t2\n"
            + "LEFT JOIN t3 ON t1.id = t3.id -- join t3\n";
        String result = formatWithComments(original);
        assertTrue(result.contains("-- join t2"), "First JOIN comment should be preserved");
        assertTrue(result.contains("-- join t3"), "Second JOIN comment should be preserved");
        assertTrue(result.contains("JOIN"), "JOIN keyword should be present");
        assertTrue(result.contains("LEFT JOIN"), "LEFT JOIN keyword should be present");
    }

    @Test
    void testReinsert_windowFunctionWithHint() {
        // Inline block comment (query hint) before a window function expression
        String original = "SELECT /*+ BROADCAST(t2) */ id, ROW_NUMBER() OVER (PARTITION BY grp ORDER BY id) AS rn FROM t";
        String result = formatWithComments(original);
        assertTrue(result.contains("/*+ BROADCAST(t2) */"), "Window hint comment should be preserved");
        assertTrue(result.contains("ROW_NUMBER"), "Window function should be present");
        assertTrue(result.contains("PARTITION"), "PARTITION BY should be present");
    }

    @Test
    void testReinsert_unionWithLeadingBlockComment() {
        // UNION query where first branch has a leading comment
        String original =
            "/* first */ SELECT id FROM a\n"
            + "UNION ALL\n"
            + "SELECT id FROM b";
        String result = formatWithComments(original);
        assertTrue(result.contains("/* first */"), "Leading block comment should be preserved");
        assertTrue(result.contains("UNION ALL"), "UNION ALL should be present");
    }

    @Test
    void testReinsert_denseComments() {
        // Two adjacent comments with no SQL token between them — only the first anchors
        String original = "SELECT /* c1 */ /* c2 */ id FROM t";
        String result = formatWithComments(original);
        // Both block comments should survive
        assertTrue(result.contains("/* c1 */"), "First dense comment should be preserved");
        assertTrue(result.contains("/* c2 */"), "Second dense comment should be preserved");
    }

    @Test
    void testIdempotent_cte() {
        // CTE query should be idempotent
        String original = "WITH cte AS (SELECT id FROM base WHERE flag = true)\nSELECT id FROM cte";
        String formatted1 = formatWithComments(original);
        String formatted2 = formatWithComments(formatted1);
        assertEquals(formatted1, formatted2, "CTE formatting should be idempotent");
    }

    @Test
    void testIdempotent_lineComment() {
        // After formatting once and re-inserting comments, a second pass should
        // produce the same result (idempotent). No delimiter is included because
        // Format.java strips delimiters before calling format().
        String original = "SELECT -- note\nid FROM foo";
        String formatted1 = formatWithComments(original);
        String formatted2 = formatWithComments(formatted1);
        assertEquals(formatted1, formatted2);
    }

    // Helper: mirrors Format.format() for testing purposes
    private static String formatWithComments(String sql) {
        String cleanSql = CommentPreservingFormatter.stripComments(sql);
        io.trino.sql.parser.SqlParser parser = new io.trino.sql.parser.SqlParser();
        io.trino.sql.tree.Statement stmt = parser.createStatement(cleanSql);
        String formatted = io.trino.sql.SqlFormatter.formatSql(stmt);
        return CommentPreservingFormatter.reinsert(formatted, sql);
    }
}
