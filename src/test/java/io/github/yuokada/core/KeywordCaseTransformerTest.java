package io.github.yuokada.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yuokada.core.KeywordCaseTransformer.KeywordCase;
import org.junit.jupiter.api.Test;

class KeywordCaseTransformerTest {

    @Test
    void upper_isNoOp() {
        String sql = "SELECT *\nFROM\n  foo\nWHERE id = 1";
        assertEquals(sql, KeywordCaseTransformer.transform(sql, sql, KeywordCase.UPPER));
    }

    @Test
    void lower_transformsKeywords() {
        String sql = "SELECT *\nFROM\n  foo";
        String result = KeywordCaseTransformer.transform(sql, sql, KeywordCase.LOWER);
        assertTrue(result.contains("select"), "SELECT should be lowercased");
        assertTrue(result.contains("from"), "FROM should be lowercased");
        assertFalse(result.contains("SELECT"), "No uppercase SELECT expected");
    }

    @Test
    void lower_preservesIdentifiers() {
        // TD_TIME_FORMAT is not in the keyword set, so it must be preserved.
        String sql = "SELECT TD_TIME_FORMAT(time, 'yyyy') FROM foo";
        String result = KeywordCaseTransformer.transform(sql, sql, KeywordCase.LOWER);
        assertTrue(result.contains("TD_TIME_FORMAT"),
            "Non-keyword identifier should be preserved");
        assertTrue(result.contains("select"), "SELECT should be lowercased");
        assertTrue(result.contains("from"), "FROM should be lowercased");
    }

    @Test
    void lower_preservesStringLiterals() {
        // String literal 'ACTIVE' must not be touched.
        String sql = "SELECT * FROM foo WHERE status = 'ACTIVE'";
        String result = KeywordCaseTransformer.transform(sql, sql, KeywordCase.LOWER);
        assertTrue(result.contains("'ACTIVE'"), "String literal should be preserved");
        assertTrue(result.contains("select"), "SELECT should be lowercased");
        assertTrue(result.contains("where"), "WHERE should be lowercased");
    }

    @Test
    void lower_preservesQuotedIdentifiers() {
        // Quoted identifier "ALL" must not be transformed.
        String sql = "SELECT COUNT(*) \"ALL\" FROM foo";
        String result = KeywordCaseTransformer.transform(sql, sql, KeywordCase.LOWER);
        assertTrue(result.contains("\"ALL\""), "Quoted identifier should be preserved");
    }

    @Test
    void lower_transformsWindowKeywords() {
        String sql = "SELECT id, RANK() OVER (PARTITION BY dept ORDER BY salary DESC) FROM foo";
        String result = KeywordCaseTransformer.transform(sql, sql, KeywordCase.LOWER);
        assertTrue(result.contains("over"), "OVER should be lowercased");
        assertTrue(result.contains("partition"), "PARTITION should be lowercased");
        assertTrue(result.contains("order"), "ORDER should be lowercased");
        assertTrue(result.contains("desc"), "DESC should be lowercased");
    }

    @Test
    void keep_restoresLowercaseOriginal() {
        // Original keywords are lowercase; formatted is uppercase; keep should restore lower.
        String formatted = "SELECT *\nFROM\n  foo\nWHERE id = 1";
        String original = "select * from foo where id = 1";
        String result = KeywordCaseTransformer.transform(formatted, original, KeywordCase.KEEP);
        assertTrue(result.contains("select"), "select should be restored");
        assertTrue(result.contains("from"), "from should be restored");
        assertTrue(result.contains("where"), "where should be restored");
        assertFalse(result.contains("SELECT"), "No uppercase SELECT expected");
    }

    @Test
    void keep_handlesMixedCaseOriginal() {
        String formatted = "SELECT *\nFROM\n  foo";
        String original = "Select * From foo";
        String result = KeywordCaseTransformer.transform(formatted, original, KeywordCase.KEEP);
        assertTrue(result.contains("Select"), "Select (mixed) should be restored");
        assertTrue(result.contains("From"), "From (mixed) should be restored");
    }

    @Test
    void keep_preservesNonKeywordCasing() {
        // Function name TD_TIME_FORMAT must not be altered by KEEP mode.
        String formatted = "SELECT TD_TIME_FORMAT(time, 'yyyy') FROM foo";
        String original = "select TD_TIME_FORMAT(time, 'yyyy') from foo";
        String result = KeywordCaseTransformer.transform(formatted, original, KeywordCase.KEEP);
        assertTrue(result.contains("TD_TIME_FORMAT"),
            "Non-keyword identifier casing should be unchanged");
    }

    @Test
    void fromString_parsesValidValues() {
        assertEquals(KeywordCase.UPPER, KeywordCase.fromString("upper"));
        assertEquals(KeywordCase.LOWER, KeywordCase.fromString("lower"));
        assertEquals(KeywordCase.KEEP, KeywordCase.fromString("keep"));
        assertEquals(KeywordCase.UPPER, KeywordCase.fromString("UPPER"));
    }

    @Test
    void fromString_throwsOnInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> KeywordCase.fromString("invalid"));
    }
}
