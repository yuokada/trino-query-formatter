package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
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
 * Tests for Analyze subcommand JSON and AST behaviors.
 */
class AnalyzeJsonAstTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final java.io.InputStream originalIn = System.in;

    @BeforeEach
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        System.setIn(originalIn);
    }

    @Test
    void testJsonOutputWithTwoStatements_returnsError(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("two.sql");
        String sql = "SELECT 1; SELECT * FROM catalog1.s.t;";
        Files.writeString(sqlFile, sql);

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("json");
        int exitCode = analyze.call();

        assertEquals(1, exitCode);
        assertEquals("", outContent.toString(StandardCharsets.UTF_8).strip());
        assertEquals(
            "analyze supports exactly one query; found multiple statements",
            errContent.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void testJsonWithAstEmbedded(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("ast.sql");
        String sql = "SELECT * FROM catalog1.s.t;";
        Files.writeString(sqlFile, sql);

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("json");
        analyze.setShowAst();
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8).strip();
        assertTrue(out.startsWith("{"));
        assertTrue(out.contains("\"ast\":"));
        assertTrue(out.contains("\"queryType\":"));
    }

    @Test
    void testTextWithAstStdout() throws IOException {
        String sql = "SELECT * FROM catalog1.s.t;";
        System.setIn(new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8)));

        Analyze analyze = new Analyze();
        analyze.setSqlFile("");
        analyze.setFormat("text");
        analyze.setShowAst();
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("Catalogs:"));
        assertTrue(out.contains("AST ("), "AST section header should appear: " + out);
    }

    @Test
    void testStdInEmpty_noOutput() throws IOException {
        System.setIn(new ByteArrayInputStream(new byte[0]));

        Analyze analyze = new Analyze();
        analyze.setSqlFile("");
        analyze.setFormat("json");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8).strip();
        assertEquals("", out);
    }

    @Test
    void testJsonErrorIncludesParseError(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("invalid.sql");
        String sql = "SELEC * FROM t;"; // invalid
        Files.writeString(sqlFile, sql);

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("json");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8).strip();
        assertTrue(out.contains("\"parseError\""));
        assertTrue(out.contains("\"queryType\":\"Unknown\""));
    }

    @Test
    void testJsonDetailsBasicAndFull(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("details.sql");
        String sql = "SELECT * FROM catalog1.s.t;";
        Files.writeString(sqlFile, sql);

        // basic: only queryType and catalogs
        Analyze analyzeBasic = new Analyze();
        analyzeBasic.setSqlFile(sqlFile.toString());
        analyzeBasic.setFormat("json");
        analyzeBasic.setDetails("basic");
        outContent.reset();
        analyzeBasic.call();
        String basicOut = outContent.toString(StandardCharsets.UTF_8).strip();
        assertTrue(basicOut.contains("\"queryType\""));
        assertTrue(basicOut.contains("\"catalogs\""));
        // should not contain fields unique to full output such as usesSelectStar
        assertFalse(basicOut.contains("usesSelectStar"));

        // full: includes flags and tables
        Analyze analyzeFull = new Analyze();
        analyzeFull.setSqlFile(sqlFile.toString());
        analyzeFull.setFormat("json");
        analyzeFull.setDetails("full");
        outContent.reset();
        analyzeFull.call();
        String fullOut = outContent.toString(StandardCharsets.UTF_8).strip();
        assertTrue(fullOut.contains("usesSelectStar"));
        assertTrue(fullOut.contains("tables"));
    }

    @Test
    void testAstView_tree(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("tree.sql");
        Files.writeString(sqlFile, "SELECT id FROM catalog1.s.t WHERE id = 1;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("text");
        analyze.setShowAst();
        analyze.setAstView("tree");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("AST (tree):"), "Tree header should appear: " + out);
        // TREE mode enriches Join/FunctionCall with attributes
        assertTrue(out.contains("Table[name=catalog1.s.t]"), "Table name should appear: " + out);
    }

    @Test
    void testAstView_outline(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("outline.sql");
        Files.writeString(sqlFile,
            "SELECT id, name FROM catalog1.s.orders WHERE id > 1 LIMIT 10;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("text");
        analyze.setShowAst();
        analyze.setAstView("outline");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("AST (outline):"), "Outline header should appear: " + out);
        assertTrue(out.contains("SELECT:"), "SELECT clause should appear: " + out);
        assertTrue(out.contains("TABLE:"), "TABLE should appear: " + out);
        assertTrue(out.contains("WHERE:"), "WHERE clause should appear: " + out);
        assertTrue(out.contains("LIMIT"), "LIMIT should appear: " + out);
    }

    @Test
    void testAstView_raw_backwardsCompat(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("raw.sql");
        Files.writeString(sqlFile, "SELECT * FROM catalog1.s.t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("text");
        analyze.setShowAst();
        analyze.setAstView("raw");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("AST (raw):"), "Raw header should appear: " + out);
        // RAW mode shows class names (same as original behaviour)
        assertTrue(out.contains("Query"), "Query node should appear: " + out);
    }

    @Test
    void testAstView_invalidValue(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("invalid.sql");
        Files.writeString(sqlFile, "SELECT 1;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setShowAst();
        analyze.setAstView("badview");
        int exitCode = analyze.call();

        assertEquals(2, exitCode, "Invalid --ast-view should exit ERROR (2)");
        assertTrue(errContent.toString().contains("Invalid --ast-view"),
            "Error message should be on stderr: " + errContent);
    }

    @Test
    void testAstDepth_limits_output(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("depth.sql");
        // A nested query will produce many levels; depth=2 should truncate
        Files.writeString(sqlFile,
            "SELECT id FROM (SELECT id FROM catalog1.s.t) sub WHERE id > 0;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("text");
        analyze.setShowAst();
        analyze.setAstView("tree");
        analyze.setAstDepth(2);
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("..."), "Truncation marker should appear with depth limit: " + out);
    }

    @Test
    void testAstView_outline_withCte(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("cte.sql");
        Files.writeString(sqlFile,
            "WITH cte AS (SELECT id FROM base) SELECT id FROM cte;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("text");
        analyze.setShowAst();
        analyze.setAstView("outline");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("WITH: cte"), "WITH clause with CTE name should appear: " + out);
        assertTrue(out.contains("SELECT:"), "SELECT clause should appear: " + out);
    }

    @Test
    void testOutputFileWritesAndStdoutEmpty(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("out.sql");
        String sql = "SELECT 1;";
        Files.writeString(sqlFile, sql);
        Path outFile = tempDir.resolve("result.ndjson");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("json");
        analyze.setOutputPath(outFile.toString());
        outContent.reset();
        analyze.call();
        // stdout should be empty
        assertEquals("", outContent.toString(StandardCharsets.UTF_8));
        // file should contain one JSON line
        String fileContent = Files.readString(outFile);
        assertTrue(fileContent.strip().startsWith("{"));
    }
}
