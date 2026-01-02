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

        Analyze analyze = new Analyze(
            sqlFile.toString(),  // sqlFile
            "text",              // format
            false,               // showAst
            "full",              // details
            null,                // outputPath
            null,                // defaultCatalog
            null,                // defaultSchema
            10000                // astLimit
        );
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
}

