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
