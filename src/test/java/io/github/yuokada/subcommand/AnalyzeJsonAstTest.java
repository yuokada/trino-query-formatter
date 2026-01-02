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
    private final PrintStream originalOut = System.out;
    private final java.io.InputStream originalIn = System.in;

    @BeforeEach
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
        System.setIn(originalIn);
    }

    @Test
    void testJsonOutputWithTwoStatements(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("two.sql");
        String sql = "SELECT 1; SELECT * FROM catalog1.s.t;";
        Files.writeString(sqlFile, sql);

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("json");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        String[] lines = out.strip().split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("\"queryType\""));
        assertTrue(lines[1].contains("\"catalogs\""));
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
        assertTrue(out.contains("Catalogs of Query No") || out.contains("Catalogs:"));
        assertTrue(out.contains("AST:"));
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
