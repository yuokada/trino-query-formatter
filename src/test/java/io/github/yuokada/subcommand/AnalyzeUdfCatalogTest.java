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
 * End-to-end tests for the {@code --udf-catalog} option and W003 lint rule.
 */
class AnalyzeUdfCatalogTest {

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

    // ---- W003 not triggered without --udf-catalog ---------------------------

    @Test
    void testNoW003_withoutUdfCatalog(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT my_fn(a, b) FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertFalse(out.contains("W003"),
            "W003 should not appear without --udf-catalog: " + out);
    }

    // ---- W003 triggered on arity mismatch -----------------------------------

    @Test
    void testW003_arityMismatch_exactArity(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml, "functions:\n  - name: my_fn\n    arity: 2\n");

        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT my_fn(a) FROM t;");  // expects 2, got 1

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.setUdfCatalogPath(yaml.toString());
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("W003"), "W003 should appear on arity mismatch: " + out);
        assertTrue(out.contains("my_fn"), "Function name should appear: " + out);
        assertTrue(out.contains("expects 2"), "Expected arity should appear: " + out);
        assertTrue(out.contains("got 1"), "Actual arity should appear: " + out);
    }

    // ---- W003 not triggered when arity matches -------------------------------

    @Test
    void testNoW003_arityCorrect(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml, "functions:\n  - name: my_fn\n    arity: 2\n");

        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT my_fn(a, b) FROM t;");  // correct: 2 args

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.setUdfCatalogPath(yaml.toString());
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertFalse(out.contains("W003"), "No W003 when arity is correct: " + out);
    }

    // ---- W003 with minArgs ---------------------------------------------------

    @Test
    void testW003_minArgsViolation(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml, "functions:\n  - name: variadic\n    minArgs: 2\n");

        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT variadic(a) FROM t;");  // expects >= 2, got 1

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.setUdfCatalogPath(yaml.toString());
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("W003"), "W003 should appear for minArgs violation: " + out);
        assertTrue(out.contains("at least 2"), "Expected description should appear: " + out);
    }

    // ---- W003 with no arity constraint: no finding --------------------------

    @Test
    void testNoW003_unconstrained(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml,
            "functions:\n  - name: my_fn\n    description: \"known but unconstrained\"\n");

        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT my_fn(a, b, c, d) FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.setUdfCatalogPath(yaml.toString());
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertFalse(out.contains("W003"), "No W003 for unconstrained function: " + out);
    }

    // ---- udf-catalog functions are treated as known (no W002) ---------------

    @Test
    void testUdfCatalog_suppressesW002(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml, "functions:\n  - name: my_fn\n    arity: 1\n");

        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT my_fn(a) FROM t;");  // correct arity

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.setValidateFunctions(true);
        analyze.setUdfCatalogPath(yaml.toString());
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        // my_fn is in the catalog → should not generate W002
        assertFalse(out.contains("W002"),
            "Catalog function should not produce W002: " + out);
        // Arity matches → no W003 either
        assertFalse(out.contains("W003"),
            "No W003 when arity matches: " + out);
    }

    // ---- W002 for unknown + W003 for catalog function with wrong arity ------

    @Test
    void testBothW002andW003(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml, "functions:\n  - name: catalog_fn\n    arity: 2\n");

        Path sql = tempDir.resolve("q.sql");
        // catalog_fn called with wrong arity → W003
        // unknown_fn not in catalog and --validate-functions → W002
        Files.writeString(sql, "SELECT catalog_fn(a), unknown_fn(x) FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.setValidateFunctions(true);
        analyze.setUdfCatalogPath(yaml.toString());
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("W002"), "W002 should appear for unknown_fn: " + out);
        assertTrue(out.contains("W003"),
            "W003 should appear for catalog_fn arity mismatch: " + out);
        assertTrue(out.contains("unknown_fn"), "unknown_fn should appear: " + out);
        assertTrue(out.contains("catalog_fn"), "catalog_fn should appear: " + out);
    }

    // ---- udf-catalog only (no --validate-functions): only W003 --------------

    @Test
    void testUdfCatalogOnly_noW002(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml, "functions:\n  - name: catalog_fn\n    arity: 2\n");

        Path sql = tempDir.resolve("q.sql");
        // another_fn is not in catalog, but --validate-functions is not set → no W002
        // catalog_fn has wrong arity → W003
        Files.writeString(sql, "SELECT catalog_fn(a), another_fn(x) FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.setUdfCatalogPath(yaml.toString());
        // validateFunctions NOT set
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertFalse(out.contains("W002"), "No W002 without --validate-functions: " + out);
        assertTrue(out.contains("W003"), "W003 for arity mismatch: " + out);
    }

    // ---- Exit code is OK even when W003 is triggered -----------------------

    @Test
    void testExitCode_isOkEvenWithW003(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml, "functions:\n  - name: my_fn\n    arity: 3\n");

        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT my_fn(a) FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.setUdfCatalogPath(yaml.toString());
        int exitCode = analyze.call();

        assertEquals(0, exitCode, "Analyze should exit OK even with W003 findings");
    }

    // ---- Text output includes W003 in Lint line -----------------------------

    @Test
    void testTextOutput_w003InLintLine(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml, "functions:\n  - name: my_fn\n    arity: 2\n");

        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT my_fn(a) FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("text");
        analyze.setDetails("full");
        analyze.setUdfCatalogPath(yaml.toString());
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("Lint:"), "Lint line should appear: " + out);
        assertTrue(out.contains("W003"), "W003 should appear in text Lint line: " + out);
        assertTrue(out.contains("my_fn"), "Function name should appear: " + out);
    }

    // ---- Function not in catalog: no W003 -----------------------------------

    @Test
    void testNoW003_functionNotInCatalog(@TempDir Path tempDir) throws IOException {
        Path yaml = tempDir.resolve("udfs.yaml");
        Files.writeString(yaml, "functions:\n  - name: catalog_fn\n    arity: 2\n");

        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT other_fn(a, b, c) FROM t;");  // other_fn not in catalog

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.setUdfCatalogPath(yaml.toString());
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        assertFalse(out.contains("W003"), "No W003 for function not in catalog: " + out);
    }
}
