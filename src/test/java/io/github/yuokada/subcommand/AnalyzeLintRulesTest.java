package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Tests for lint rules W005 (ORDER BY positional reference),
 * W006 (LIMIT without ORDER BY), and W007 (unqualified table in multi-catalog query).
 */
class AnalyzeLintRulesTest {

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

    // --- W005 ---

    @Test
    void testW005_positionalOrderBy(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("w005_positional.sql");
        Files.writeString(sqlFile, "SELECT id FROM t ORDER BY 1;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8).strip();
        assertTrue(out.contains("\"W005\""), "W005 should be reported: " + out);
    }

    @Test
    void testW005_namedOrderBy_noFinding(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("w005_named.sql");
        Files.writeString(sqlFile, "SELECT id FROM t ORDER BY id;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8).strip();
        assertFalse(out.contains("\"W005\""), "W005 should NOT be reported: " + out);
    }

    @Test
    void testW005_positionalInSubquery(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("w005_subquery.sql");
        Files.writeString(sqlFile, "SELECT * FROM (SELECT id FROM t ORDER BY 1) s;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8).strip();
        assertTrue(out.contains("\"W005\""), "W005 should be reported for subquery: " + out);
    }

    // --- W006 ---

    @Test
    void testW006_limitWithoutOrderBy(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("w006_limit.sql");
        Files.writeString(sqlFile, "SELECT * FROM t LIMIT 10;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8).strip();
        assertTrue(out.contains("\"W006\""), "W006 should be reported: " + out);
    }

    @Test
    void testW006_limitWithOrderBy_noFinding(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("w006_limit_order.sql");
        Files.writeString(sqlFile, "SELECT * FROM t ORDER BY id LIMIT 10;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8).strip();
        assertFalse(out.contains("\"W006\""), "W006 should NOT be reported: " + out);
    }

    @Test
    void testW006_nonQuery_noFinding(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("w006_delete.sql");
        Files.writeString(sqlFile, "DELETE FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8).strip();
        assertFalse(out.contains("\"W006\""), "W006 should NOT be reported for DELETE: " + out);
        assertTrue(out.contains("\"E001\""), "E001 should be reported for DELETE without WHERE: " + out);
    }

    // --- W007 ---

    @Test
    void testW007_multiCatalogWithUnqualified(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("w007_unqualified.sql");
        Files.writeString(sqlFile, "SELECT * FROM catA.s.t1, catB.s.t2, uq;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8).strip();
        assertTrue(out.contains("\"W007\""), "W007 should be reported: " + out);
    }

    @Test
    void testW007_multiCatalogAllQualified_noFinding(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("w007_qualified.sql");
        Files.writeString(sqlFile, "SELECT * FROM catA.s.t1, catB.s.t2;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8).strip();
        assertFalse(out.contains("\"W007\""), "W007 should NOT be reported: " + out);
    }

    @Test
    void testW007_singleCatalogUnqualified_noFinding(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("w007_single_catalog.sql");
        Files.writeString(sqlFile, "SELECT * FROM catA.s.t1, uq;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8).strip();
        assertFalse(out.contains("\"W007\""), "W007 should NOT be reported for single catalog: " + out);
    }
}
