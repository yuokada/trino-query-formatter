package io.github.yuokada.core;

import com.google.common.collect.ImmutableSet;
import io.trino.cli.lexer.DelimiterLexer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;

/**
 * Utility class for SQL comment extraction and re-insertion.
 *
 * <p>Trino's {@code SqlFormatter} operates on an AST that does not include
 * inline or block comments. This class extracts comments before formatting
 * and re-inserts them into the formatted output at the corresponding positions.
 *
 * <p>Comment positions are anchored to the last SQL token that precedes each
 * comment in the original SQL. Because Trino's formatter normalises the token
 * stream (e.g. removes explicit {@code AS} for column aliases), the re-insertion
 * uses occurrence-based matching: for anchor token {@code T}, we find its
 * k-th occurrence in the formatted token list, where k is the number of times
 * {@code T} appeared before the anchor in the original token list.
 */
public final class CommentPreservingFormatter {

    private CommentPreservingFormatter() {}

    /**
     * Strips all SQL comments from the given SQL string.
     * Each comment token is replaced by a single space to prevent adjacent
     * tokens from being joined after the comment is removed.
     *
     * @param sql the SQL string potentially containing comments
     * @return the SQL string with all comments replaced by spaces
     */
    public static String stripComments(String sql) {
        List<Token> tokens = collectTokens(sql);
        StringBuilder result = new StringBuilder();
        for (Token token : tokens) {
            if (isCommentToken(token)) {
                result.append(" ");
            } else {
                result.append(token.getText());
            }
        }
        return result.toString();
    }

