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
        try {
            var f1 = analyze.getClass().getDeclaredField("sqlFile");
            f1.setAccessible(true);
            f1.set(analyze, sqlFile.toString());
            var f2 = analyze.getClass().getDeclaredField("format");
            f2.setAccessible(true);
            f2.set(analyze, "json");
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
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
        try {
            var f1 = analyze.getClass().getDeclaredField("sqlFile");
            f1.setAccessible(true);
            f1.set(analyze, sqlFile.toString());
            var f2 = analyze.getClass().getDeclaredField("format");
            f2.setAccessible(true);
            f2.set(analyze, "json");
            var f3 = analyze.getClass().getDeclaredField("showAst");
            f3.setAccessible(true);
            f3.set(analyze, true);
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
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
        try {
            var f1 = analyze.getClass().getDeclaredField("sqlFile");
            f1.setAccessible(true);
            f1.set(analyze, "");
            var f2 = analyze.getClass().getDeclaredField("format");
            f2.setAccessible(true);
            f2.set(analyze, "text");
            var f3 = analyze.getClass().getDeclaredField("showAst");
            f3.setAccessible(true);
            f3.set(analyze, true);
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("Catalogs of Query No") || out.contains("Catalogs:"));
        assertTrue(out.contains("AST:"));
    }

    @Test
    void testStdInEmpty_noOutput() throws IOException {
        System.setIn(new ByteArrayInputStream(new byte[0]));

        Analyze analyze = new Analyze();
        try {
            var f1 = analyze.getClass().getDeclaredField("sqlFile");
            f1.setAccessible(true);
            f1.set(analyze, "");
            var f2 = analyze.getClass().getDeclaredField("format");
            f2.setAccessible(true);
            f2.set(analyze, "json");
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
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
        try {
            var f1 = analyze.getClass().getDeclaredField("sqlFile");
            f1.setAccessible(true);
            f1.set(analyze, sqlFile.toString());
            var f2 = analyze.getClass().getDeclaredField("format");
            f2.setAccessible(true);
            f2.set(analyze, "json");
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
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
        try {
            var f1 = analyzeBasic.getClass().getDeclaredField("sqlFile");
            f1.setAccessible(true);
            f1.set(analyzeBasic, sqlFile.toString());
            var f2 = analyzeBasic.getClass().getDeclaredField("format");
            f2.setAccessible(true);
            f2.set(analyzeBasic, "json");
            var f3 = analyzeBasic.getClass().getDeclaredField("details");
            f3.setAccessible(true);
            f3.set(analyzeBasic, "basic");
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
        outContent.reset();
        analyzeBasic.call();
        String basicOut = outContent.toString(StandardCharsets.UTF_8).strip();
        assertTrue(basicOut.contains("\"queryType\""));
        assertTrue(basicOut.contains("\"catalogs\""));
        // should not contain fields unique to full output such as usesSelectStar
        assertFalse(basicOut.contains("usesSelectStar"));

        // full: includes flags and tables
        Analyze analyzeFull = new Analyze();
        try {
            var f1 = analyzeFull.getClass().getDeclaredField("sqlFile");
            f1.setAccessible(true);
            f1.set(analyzeFull, sqlFile.toString());
            var f2 = analyzeFull.getClass().getDeclaredField("format");
            f2.setAccessible(true);
            f2.set(analyzeFull, "json");
            var f3 = analyzeFull.getClass().getDeclaredField("details");
            f3.setAccessible(true);
            f3.set(analyzeFull, "full");
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
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
        try {
            var f1 = analyze.getClass().getDeclaredField("sqlFile");
            f1.setAccessible(true);
            f1.set(analyze, sqlFile.toString());
            var f2 = analyze.getClass().getDeclaredField("format");
            f2.setAccessible(true);
            f2.set(analyze, "json");
            var f3 = analyze.getClass().getDeclaredField("outputPath");
            f3.setAccessible(true);
            f3.set(analyze, outFile.toString());
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        }
        outContent.reset();
        analyze.call();
        // stdout should be empty
        assertEquals("", outContent.toString(StandardCharsets.UTF_8));
        // file should contain one JSON line
        String fileContent = Files.readString(outFile);
        assertTrue(fileContent.strip().startsWith("{"));
    }
}
