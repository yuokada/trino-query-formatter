package io.github.yuokada.subcommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yuokada.core.LintFinding;
import io.github.yuokada.core.QueryAnalyzer;
import io.github.yuokada.core.QueryAnalysisResult;
import io.github.yuokada.core.RemoteValidationResult;
import io.github.yuokada.core.TrinoRemoteValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TrinoRemoteValidator} two-phase flow control logic.
 *
 * <p>These tests verify the gating logic (when Phase 2 is skipped) without making
 * actual network connections. Remote-disabled and Phase-1-ERROR scenarios are covered.
 */
class TrinoRemoteValidatorTest {

    // ---- Remote validation disabled (no --server) ----------------------------

    @Test
    void testNoServer_returnsPhase1Unchanged() {
        TrinoConnectionOptions opts = new TrinoConnectionOptions();
        // server not set → isEnabled() == false

        QueryAnalysisResult phase1 = QueryAnalyzer.analyze(
            "SELECT * FROM t", null, null, null, null);

        QueryAnalysisResult result =
            TrinoRemoteValidator.validate(phase1, "SELECT * FROM t", opts, null, null);

        // Should be the exact same result object (no Phase 2 attempted)
        assertSameFindingCount(phase1, result);
    }

    // ---- Phase 1 has ERROR → Phase 2 skipped ---------------------------------

    @Test
    void testPhase1HasError_phase2Skipped() {
        // DELETE without WHERE → E001 (ERROR level)
        QueryAnalysisResult phase1 = QueryAnalyzer.analyze(
            "DELETE FROM t", null, null, null, null);

        List<LintFinding> phase1Findings = phase1.getFindings();
        boolean hasError = phase1Findings.stream()
            .anyMatch(f -> f.getSeverity() == LintFinding.Severity.ERROR);
        assertTrue(hasError, "Phase 1 should have E001 error finding");

        TrinoConnectionOptions opts = new TrinoConnectionOptions();
        opts.setServer("localhost:9999"); // server configured but should be skipped

        QueryAnalysisResult result =
            TrinoRemoteValidator.validate(phase1, "DELETE FROM t", opts, null, null);

        // Finding count must equal Phase 1 only (no remote findings added)
        assertSameFindingCount(phase1, result);
    }

    // ---- Phase 1 WARNING only → Phase 2 would run (but server unreachable) --

    @Test
    void testPhase1WarningOnly_phase2AttemptedButSkippedOnConnectionFailure() {
        // SELECT * → W001 (WARNING only, no ERROR)
        QueryAnalysisResult phase1 = QueryAnalyzer.analyze(
            "SELECT * FROM t", null, null, null, null);

        List<LintFinding> phase1Findings = phase1.getFindings();
        assertFalse(
            phase1Findings.stream().anyMatch(f -> f.getSeverity() == LintFinding.Severity.ERROR),
            "Phase 1 should have no ERROR findings");

        TrinoConnectionOptions opts = new TrinoConnectionOptions();
        // Use an address that will fail to connect immediately
        opts.setServer("127.0.0.1:19999");
        opts.setExplainTimeout(1); // 1 second timeout for fast failure

        QueryAnalysisResult result =
            TrinoRemoteValidator.validate(phase1, "SELECT * FROM t", opts, null, null);

        // Connection will fail → Phase 2 returns skipped → Phase 1 findings only
        assertEquals(phase1.getFindings().size(), result.getFindings().size(),
            "On connection failure Phase 2 should be skipped, returning Phase 1 findings only");
    }

    // ---- withRemoteFindings preserves Phase 1 data ---------------------------

    @Test
    void testWithRemoteFindings_appendsToPhase1() {
        QueryAnalysisResult phase1 = QueryAnalyzer.analyze(
            "SELECT * FROM t", null, null, null, null);
        int phase1Count = phase1.getFindings().size(); // W001

        List<LintFinding> remoteFinding = List.of(
            new LintFinding("E002", LintFinding.Severity.ERROR,
                "Table not found: mycat.s.t"));

        QueryAnalysisResult combined = phase1.withRemoteFindings(remoteFinding);

        assertEquals(phase1Count + 1, combined.getFindings().size(),
            "Combined result should have Phase 1 + 1 remote finding");

        LintFinding last = combined.getFindings().get(combined.getFindings().size() - 1);
        assertEquals("E002", last.getRuleId());
        assertEquals(LintFinding.Severity.ERROR, last.getSeverity());
        assertTrue(last.getMessage().contains("Table not found"));
    }

    // ---- withRemoteFindings with null list -----------------------------------

    @Test
    void testWithRemoteFindings_nullList_isNoop() {
        QueryAnalysisResult phase1 = QueryAnalyzer.analyze(
            "SELECT id FROM t", null, null, null, null);
        int phase1Count = phase1.getFindings().size();

        QueryAnalysisResult result = phase1.withRemoteFindings(null);
        assertEquals(phase1Count, result.getFindings().size(),
            "withRemoteFindings(null) should not change finding count");
    }

    // ---- RemoteValidationResult factory methods ------------------------------

    @Test
    void testRemoteValidationResult_valid() {
        RemoteValidationResult r = RemoteValidationResult.valid();
        assertFalse(r.isSkipped());
        assertTrue(r.getFindings().isEmpty());
    }

    @Test
    void testRemoteValidationResult_failed() {
        List<LintFinding> findings = List.of(
            new LintFinding("E003", LintFinding.Severity.ERROR, "Column not found: x"));
        RemoteValidationResult r = RemoteValidationResult.failed(findings);
        assertFalse(r.isSkipped());
        assertEquals(1, r.getFindings().size());
        assertEquals("E003", r.getFindings().get(0).getRuleId());
    }

    @Test
    void testRemoteValidationResult_skipped() {
        RemoteValidationResult r = RemoteValidationResult.skipped();
        assertTrue(r.isSkipped());
        assertTrue(r.getFindings().isEmpty());
    }

    // ---- TrinoConnectionOptions --server / isEnabled -------------------------

    @Test
    void testConnectionOptions_notEnabled_whenServerAbsent() {
        TrinoConnectionOptions opts = new TrinoConnectionOptions();
        assertFalse(opts.isEnabled());
    }

    @Test
    void testConnectionOptions_enabled_whenServerSet() {
        TrinoConnectionOptions opts = new TrinoConnectionOptions();
        opts.setServer("localhost:8080");
        assertTrue(opts.isEnabled());
    }

    @Test
    void testConnectionOptions_defaultUser_fallsBackToSystemUser() {
        TrinoConnectionOptions opts = new TrinoConnectionOptions();
        String user = opts.getUser();
        // Should be non-null (system user or env variable)
        assertTrue(user != null && !user.isBlank(),
            "Default user should fall back to system user");
    }

    // ---- helper --------------------------------------------------------------

    private static void assertSameFindingCount(QueryAnalysisResult expected,
                                               QueryAnalysisResult actual) {
        assertEquals(expected.getFindings().size(), actual.getFindings().size(),
            "Finding count should be unchanged");
    }
}
