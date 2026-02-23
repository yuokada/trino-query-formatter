package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyzeTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void testCallWithFile_multipleQueries_returnsError(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("test.sql");
        String sql = "SELECT * FROM catalog1.schema.tbl1; SELECT * FROM catalog2.schema.tbl2;";
        Files.writeString(sqlFile, sql);

        Analyze analyze = new Analyze();
        try {
            java.lang.reflect.Field field = analyze.getClass().getDeclaredField("sqlFile");
            field.setAccessible(true);
            field.set(analyze, sqlFile.toString());
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
        int exitCode = analyze.call();

        assertEquals(1, exitCode);
        assertEquals("", outContent.toString().trim());
        assertEquals(
            "analyze supports exactly one query; found multiple statements",
            errContent.toString().trim());
    }

    @Test
    void testCallWithFile_NoCatalogs(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("test_no_catalogs.sql");
        String sql = "SHOW CATALOGS;";
        Files.writeString(sqlFile, sql);

        Analyze analyze = new Analyze();
        try {
            java.lang.reflect.Field field = analyze.getClass().getDeclaredField("sqlFile");
            field.setAccessible(true);
            field.set(analyze, sqlFile.toString());
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
        analyze.call();

        String expectedOutput = """
            =========================
            No catalogs found.
            """.trim();
        assertEquals(expectedOutput, outContent.toString().trim());
    }

    @Test
    void testCallWithFile_JoinAcrossCatalogs(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("test_join.sql");
        String sql = """
            SELECT *
            FROM catalog1.schema.tbl1 t1
            JOIN catalog2.schema.tbl2 t2 ON t1.id = t2.id;
            """;
        Files.writeString(sqlFile, sql);

        Analyze analyze = new Analyze();
        try {
            java.lang.reflect.Field field = analyze.getClass().getDeclaredField("sqlFile");
            field.setAccessible(true);
            field.set(analyze, sqlFile.toString());
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
        analyze.call();

        String out = outContent.toString().trim();
        assertTrue(out.contains("========================="));
        assertTrue(out.contains("Catalogs: ["));
        assertTrue(out.contains("catalog1"));
        assertTrue(out.contains("catalog2"));
    }
}
