package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
 * End-to-end tests for the remote validation integration in the {@code analyze} subcommand.
 *
 * <p>These tests verify that:
 * <ul>
 *   <li>The existing static-analysis behavior is unchanged when {@code --server} is absent.</li>
 *   <li>Phase 1 ERROR findings are present and Phase 2 is not attempted when errors exist.</li>
 *   <li>On connection failure Phase 1 findings are still returned intact.</li>
 * </ul>
 *
 * <p>Tests do not require a real Trino server; connection failures are expected and handled
 * gracefully (Phase 2 skipped with a stderr message).
 */
class AnalyzeRemoteValidationTest {

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

    // ---- No --server: existing behaviour is unchanged -----------------------

    @Test
    void testNoServer_existingFindingsUnchanged(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT * FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        // W001 should still appear (SELECT *)
        assertTrue(out.contains("W001"), "W001 should appear without --server: " + out);
        // No E002–E005 or W004 without remote validation
        assertFalse(out.contains("E002"), "No E002 without --server");
        assertFalse(out.contains("E003"), "No E003 without --server");
        assertFalse(out.contains("W004"), "No W004 without --server");
    }

    @Test
    void testNoServer_exitCodeOk(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT id FROM t;");

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        int exitCode = analyze.call();

        assertEquals(0, exitCode, "Exit code should be 0 without --server");
    }

    // ---- Phase 1 ERROR present: Phase 2 must NOT be attempted ---------------

    @Test
    void testPhase1Error_phase2Skipped(@TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        // DELETE without WHERE → E001 (ERROR)
        Files.writeString(sql, "DELETE FROM t;");

        TrinoConnectionOptions opts = new TrinoConnectionOptions();
        // Point to a non-existent server; if Phase 2 were attempted, it would either
        // fail fast (connection refused) or produce extra stderr output
        opts.setServer("127.0.0.1:19998");
        opts.setExplainTimeout(1);

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.setServerOptions(opts);
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        String err = errContent.toString(StandardCharsets.UTF_8);

        assertTrue(out.contains("E001"), "E001 should appear: " + out);
        // Phase 2 should NOT have been attempted (no connection error on stderr)
        assertFalse(err.contains("[remote-validation]"),
            "Phase 2 should be skipped when Phase 1 has ERROR: " + err);
    }

    // ---- Phase 1 WARNING only + unreachable server: Phase 1 findings intact -

    @Test
    void testPhase1Warning_unreachableServer_phase1FindingsReturned(
            @TempDir Path tempDir) throws IOException {
        Path sql = tempDir.resolve("q.sql");
        Files.writeString(sql, "SELECT * FROM t;"); // W001

        TrinoConnectionOptions opts = new TrinoConnectionOptions();
        opts.setServer("127.0.0.1:19997"); // will fail to connect
        opts.setExplainTimeout(1);

        Analyze analyze = new Analyze();
        analyze.setSqlFile(sql.toString());
        analyze.setFormat("json");
        analyze.setDetails("full");
        analyze.setServerOptions(opts);
        analyze.call();

        String out = outContent.toString(StandardCharsets.UTF_8);
        String err = errContent.toString(StandardCharsets.UTF_8);

        // Phase 1 findings should still be present
        assertTrue(out.contains("W001"), "W001 (Phase 1) should appear: " + out);
        // Phase 2 was attempted but failed → stderr message expected
        assertTrue(err.contains("[remote-validation]"),
            "Remote validation should log connection error to stderr: " + err);
        // No Phase 2 findings in output
        assertFalse(out.contains("E002"), "No E002 on connection failure: " + out);
        assertFalse(out.contains("E003"), "No E003 on connection failure: " + out);
    }

    // ---- TrinoConnectionOptions environment variable fallback ---------------

    @Test
    void testConnectionOptions_noServerEnv_isDisabled() {
        // Skip when TRINO_SERVER is set in the environment so the test is not flaky in CI.
        assumeTrue(System.getenv("TRINO_SERVER") == null,
            "Skipping: TRINO_SERVER is set in the environment");

        TrinoConnectionOptions opts = new TrinoConnectionOptions();
        assertFalse(opts.isEnabled(),
            "isEnabled() should be false when neither --server nor TRINO_SERVER is present");
    }
}
