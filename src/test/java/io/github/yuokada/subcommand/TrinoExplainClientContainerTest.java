package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yuokada.core.LintFinding;
import io.github.yuokada.core.RemoteValidationResult;
import io.github.yuokada.core.TrinoExplainClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.TrinoContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for {@link TrinoExplainClient} against a real Trino server
 * started via Testcontainers.
 *
 * <p>These tests verify that {@code EXPLAIN (TYPE VALIDATE)} requests are sent correctly
 * to Trino and that server error responses are mapped to the expected
 * {@link LintFinding} rule IDs (E002–E005).
 *
 * <p>Requires Docker to be available. All tests are skipped automatically when Docker
 * is not reachable.
 */
@Testcontainers(disabledWithoutDocker = true)
class TrinoExplainClientContainerTest {

    private static final String TRINO_IMAGE =
        "trinodb/trino:" + System.getProperty("trino.version", "435");

    @Container
    private static final TrinoContainer TRINO = new TrinoContainer(TRINO_IMAGE);

    private TrinoConnectionOptions opts;

    @BeforeEach
    void setUp() {
        this.opts = new TrinoConnectionOptions();
        this.opts.setServer(TRINO.getHost() + ":" + TRINO.getMappedPort(8080));
        this.opts.setUser(TRINO.getUsername());
    }

    // ---- Valid queries -------------------------------------------------------

    @Test
    void testValidQuery_returnsValid() {
        try (TrinoExplainClient client = TrinoExplainClient.create(this.opts, "tpch", "tiny")) {
            RemoteValidationResult result = client.validate("SELECT nationkey, name FROM nation");
            assertFalse(result.isSkipped(), "Result should not be skipped");
            assertTrue(result.getFindings().isEmpty(),
                "Valid query should produce no findings: " + result.getFindings());
        }
    }

    @Test
    void testValidAggregate_returnsValid() {
        try (TrinoExplainClient client = TrinoExplainClient.create(this.opts, "tpch", "tiny")) {
            RemoteValidationResult result = client.validate("SELECT COUNT(*) FROM nation");
            assertFalse(result.isSkipped(), "Result should not be skipped");
            assertTrue(result.getFindings().isEmpty(),
                "Valid aggregate should produce no findings: " + result.getFindings());
        }
    }

    // ---- E002: TABLE_NOT_FOUND ----------------------------------------------

    @Test
    void testTableNotFound_producesE002() {
        try (TrinoExplainClient client = TrinoExplainClient.create(this.opts, "tpch", "tiny")) {
            RemoteValidationResult result =
                client.validate("SELECT id FROM nonexistent_table_xyz");
            assertFalse(result.isSkipped(), "Result should not be skipped");
            assertFalse(result.getFindings().isEmpty(),
                "TABLE_NOT_FOUND should produce a finding");
            assertEquals("E002", result.getFindings().get(0).getRuleId(),
                "TABLE_NOT_FOUND should map to rule E002");
            assertEquals(LintFinding.Severity.ERROR, result.getFindings().get(0).getSeverity(),
                "E002 should have ERROR severity");
        }
    }

    // ---- E003: COLUMN_NOT_FOUND --------------------------------------------

    @Test
    void testColumnNotFound_producesE003() {
        try (TrinoExplainClient client = TrinoExplainClient.create(this.opts, "tpch", "tiny")) {
            RemoteValidationResult result =
                client.validate("SELECT nonexistent_col_xyz FROM nation");
            assertFalse(result.isSkipped(), "Result should not be skipped");
            assertFalse(result.getFindings().isEmpty(),
                "COLUMN_NOT_FOUND should produce a finding");
            assertEquals("E003", result.getFindings().get(0).getRuleId(),
                "COLUMN_NOT_FOUND should map to rule E003");
            assertEquals(LintFinding.Severity.ERROR, result.getFindings().get(0).getSeverity(),
                "E003 should have ERROR severity");
        }
    }

    // ---- E004: FUNCTION_NOT_FOUND ------------------------------------------

    @Test
    void testFunctionNotFound_producesE004() {
        try (TrinoExplainClient client = TrinoExplainClient.create(this.opts, "tpch", "tiny")) {
            RemoteValidationResult result =
                client.validate("SELECT my_unknown_xyz_func_99(1)");
            assertFalse(result.isSkipped(), "Result should not be skipped");
            assertFalse(result.getFindings().isEmpty(),
                "FUNCTION_NOT_FOUND should produce a finding");
            assertEquals("E004", result.getFindings().get(0).getRuleId(),
                "FUNCTION_NOT_FOUND should map to rule E004");
            assertEquals(LintFinding.Severity.ERROR, result.getFindings().get(0).getSeverity(),
                "E004 should have ERROR severity");
        }
    }

    // ---- E005: TYPE_MISMATCH -----------------------------------------------

    @Test
    void testTypeMismatch_producesE005() {
        // nationkey is BIGINT, name is VARCHAR — adding them produces TYPE_MISMATCH
        try (TrinoExplainClient client = TrinoExplainClient.create(this.opts, "tpch", "tiny")) {
            RemoteValidationResult result =
                client.validate("SELECT nationkey + name FROM nation");
            assertFalse(result.isSkipped(), "Result should not be skipped");
            assertFalse(result.getFindings().isEmpty(),
                "TYPE_MISMATCH should produce a finding");
            assertEquals("E005", result.getFindings().get(0).getRuleId(),
                "TYPE_MISMATCH should map to rule E005");
            assertEquals(LintFinding.Severity.ERROR, result.getFindings().get(0).getSeverity(),
                "E005 should have ERROR severity");
        }
    }

    // ---- Null SQL guard -----------------------------------------------------

    @Test
    void testNullSql_throwsIllegalArgumentException() {
        try (TrinoExplainClient client = TrinoExplainClient.create(this.opts, "tpch", "tiny")) {
            assertThrows(IllegalArgumentException.class, () -> client.validate(null),
                "validate(null) should throw IllegalArgumentException");
        }
    }
}
