package io.github.yuokada.subcommand.util;

import com.google.common.collect.ImmutableSet;
import io.trino.cli.lexer.StatementSplitter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility for reading SQL statements either from a file or STDIN and splitting by delimiters.
 */
public final class SqlInput {

    /**
     * Utility class; prevent instantiation.
     */
    private SqlInput() {
    }

    /**
     * Delimiters used by the Trino StatementSplitter.
     */
    private static final ImmutableSet<String> DELIMITERS = ImmutableSet.of(";", "\\G");

    /**
     * A consumer that can throw IOException.
     */
    @FunctionalInterface
    public interface IoConsumer<T> {

        void accept(T value) throws IOException;
    }

    /**
     * A bi-consumer that can throw IOException.
     */
    @FunctionalInterface
    public interface IoBiConsumer<T, U> {

        void accept(T t, U u) throws IOException;
    }

    /**
     * Reads entire file content as UTF-8 string.
     *
     * @param path file path
     * @return file content
     * @throws IOException when file read fails
     */
    public static String readFileUtf8(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    /**
     * Iterates statements from a UTF-8 file, invoking the consumer for each complete statement.
     *
     * @param path     file path
     * @param consumer statement consumer that can throw IOException
     * @throws IOException when file read fails or consumer throws
     */
    public static void forEachStatementFromFile(String path, IoConsumer<String> consumer)
        throws IOException {
        String sql = readFileUtf8(path);
        StatementSplitter splitter = new StatementSplitter(sql, DELIMITERS);
        for (StatementSplitter.Statement split : splitter.getCompleteStatements()) {
            consumer.accept(split.statement());
        }
    }

    /**
     * Iterates statements from STDIN (UTF-8), invoking the consumer with 1-based index and SQL.
     * Handles partial trailing input as the last statement when EOF is reached.
     *
     * @param consumer bi-consumer receiving (index, statement) that can throw IOException
     * @throws IOException when IO fails or consumer throws
     */
    public static void forEachStatementFromStdin(IoBiConsumer<Integer, String> consumer)
        throws IOException {
        int queryCounter = 0;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line).append('\n');
                String sql = buffer.toString();
                StatementSplitter splitter = new StatementSplitter(sql, DELIMITERS);
                for (StatementSplitter.Statement split : splitter.getCompleteStatements()) {
                    queryCounter++;
                    consumer.accept(queryCounter, split.statement());
                }
                // create new buffer for trailing partial statement if any
                buffer = new StringBuilder();
                String partial = splitter.getPartialStatement();
                if (!partial.isEmpty()) {
                    buffer.append(partial).append('\n');
                }
            }
            String leftover = buffer.toString();
            if (!leftover.isEmpty()) {
                queryCounter++;
                consumer.accept(queryCounter, leftover);
            }
        }
    }
}

