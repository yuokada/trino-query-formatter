package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * End-to-end tests for the {@code --validate-functions} and {@code --known-functions} options
 * on the {@code analyze} subcommand.
 */
class AnalyzeValidateFunctionsTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    // ---- W002 not triggered without --validate-functions ----------------------

    @Test
    void testNoValidation_noW002(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT my_custom_udf(id) FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertFalse(out.contains("W002"),
            "W002 should not appear without --validate-functions: " + out);
        assertTrue(out.contains("unknownFunctions"),
            "unknownFunctions array should always appear in JSON");
        // Without validation the unknownFunctions array should be empty
        assertTrue(out.contains("\"unknownFunctions\":[]"),
            "unknownFunctions should be empty without --validate-functions: " + out);
    }

    // ---- W002 triggered for unknown function ----------------------------------

    @Test
    void testValidation_unknownFunctionProducesW002(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT my_custom_udf(id) FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setValidateFunctions(true);
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("W002"), "W002 should appear for unknown function: " + out);
        assertTrue(out.contains("my_custom_udf"),
            "Unknown function name should appear in output: " + out);
    }

    // ---- Known Trino built-in does not trigger W002 --------------------------

    @Test
    void testValidation_builtinFunctionNoW002(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT COUNT(*), LOWER(name) FROM t GROUP BY name;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setValidateFunctions(true);
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertFalse(out.contains("W002"),
            "Built-in functions should not trigger W002: " + out);
        assertTrue(out.contains("\"unknownFunctions\":[]"),
            "unknownFunctions should be empty for built-ins: " + out);
    }

    // ---- --known-functions (comma-separated) suppresses W002 ----------------

    @Test
    void testKnownFunctions_commaList_suppressesW002(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT my_etl_transform(id), another_udf(name) FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setValidateFunctions(true);
        analyze.setKnownFunctionsInput("my_etl_transform,another_udf");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertFalse(out.contains("W002"),
            "Declared known functions should not produce W002: " + out);
    }

    // ---- --known-functions partially suppresses W002 -------------------------

    @Test
    void testKnownFunctions_partialMatch(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT known_udf(id), unknown_udf(name) FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setValidateFunctions(true);
        analyze.setKnownFunctionsInput("known_udf");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        // unknown_udf is not declared → should appear in findings
        assertTrue(out.contains("W002"), "unknown_udf should still produce W002: " + out);
        assertTrue(out.contains("unknown_udf"), "unknown_udf should appear in output: " + out);
        // known_udf is declared → should not be in unknownFunctions
        assertFalse(out.contains("\"unknownFunctions\":[\"known_udf\"]"),
            "known_udf should not appear in unknownFunctions: " + out);
        assertFalse(out.contains("Unknown function: known_udf"),
            "known_udf should not produce a W002 message: " + out);
    }

    // ---- --known-functions via @file -----------------------------------------

    @Test
    void testKnownFunctions_atFile_suppressesW002(@TempDir Path tempDir) throws IOException {
        Path udfFile = tempDir.resolve("udfs.txt");
        Files.writeString(udfFile, "# My project UDFs\nmy_etl_transform\n\nanother_udf\n");

        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT my_etl_transform(id) FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setValidateFunctions(true);
        analyze.setKnownFunctionsInput("@" + udfFile.toString());
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertFalse(out.contains("W002"),
            "Function declared in @file should not produce W002: " + out);
    }

    // ---- Text output includes UnknownFunctions line --------------------------

    @Test
    void testTextOutput_unknownFunctions_line(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT my_custom_udf(id) FROM catalog1.s.t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("text");
        analyze.setDetails("full");
        analyze.setValidateFunctions(true);
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("UnknownFunctions:"),
            "Text output should contain UnknownFunctions line: " + out);
        assertTrue(out.contains("my_custom_udf"),
            "Unknown function name should appear in text output: " + out);
        assertTrue(out.contains("Lint:"),
            "Lint line should appear: " + out);
        assertTrue(out.contains("W002"),
            "W002 should appear in text lint output: " + out);
    }

    // ---- Case-insensitive matching -------------------------------------------

    @Test
    void testValidation_caseInsensitive(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        // COUNT is a Trino built-in (uppercase in SQL)
        Files.writeString(sql, "SELECT COUNT(*) FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setValidateFunctions(true);
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertFalse(out.contains("W002"),
            "COUNT (uppercase) is a built-in and should not trigger W002: " + out);
    }

    // ---- Multiple unknown functions -------------------------------------------

    @Test
    void testMultipleUnknownFunctions(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql,
            "SELECT udf_a(id), udf_b(name), COUNT(*) FROM t GROUP BY id, name;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setValidateFunctions(true);
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        // Both udf_a and udf_b should appear; COUNT should not
        assertTrue(out.contains("udf_a"), "udf_a should appear: " + out);
        assertTrue(out.contains("udf_b"), "udf_b should appear: " + out);
        // Two W002 findings expected
        long w002count = out.chars().filter(c -> c == '2')
            .count(); // rough check — both appear in output
        assertTrue(out.indexOf("W002") != out.lastIndexOf("W002"),
            "Two W002 findings expected (one per unknown function): " + out);
    }

    // ---- Exit code is OK even when W002 is triggered -------------------------

    @Test
    void testExitCode_isOkEvenWithW002(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT my_udf(id) FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setValidateFunctions(true);
        int exitCode = analyze.call();

        assertEquals(0, exitCode, "Analyze should exit OK even with W002 findings");
    }
}
