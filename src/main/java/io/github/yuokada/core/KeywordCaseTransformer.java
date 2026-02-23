package io.github.yuokada.core;

import com.google.common.collect.ImmutableSet;
import io.trino.cli.lexer.DelimiterLexer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;

/**
 * Utility class for applying keyword case transformations to formatted SQL.
 *
 * <p>Trino's {@code SqlFormatter} always outputs SQL keywords in uppercase with no
 * configuration knob. This class post-processes the formatter's output to apply
 * {@code UPPER} (no-op), {@code LOWER}, or {@code KEEP} (restore original casing)
 * transformations to SQL keyword tokens while leaving identifiers, literals, and
 * comments untouched.
 */
public final class KeywordCaseTransformer {

    /**
     * The keyword case transformation mode.
     */
    public enum KeywordCase {

        /** Keep all SQL keywords in UPPER CASE (Trino's default, no-op). */
        UPPER,

        /** Transform all SQL keywords to lower case. */
        LOWER,

        /** Restore the original keyword casing from the input SQL. */
        KEEP;

        /**
         * Parses a {@code KeywordCase} from a CLI string value (case-insensitive).
         *
         * @param value the string to parse ({@code "upper"}, {@code "lower"}, or {@code "keep"})
         * @return the corresponding {@code KeywordCase}
         * @throws IllegalArgumentException if the value is not recognised
         */
        public static KeywordCase fromString(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "upper" -> UPPER;
                case "lower" -> LOWER;
                case "keep" -> KEEP;
                default -> throw new IllegalArgumentException(
                    "Invalid --keyword-case value: '" + value + "'. Use upper, lower, or keep.");
            };
        }
    }

    /**
     * Comprehensive set of Trino/ANSI SQL keywords (stored in uppercase).
     * Common aggregate/scalar function names (COUNT, MAX, etc.) are intentionally
     * excluded because they are identifiers in the AST and are case-preserved by Trino.
     */
    private static final Set<String> SQL_KEYWORDS = ImmutableSet.of(
        // Query structure
        "SELECT", "FROM", "WHERE", "GROUP", "BY", "ORDER", "HAVING",
        "LIMIT", "OFFSET", "FETCH", "NEXT", "ONLY", "TIES",
        // Joins
        "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS",
        "NATURAL", "ON", "USING", "LATERAL",
        // Logical operators
        "AND", "OR", "NOT", "IN", "IS", "NULL", "LIKE", "BETWEEN", "ESCAPE",
        // Set operations
        "UNION", "INTERSECT", "EXCEPT", "ALL", "DISTINCT",
        // Aliases
        "AS",
        // CTEs
        "WITH", "RECURSIVE",
        // Sorting
        "ASC", "DESC", "NULLS", "FIRST", "LAST",
        // Conditionals
        "CASE", "WHEN", "THEN", "ELSE", "END",
        // DML
        "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "MERGE", "MATCHED",
        // DDL
        "CREATE", "TABLE", "DROP", "ALTER", "ADD", "COLUMN",
        "VIEW", "SCHEMA", "IF", "EXISTS", "RENAME", "TO", "COMMENT",
        "PROPERTIES", "TRUNCATE",
        // Data types
        "BOOLEAN", "TINYINT", "SMALLINT", "INTEGER", "INT", "BIGINT",
        "REAL", "DOUBLE", "FLOAT", "DECIMAL", "NUMERIC",
        "VARCHAR", "CHAR", "VARBINARY", "DATE", "TIME", "TIMESTAMP", "INTERVAL",
        "ARRAY", "MAP", "ROW", "JSON",
        // Cast / type conversion
        "CAST", "TRY",
        // Literals
        "TRUE", "FALSE",
        // Window functions
        "OVER", "PARTITION", "ROWS", "RANGE", "GROUPS",
        "PRECEDING", "FOLLOWING", "CURRENT", "ROW", "UNBOUNDED", "FILTER",
        // Grouping extensions
        "GROUPING", "SETS", "ROLLUP", "CUBE",
        // Subqueries
        "EXISTS", "ANY", "SOME",
        // String / type keywords
        "TRIM", "LEADING", "TRAILING", "BOTH",
        // Date/time parts
        "YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND",
        // Timezone
        "AT", "ZONE",
        // Other Trino keywords
        "TABLESAMPLE", "BERNOULLI", "SYSTEM", "UNNEST", "ORDINALITY",
        "SHOW", "DESCRIBE", "EXPLAIN", "ANALYZE",
        "CALL", "GRANT", "REVOKE",
        "COMMIT", "ROLLBACK", "START", "TRANSACTION",
        "PREPARE", "EXECUTE", "DEALLOCATE",
        "RETURNS", "RETURN", "FUNCTION", "LANGUAGE"
    );

    private KeywordCaseTransformer() {}

    /**
     * Applies the requested keyword-case transformation to {@code formattedSql}.
     *
     * @param formattedSql the formatted SQL string (Trino output — keywords uppercase)
     * @param originalSql  the original SQL string before formatting (needed for KEEP mode)
     * @param mode         the transformation to apply
     * @return the SQL with keywords in the requested case
     */
    public static String transform(
            String formattedSql, String originalSql, KeywordCase mode) {
        if (mode == KeywordCase.UPPER) {
            return formattedSql;
        }
        if (mode == KeywordCase.LOWER) {
            return applyLower(formattedSql);
        }
        return applyKeep(formattedSql, originalSql);
    }

    /**
     * Lowercases all SQL keyword tokens in the given SQL string.
     * Non-keyword tokens (identifiers, literals, whitespace, comments) are unchanged.
     *
     * @param sql the SQL to transform
     * @return the SQL with keyword tokens in lowercase
     */
    private static String applyLower(String sql) {
        List<Token> tokens = collectTokens(sql);
        StringBuilder sb = new StringBuilder();
        for (Token token : tokens) {
            String text = token.getText();
            if (isKeyword(text)) {
                sb.append(text.toLowerCase(Locale.ROOT));
            } else {
                sb.append(text);
            }
        }
        return sb.toString();
    }

    /**
     * Restores original keyword casing by mapping each formatted SQL token to its
     * corresponding original token using occurrence-based (case-insensitive) matching.
     *
     * <p>For each keyword token in the formatted output, the algorithm finds the
     * k-th occurrence of the same text in the original SQL (where k is the count of
     * prior occurrences in the formatted SQL), and uses the original token's text.
     * If no match is found (e.g. a keyword added by the formatter), the formatted
     * casing is retained.
     *
     * @param formattedSql the formatted SQL (uppercase keywords)
     * @param originalSql  the original SQL (user-supplied keyword casing)
     * @return the formatted SQL with keyword casing restored to the original
     */
    private static String applyKeep(String formattedSql, String originalSql) {
        List<String> origSqlTokenTexts = getSqlTokenTexts(originalSql);
        List<Token> fmtRawTokens = collectTokens(formattedSql);

        // For each raw token position, track how many times the same text appeared
        // before it among SQL tokens in the formatted SQL (occurrence-before index).
        Map<String, Integer> seenCountMap = new HashMap<>();
        int[] occurrenceBeforeArr = new int[fmtRawTokens.size()];
        for (int i = 0; i < fmtRawTokens.size(); i++) {
            Token t = fmtRawTokens.get(i);
            if (isSqlToken(t)) {
                String upper = t.getText().toUpperCase(Locale.ROOT);
                int cnt = seenCountMap.getOrDefault(upper, 0);
                occurrenceBeforeArr[i] = cnt;
                seenCountMap.put(upper, cnt + 1);
            }
        }

        // Rebuild the SQL, replacing keyword token text with the original casing.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fmtRawTokens.size(); i++) {
            Token t = fmtRawTokens.get(i);
            String text = t.getText();
            if (isSqlToken(t) && isKeyword(text)) {
                String origText = findOriginalToken(
                    origSqlTokenTexts, text, occurrenceBeforeArr[i]);
                sb.append(origText != null ? origText : text);
            } else {
                sb.append(text);
            }
        }
        return sb.toString();
    }

    /**
     * Finds the {@code occurrence}-th match (0-indexed) of {@code text} (case-insensitive)
     * in {@code origTokens} and returns that entry's original text.
     * Returns {@code null} if no match is found.
     *
     * @param origTokens the list of original SQL token texts
     * @param text       the text to search for (case-insensitively)
     * @param occurrence the 0-based occurrence index to return
     * @return the matched original token text, or {@code null}
     */
    private static String findOriginalToken(
            List<String> origTokens, String text, int occurrence) {
        int count = 0;
        for (String orig : origTokens) {
            if (orig.equalsIgnoreCase(text)) {
                if (count == occurrence) {
                    return orig;
                }
                count++;
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if the token text is a SQL keyword.
     *
     * @param text the token text to test
     * @return true if the text belongs to the SQL keyword set
     */
    private static boolean isKeyword(String text) {
        return text != null && SQL_KEYWORDS.contains(text.toUpperCase(Locale.ROOT));
    }

    /**
     * Returns {@code true} if the token is a meaningful SQL token
     * (not EOF, whitespace, or a comment).
     *
     * @param token the token to test
     * @return true if the token is a SQL keyword, identifier, literal, or operator
     */
    private static boolean isSqlToken(Token token) {
        if (token.getType() == Token.EOF) {
            return false;
        }
        String text = token.getText();
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (text.startsWith("--") || text.startsWith("/*")) {
            return false;
        }
        return !text.isBlank();
    }

    /**
     * Returns the texts of all SQL tokens (non-whitespace, non-comment) in the given SQL.
     *
     * @param sql the SQL string to tokenize
     * @return ordered list of SQL token texts
     */
    private static List<String> getSqlTokenTexts(String sql) {
        List<String> texts = new ArrayList<>();
        for (Token token : collectTokens(sql)) {
            if (isSqlToken(token)) {
                texts.add(token.getText());
            }
        }
        return texts;
    }

    /**
     * Collects all tokens from the given SQL string using {@link DelimiterLexer}.
     * EOF is excluded from the returned list.
     *
     * @param sql the SQL string to tokenize
     * @return list of all tokens (excluding EOF)
     */
    private static List<Token> collectTokens(String sql) {
        List<Token> tokens = new ArrayList<>();
        org.antlr.v4.runtime.CharStream stream = CharStreams.fromString(sql);
        TokenSource lexer = new DelimiterLexer(stream, ImmutableSet.of(";", "\\G"));
        Token token;
        while ((token = lexer.nextToken()).getType() != Token.EOF) {
            tokens.add(token);
        }
        return tokens;
    }
}
