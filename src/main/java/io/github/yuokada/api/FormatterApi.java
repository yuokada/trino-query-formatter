package io.github.yuokada.api;

import static com.google.common.base.Preconditions.checkState;
import static io.trino.sql.SqlFormatter.formatSql;

import io.github.yuokada.core.CommentPreservingFormatter;
import io.github.yuokada.core.KeywordCaseTransformer;
import io.github.yuokada.core.KeywordCaseTransformer.KeywordCase;
import io.trino.sql.parser.SqlParser;
import io.trino.sql.tree.Statement;

/**
 * Library-facing API for formatting a single SQL statement.
 */
public final class FormatterApi {

    /**
     * Shared parser instance.
     */
    private static final SqlParser SQL_PARSER = new SqlParser();

    private FormatterApi() {
    }

    /**
     * Formats one SQL statement using the same formatter pipeline as the CLI.
     *
     * @param sql the input SQL statement
     * @param keywordCase keyword case mode
     * @param indentSize spaces per indentation level
     * @return formatted SQL (without trailing semicolon)
     */
    public static String formatStatement(String sql, KeywordCase keywordCase, int indentSize) {
        String cleanSql = CommentPreservingFormatter.stripComments(sql);
        Statement statement = SQL_PARSER.createStatement(cleanSql);
        String formattedSql = formatSql(statement);
        checkState(
            statement.equals(SQL_PARSER.createStatement(formattedSql)),
            "Formatted SQL is different than original");
        String cased = KeywordCaseTransformer.transform(formattedSql, sql, keywordCase);
        String withComments = CommentPreservingFormatter.reinsert(cased, sql);
        return applyIndentSize(withComments, indentSize);
    }

    /**
     * Rescales indentation from 2-space baseline to requested size.
     *
     * @param sql formatted SQL
     * @param size indent size
     * @return SQL with adjusted indentation
     */
    public static String applyIndentSize(String sql, int size) {
        if (size == 2) {
            return sql;
        }
        String[] lines = sql.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int spaces = 0;
            while (spaces < line.length() && line.charAt(spaces) == ' ') {
                spaces++;
            }
            int level = spaces / 2;
            int remainder = spaces % 2;
            String rest = line.substring(spaces);
            sb.append(" ".repeat(size * level + remainder)).append(rest);
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
