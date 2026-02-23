package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yuokada.core.ExitCodes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
    void testCheckMode_alreadyFormatted(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("formatted.sql");
        // Write content that matches the formatter's output exactly
        String alreadyFormatted = "SELECT *\nFROM\n  foo\n;";
        Files.writeString(sqlFile, alreadyFormatted);

        Format format = new Format();
        format.setSqlFile(sqlFile.toString());
        format.setCheck(true);
        int exitCode = format.call();

        assertEquals(ExitCodes.OK, exitCode, "Already-formatted SQL should exit OK");
    }

    @Test
    void testCheckMode_needsReformat(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("unformatted.sql");
        Files.writeString(sqlFile, "select * from foo;");

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));
        try {
            Format format = new Format();
            format.setSqlFile(sqlFile.toString());
            format.setCheck(true);
            int exitCode = format.call();

            assertEquals(ExitCodes.WARNING, exitCode, "Unformatted SQL should exit WARNING");
            assertTrue(errContent.toString().contains("Would reformat"),
                "Should print 'Would reformat' to stderr");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void testOutputOption(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("input.sql");
        Path outFile = tempDir.resolve("output.sql");
        Files.writeString(sqlFile, "select * from foo;");

        Format format = new Format();
        format.setSqlFile(sqlFile.toString());
        format.setOutputPath(outFile.toString());
        format.call();

        // stdout should be empty
        assertEquals("", outContent.toString().trim(), "Nothing should be written to stdout");

        // output file should contain formatted SQL
        String output = Files.readString(outFile, StandardCharsets.UTF_8).trim();
        String expected = "SELECT *\nFROM\n  foo\n;".trim();
        assertEquals(expected, output);
    }

    @Test
    void testCommentPreservation_lineComment(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("comment.sql");
        Files.writeString(sqlFile, "SELECT -- my comment\nid FROM foo;");

        Format format = new Format();
        format.setSqlFile(sqlFile.toString());
        format.call();

        String output = outContent.toString().trim();
        assertTrue(output.contains("-- my comment"),
            "Trailing line comment should be preserved in output: " + output);
    }

    @Test
    void testCommentPreservation_leadingBlockComment(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("block_comment.sql");
        Files.writeString(sqlFile, "/* copyright */\nSELECT id FROM foo;");

        Format format = new Format();
        format.setSqlFile(sqlFile.toString());
        format.call();

        String output = outContent.toString().trim();
        assertTrue(output.contains("/* copyright */"),
            "Leading block comment should be preserved in output: " + output);
    }

    @Test
    void testKeywordCase_lower(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("test.sql");
        Files.writeString(sqlFile, "select * from foo where id = 1;");

        Format format = new Format();
        format.setSqlFile(sqlFile.toString());
        format.setKeywordCase("lower");
        format.call();

        String output = outContent.toString().trim();
        assertTrue(output.contains("select"), "SELECT should be lowercased: " + output);
        assertTrue(output.contains("from"), "FROM should be lowercased: " + output);
        assertTrue(output.contains("where"), "WHERE should be lowercased: " + output);
        assertFalse(output.contains("SELECT"), "No uppercase SELECT expected: " + output);
    }

    @Test
    void testKeywordCase_keep(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("test.sql");
        Files.writeString(sqlFile, "Select * From foo;");

        Format format = new Format();
        format.setSqlFile(sqlFile.toString());
        format.setKeywordCase("keep");
        format.call();

        String output = outContent.toString().trim();
        assertTrue(output.contains("Select"), "Select should be preserved: " + output);
        assertTrue(output.contains("From"), "From should be preserved: " + output);
    }

    @Test
    void testKeywordCase_invalidValue(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("test.sql");
        Files.writeString(sqlFile, "select * from foo;");

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));
        try {
            Format format = new Format();
            format.setSqlFile(sqlFile.toString());
            format.setKeywordCase("bad");
            int exitCode = format.call();
            assertEquals(ExitCodes.ERROR, exitCode, "Invalid keyword-case should exit ERROR");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void testIndentSize_4(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("test.sql");
        Files.writeString(sqlFile, "select * from foo;");

        Format format = new Format();
        format.setSqlFile(sqlFile.toString());
        format.setIndentSize(4);
        format.call();

        String output = outContent.toString().trim();
        // Trino formats as "FROM\n  foo" (2 spaces); with indent-size=4 it becomes "FROM\n    foo".
        assertTrue(output.contains("\n    foo"), "Should use 4-space indent: " + output);
        assertFalse(output.contains("\n  foo"), "Should not have 2-space-indented foo: " + output);
    }

    @Test
    void testMaxLineLength_warnsOnLongLine(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("test.sql");
        // After formatting, "  some_long_table_name" (22 chars) will exceed the limit.
        Files.writeString(sqlFile, "select * from some_long_table_name;");

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));
        try {
            Format format = new Format();
            format.setSqlFile(sqlFile.toString());
            format.setMaxLineLength(10);
            format.call();

            // Output should still be produced
            assertFalse(outContent.toString().isBlank(), "Output should still be produced");
            // Warning should be printed to stderr
            assertTrue(errContent.toString().contains("exceeds max-line-length"),
                "Should warn about long lines: " + errContent);
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void testMaxLineLength_noWarnWhenUnlimited(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("test.sql");
        Files.writeString(sqlFile, "select id from foo;");

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));
        try {
            Format format = new Format();
            format.setSqlFile(sqlFile.toString());
            format.setMaxLineLength(0); // 0 = unlimited
            format.call();

            assertFalse(errContent.toString().contains("exceeds"),
                "No warning expected when max-line-length=0");
        } finally {
            System.setErr(originalErr);
        }
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
