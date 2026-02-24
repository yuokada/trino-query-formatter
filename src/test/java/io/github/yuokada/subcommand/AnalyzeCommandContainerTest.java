package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.containers.TrinoContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end integration tests for the {@code analyze} subcommand with a real
 * Trino server started via Testcontainers.
 *
 * <p>These tests verify that:
 * <ul>
 *   <li>Phase 2 remote validation is performed against a live Trino instance.</li>
 *   <li>E002 is present in the output when a table is not found on the server.</li>
 *   <li>Phase 2 is skipped when Phase 1 has ERROR-level findings (E001).</li>
 *   <li>JSON output is well-formed and parseable.</li>
 * </ul>
 *
 * <p>Requires Docker to be available. All tests are skipped automatically when Docker
 * is not reachable.
 */
@Testcontainers(disabledWithoutDocker = true)
class AnalyzeCommandContainerTest {

    private static final String TRINO_IMAGE =
        "trinodb/trino:" + System.getProperty("trino.version", "435");

    @Container
    private static final TrinoContainer TRINO = new TrinoContainer(TRINO_IMAGE);

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(this.outContent));
        System.setErr(new PrintStream(this.errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(this.originalOut);
        System.setErr(this.originalErr);
    }

    // ---- Valid query: no remote findings ------------------------------------

    @Test
    void testValidQuery_noRemoteFindings(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT nationkey FROM nation;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.setDefaultCatalog("tpch");
        analyze.setDefaultSchema("tiny");
        analyze.setServerOptions(buildOpts());
        analyze.call();

        String out = this.outContent.toString(StandardCharsets.UTF_8);
        assertAll(
            () -> assertFalse(out.contains("E002"), "Valid query should not produce E002: " + out),
            () -> assertFalse(out.contains("E003"), "Valid query should not produce E003: " + out),
            () -> assertFalse(out.contains("E004"), "Valid query should not produce E004: " + out),
            () -> assertFalse(out.contains("E005"), "Valid query should not produce E005: " + out)
        );
    }

    // ---- Invalid table: E002 in output -------------------------------------

    @Test
    void testInvalidTable_E002InOutput(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT id FROM missing_table_xyz;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.setDefaultCatalog("tpch");
        analyze.setDefaultSchema("tiny");
        analyze.setServerOptions(buildOpts());
        analyze.call();

        String out = this.outContent.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("E002"),
            "TABLE_NOT_FOUND should produce E002 in output: " + out);
    }

    // ---- Phase 1 ERROR: Phase 2 must not be attempted ----------------------

    @Test
    void testPhase1Error_phase2NotAttempted(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        // DELETE without WHERE → E001 (ERROR) from Phase 1
        Files.writeString(sql, "DELETE FROM nation;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.setDefaultCatalog("tpch");
        analyze.setDefaultSchema("tiny");
        analyze.setServerOptions(buildOpts());
        analyze.call();

        String out = this.outContent.toString(StandardCharsets.UTF_8);
        String err = this.errContent.toString(StandardCharsets.UTF_8);

        assertTrue(out.contains("E001"),
            "DELETE without WHERE should produce E001: " + out);
        assertFalse(out.contains("E002"),
            "Phase 2 should not run when Phase 1 has ERROR: " + out);
        assertFalse(err.contains("[remote-validation]"),
            "No [remote-validation] stderr when Phase 1 has ERROR: " + err);
    }

    // ---- JSON output is well-formed ----------------------------------------

    @Test
    void testValidQuery_jsonOutputIsParseable(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT COUNT(*) FROM nation;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.setDefaultCatalog("tpch");
        analyze.setDefaultSchema("tiny");
        analyze.setServerOptions(buildOpts());
        analyze.call();

        String out = this.outContent.toString(StandardCharsets.UTF_8);
        ObjectMapper mapper = new ObjectMapper();
        assertDoesNotThrow(() -> mapper.readTree(out),
            "Output should be valid JSON: " + out);
    }

    // ---- Helper -------------------------------------------------------------

    private static TrinoConnectionOptions buildOpts() {
        TrinoConnectionOptions opts = new TrinoConnectionOptions();
        opts.setServer(TRINO.getHost() + ":" + TRINO.getMappedPort(8080));
        opts.setUser(TRINO.getUsername());
        return opts;
    }
}
