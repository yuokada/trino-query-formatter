package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FormatTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
    }

    @Test
    void testCallWithFile(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("test.sql");
        String sql = "select * from foo;select * from bar;";
        Files.writeString(sqlFile, sql);

        Format format = new Format();
        try {
            java.lang.reflect.Field field = format.getClass().getDeclaredField("sqlFile");
            field.setAccessible(true);
            field.set(format, sqlFile.toString());
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
        format.call();

        String expectedOutput = """
            SELECT *
            FROM
              foo
            ;
            SELECT *
            FROM
              bar
            ;""".trim();
        assertEquals(expectedOutput, outContent.toString().trim());
    }

    @Test
    void testCallWithInvalidSql_throws(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("invalid.sql");
        // Invalid SQL (typo: SELEC)
        String sql = "SELEC * FROM foo;";
        Files.writeString(sqlFile, sql);

        Format format = new Format();
        try {
            java.lang.reflect.Field field = format.getClass().getDeclaredField("sqlFile");
            field.setAccessible(true);
            field.set(format, sqlFile.toString());
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }

        assertThrows(Exception.class, format::call);
    }

    @Test
    void testIdempotentFormatting_withLargeSqlAndComments(@TempDir Path tempDir)
        throws IOException {
        Path sqlFile = tempDir.resolve("large.sql");
        String sql = """
            /* leading block comment */
            SELECT /*+ BROADCAST(t2) */
              t1.id,
              t2.name,
              -- inline comment
              COUNT(*) AS cnt
            FROM catalog1.schema.table_one t1
            JOIN catalog2.schema.table_two t2 ON t1.id = t2.id /* join comment */
            WHERE t1.created_at >= DATE '2024-01-01' AND t2.status = 'ACTIVE'
            GROUP BY 1,2
            HAVING COUNT(*) > 0
            ORDER BY 1,2
            ;
            
            -- second statement with CTE and subquery
            WITH a AS (
              SELECT id FROM catalog1.schema.table_three WHERE flag = true
            )
            SELECT t1.id, (SELECT max(id) FROM catalog2.schema.table_four) AS max_id
            FROM a t1
            ;
            """;
        Files.writeString(sqlFile, sql);

        // 1st formatting
        Format format1 = new Format();
        try {
            java.lang.reflect.Field field = format1.getClass().getDeclaredField("sqlFile");
            field.setAccessible(true);
            field.set(format1, sqlFile.toString());
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
        format1.call();
        String first = outContent.toString().trim();

        // Use the first output as input for the second run
        Path formattedFile = tempDir.resolve("formatted.sql");
        Files.writeString(formattedFile, first + System.lineSeparator());

        // Reset the captured output before 2nd run
        outContent.reset();

        // 2nd formatting
        Format format2 = new Format();
        try {
            java.lang.reflect.Field field = format2.getClass().getDeclaredField("sqlFile");
            field.setAccessible(true);
            field.set(format2, formattedFile.toString());
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
        format2.call();
        String second = outContent.toString().trim();

        // Output should remain identical after reformatting (formatting idempotence)
        assertEquals(first, second);
    }
}