    /**
     * Re-inserts comments from {@code originalSql} into {@code formattedSql}.
     *
     * <p>Each comment is anchored to the last SQL token that precedes it in
     * the original SQL. Trailing comments (on the same line as their anchor)
     * are appended to the end of the corresponding line in the formatted output.
     * Non-trailing block comments are inserted as new lines immediately before
     * the next logical section.
     *
     * @param formattedSql the formatted SQL string (without comments)
     * @param originalSql the original SQL string (with comments)
     * @return the formatted SQL with comments re-inserted
     */
    public static String reinsert(String formattedSql, String originalSql) {
        List<CommentEntry> comments = extractComments(originalSql);
        if (comments.isEmpty()) {
            return formattedSql;
        }

        List<String> origTokenTexts = getSqlTokenTexts(originalSql);
        List<TokenWithLine> fmtTokens = getSqlTokensWithLines(formattedSql);

        String[] lines = formattedSql.split("\n", -1);
        Map<Integer, List<String>> trailingByLine = new HashMap<>();
        Map<Integer, List<String>> leadingByLine = new HashMap<>();
        List<String> leadingComments = new ArrayList<>();

        for (CommentEntry entry : comments) {
            if (entry.anchorIndex < 0) {
                leadingComments.add(entry.text);
            } else {
                int fmtIdx = findFormattedTokenIndex(origTokenTexts, entry.anchorIndex, fmtTokens);
                if (fmtIdx >= 0) {
                    int anchorLine = fmtTokens.get(fmtIdx).lineNumber;
                    if (entry.isTrailing) {
                        trailingByLine.computeIfAbsent(anchorLine, k -> new ArrayList<>())
                            .add(entry.text);
                    } else {
                        leadingByLine.computeIfAbsent(anchorLine + 1, k -> new ArrayList<>())
                            .add(entry.text);
                    }
                }
            }
        }

        StringBuilder result = new StringBuilder();
        for (String comment : leadingComments) {
            result.append(comment).append("\n");
        }
        for (int i = 0; i < lines.length; i++) {
            if (leadingByLine.containsKey(i)) {
                String indent = extractIndent(lines[i]);
                for (String comment : leadingByLine.get(i)) {
                    result.append(indent).append(comment).append("\n");
                }
            }
            result.append(lines[i]);
            if (trailingByLine.containsKey(i)) {
                for (String comment : trailingByLine.get(i)) {
                    result.append(" ").append(comment);
                }
            }
            if (i < lines.length - 1) {
                result.append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Extracts all comment entries from the given SQL string, recording
     * the anchor index and trailing status for each comment.
     * Comment token text is stripped of trailing whitespace to handle lexers
     * that include the line-terminator within the comment token.
     *
     * @param sql the SQL string to analyze
     * @return an ordered list of comment entries
     */
    private static List<CommentEntry> extractComments(String sql) {
        List<CommentEntry> comments = new ArrayList<>();
        List<Token> tokens = collectTokens(sql);
        int sqlTokenIndex = 0;
        int lastSqlTokenLine = -1;

        for (Token token : tokens) {
            if (isCommentToken(token)) {
                boolean trailing = (sqlTokenIndex > 0)
                    && (token.getLine() == lastSqlTokenLine);
                // Strip any trailing newline that some lexers include in the comment text.
                String text = token.getText().stripTrailing();
                comments.add(new CommentEntry(sqlTokenIndex - 1, text, trailing));
            } else if (isSqlToken(token)) {
                lastSqlTokenLine = token.getLine();
                sqlTokenIndex++;
            }
        }
        return comments;
    }

    /**
     * Returns the texts of all SQL tokens in the given SQL string, in order.
     * Whitespace and comment tokens are excluded.
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
     * Returns all SQL tokens from the given SQL string together with their
     * 0-based line numbers. Whitespace and comment tokens are excluded.
     *
     * @param sql the SQL string to tokenize
     * @return ordered list of SQL tokens with their line numbers
     */
    private static List<TokenWithLine> getSqlTokensWithLines(String sql) {
        List<TokenWithLine> result = new ArrayList<>();
        for (Token token : collectTokens(sql)) {
            if (isSqlToken(token)) {
                result.add(new TokenWithLine(token.getText(), token.getLine() - 1));
            }
        }
        return result;
    }

    /**
     * Finds the index in {@code fmtTokens} that best corresponds to
     * {@code origTokenTexts[anchorIdx]}.
     *
     * <p>The match is performed by (text, occurrence-count): we find the
     * k-th occurrence of the anchor text in the formatted token list, where k
     * equals the number of times the anchor text appeared before {@code anchorIdx}
     * in the original token list. Comparison is case-insensitive to handle
     * keyword normalisation (e.g. {@code select} → {@code SELECT}).
     *
     * @param origTokenTexts SQL token texts from the original SQL
     * @param anchorIdx      index of the anchor token in {@code origTokenTexts}
     * @param fmtTokens      SQL tokens (with line numbers) from the formatted SQL
     * @return the matching index in {@code fmtTokens}, or {@code -1} if not found
     */
    private static int findFormattedTokenIndex(
        List<String> origTokenTexts, int anchorIdx, List<TokenWithLine> fmtTokens) {

        if (anchorIdx < 0 || anchorIdx >= origTokenTexts.size()) {
            return -1;
        }
        String anchorText = origTokenTexts.get(anchorIdx);

        int occurrencesBefore = 0;
        for (int i = 0; i < anchorIdx; i++) {
            if (origTokenTexts.get(i).equalsIgnoreCase(anchorText)) {
                occurrencesBefore++;
            }
        }

        int count = 0;
        for (int i = 0; i < fmtTokens.size(); i++) {
            if (fmtTokens.get(i).text.equalsIgnoreCase(anchorText)) {
                if (count == occurrencesBefore) {
                    return i;
                }
                count++;
            }
        }
        return -1;
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
        CharStream stream = CharStreams.fromString(sql);
        TokenSource lexer = new DelimiterLexer(stream, ImmutableSet.of(";", "\\G"));
        Token token;
        while ((token = lexer.nextToken()).getType() != Token.EOF) {
            tokens.add(token);
        }
        return tokens;
    }

    /**
     * Returns true if the given token is a SQL comment.
     * Detection is text-based: tokens whose text begins with {@code --} or {@code /*}
     * are treated as comments, regardless of the token type reported by the lexer.
     *
     * @param token the token to check
     * @return true if the token text looks like a comment
     */
    private static boolean isCommentToken(Token token) {
        String text = token.getText();
        return text != null && (text.startsWith("--") || text.startsWith("/*"));
    }

    /**
     * Returns true if the given token is a meaningful SQL token
     * (not EOF, whitespace, or a comment).
     *
     * @param token the token to check
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
        if (isCommentToken(token)) {
            return false;
        }
        return !text.isBlank();
    }

    /**
     * Extracts the leading whitespace (indentation) from a line.
     *
     * @param line the line to inspect
     * @return a string containing only the leading spaces and tabs
     */
    private static String extractIndent(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return line.substring(0, i);
    }

    /**
     * Internal representation of a comment and its position relative to SQL tokens.
     */
    private static final class CommentEntry {

        /** Index of the last SQL token before this comment, or -1 if before all tokens. */
        private final int anchorIndex;

        /** The full text of the comment, stripped of trailing whitespace. */
        private final String text;

        /** True if this comment is on the same line as its anchor token. */
        private final boolean isTrailing;

        private CommentEntry(int anchorIndex, String text, boolean isTrailing) {
            this.anchorIndex = anchorIndex;
            this.text = text;
            this.isTrailing = isTrailing;
        }
    }

    /**
     * A SQL token paired with its 0-based line number in the formatted output.
     */
    private static final class TokenWithLine {

        /** The token text (as returned by the lexer). */
        private final String text;

        /** The 0-based line number of this token in the formatted SQL string. */
        private final int lineNumber;

        private TokenWithLine(String text, int lineNumber) {
            this.text = text;
            this.lineNumber = lineNumber;
        }
    }
}
