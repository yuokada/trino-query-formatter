package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

/**
 * Extended JSON full details verification (write targets and arrays present).
 */
class AnalyzeJsonFullExtendedTest {

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
    void testJsonFullIncludesWriteTargets(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("write_target.sql");
        String sql = "INSERT INTO catalog1.s.out SELECT * FROM catalog1.s.src;";
        Files.writeString(sqlFile, sql);

        Analyze analyze = new Analyze(
            sqlFile.toString(),  // sqlFile
            "json",              // format
            false,               // showAst
            "full",              // details
            null,                // outputPath
            null,                // defaultCatalog
            null,                // defaultSchema
            10000                // astLimit
        );
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8).strip();
        assertTrue(out.contains("\"writeTargets\""));
        assertTrue(out.contains("catalog1.s.out"));
        assertTrue(out.contains("\"tables\""));
        assertTrue(out.contains("catalog1.s.src"));
    }
}

