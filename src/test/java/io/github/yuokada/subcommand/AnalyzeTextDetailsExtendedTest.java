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
 * Extended text details (CTE/Join/Functions) verification.
 */
class AnalyzeTextDetailsExtendedTest {

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
    void testFullDetailsShowsCteJoinFunctions(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("cte_join_funcs.sql");
        String sql = """
            WITH a AS (SELECT 1 AS id)
            SELECT LOWER(CAST(a.id AS VARCHAR)), COUNT(*) AS c, ROW_NUMBER() OVER(PARTITION BY a.id)
            FROM a JOIN catalog2.s.t2 ON 1=1
            ;
            """;
        Files.writeString(sqlFile, sql);

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("text");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString();
        assertTrue(out.contains("CTEs: [a]"));
        assertTrue(out.contains("Joins: [INNER:on]"));
        assertTrue(out.contains("Functions:"));
        assertTrue(out.toLowerCase().contains("scalar=[lower]"));
        assertTrue(out.toLowerCase().contains("aggregate=[count]"));
        assertTrue(out.toLowerCase().contains("window=[row_number]"));
    }
}
