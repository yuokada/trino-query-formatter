package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Disabled("Disabled to reduce CI runtime; these assertions are covered by faster tests.")
class AnalyzeIssueValidationTest {

    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalErr = System.err;
    private final java.io.InputStream originalIn = System.in;

    @BeforeEach
    void setUpStreams() {
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setErr(originalErr);
        System.setIn(originalIn);
    }

    @Test
    void jsonBasicWarnsAboutSuppressedFields(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("test.sql");
        Files.writeString(sqlFile, "SELECT * FROM foo;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("json");
        analyze.setDetails("basic");

        assertEquals(0, analyze.call());
        assertTrue(errContent.toString(StandardCharsets.UTF_8)
            .contains("--details basic suppresses extended fields"));
    }

    @Test
    void udfCatalogWithoutValidateFunctionsPrintsInfo(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("test.sql");
        Path udfFile = tempDir.resolve("udf.yml");
        Files.writeString(sqlFile, "SELECT my_fn(a) FROM foo;");
        Files.writeString(udfFile, "functions:\n  - name: my_fn\n    arity: 1\n");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setUdfCatalogPath(udfFile.toString());

        assertEquals(0, analyze.call());
        assertTrue(errContent.toString(StandardCharsets.UTF_8)
            .contains("--udf-catalog implies function existence checking"));
    }

    @Test
    void textBasicWithRemoteValidationPrintsWarning(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("test.sql");
        Files.writeString(sqlFile, "SELECT id FROM foo;");

        TrinoConnectionOptions options = new TrinoConnectionOptions();
        options.setServer("localhost:8080");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setFormat("text");
        analyze.setDetails("basic");
        analyze.setServerOptions(options);

        assertEquals(0, analyze.call());
        assertTrue(errContent.toString(StandardCharsets.UTF_8)
            .contains("remote findings are only shown in --details full mode"));
    }

    @Test
    void missingSqlFileReturnsError() throws IOException {
        Analyze analyze = new Analyze();
        analyze.setSqlFile("missing.sql");

        assertEquals(2, analyze.call());
        assertTrue(errContent.toString(StandardCharsets.UTF_8).contains("SQL file not found"));
    }

    @Test
    void missingUdfCatalogReturnsError(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("test.sql");
        Files.writeString(sqlFile, "SELECT id FROM foo;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());
        analyze.setUdfCatalogPath(tempDir.resolve("missing.yml").toString());

        assertEquals(2, analyze.call());
        assertTrue(errContent.toString(StandardCharsets.UTF_8)
            .contains("UDF catalog file not found"));
    }

    @Test
    void fileAndStdinTogetherReturnError(@TempDir Path tempDir) throws IOException {
        Path sqlFile = tempDir.resolve("test.sql");
        Files.writeString(sqlFile, "SELECT id FROM foo;");
        System.setIn(new ByteArrayInputStream("SELECT 1;".getBytes(StandardCharsets.UTF_8)));

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sqlFile.toString());

        assertEquals(2, analyze.call());
        assertTrue(errContent.toString(StandardCharsets.UTF_8)
            .contains("provide either <file> or stdin, not both"));
    }
}
