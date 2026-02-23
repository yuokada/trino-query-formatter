package io.github.yuokada.subcommand;

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

/**
 * Tests for --details full in text output mode.
 */
class AnalyzeTextDetailsTest {

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
    void testTextFullDetailsPrintsExtendedInfo(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("multi.sql");
        String sql = "SELECT * FROM catalog1.s.t LIMIT 10; DELETE FROM catalog1.s.t WHERE id = 1;";
        Files.writeString(sqlFile, sql);

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("text");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString();
        // First statement assertions
        assertTrue(out.contains("QueryType: Query"));
        assertTrue(out.contains("Tables: [catalog1.s.t]"));
        assertTrue(out.contains("usesSelectStar=true"));
        assertTrue(out.contains("hasLimit=true"));
        // Second statement assertions
        assertTrue(out.contains("QueryType: Delete"));
        assertTrue(out.contains("hasWhereOnDelete=true"));
    }

    @Test
    void testTextFullDetails_lintW001_selectStar(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("star.sql");
        Files.writeString(sqlFile, "SELECT * FROM foo;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("text");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString();
        assertTrue(out.contains("Lint:"), "Lint line should appear: " + out);
        assertTrue(out.contains("W001"), "W001 rule ID should appear: " + out);
        assertTrue(out.contains("WARNING"), "WARNING severity should appear: " + out);
        assertTrue(out.contains("SELECT *"), "SELECT * message should appear: " + out);
    }

    @Test
    void testTextFullDetails_lintE001_deleteWithoutWhere(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("delete.sql");
        Files.writeString(sqlFile, "DELETE FROM foo;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("text");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString();
        assertTrue(out.contains("Lint:"), "Lint line should appear: " + out);
        assertTrue(out.contains("E001"), "E001 rule ID should appear: " + out);
        assertTrue(out.contains("ERROR"), "ERROR severity should appear: " + out);
        assertTrue(out.contains("DELETE without WHERE"), "Message should mention DELETE without WHERE: " + out);
    }

    @Test
    void testTextFullDetails_noLintWhenClean(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("clean.sql");
        Files.writeString(sqlFile, "SELECT id FROM foo;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("text");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString();
        // No SELECT * and no DELETE without WHERE — no lint findings
        assertTrue(!out.contains("Lint:") || !out.contains("W001"),
            "No W001 finding should appear for SELECT id: " + out);
    }
}

