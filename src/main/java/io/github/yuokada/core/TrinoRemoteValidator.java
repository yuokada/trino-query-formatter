package io.github.yuokada.core;

import io.github.yuokada.subcommand.TrinoConnectionOptions;
import java.util.List;

/**
 * Implements the two-phase validation strategy:
 *
 * <ol>
 *   <li><strong>Phase 1</strong> (local static analysis) — performed by {@link QueryAnalyzer}
 *       and {@link QueryAnalysisResult} before this class is involved.</li>
 *   <li><strong>Phase 2</strong> (remote {@code EXPLAIN (TYPE VALIDATE)}) — executed only when
 *       Phase 1 produced no ERROR-level {@link LintFinding} entries.</li>
 * </ol>
 *
 * <p>This class is a pure utility class and cannot be instantiated.
 */
public final class TrinoRemoteValidator {

    private TrinoRemoteValidator() {
    }

    /**
     * Runs Phase 2 remote validation if appropriate, and returns the combined result.
     *
     * <p>Phase 2 is skipped when:
     * <ul>
     *   <li>Remote validation is not configured ({@link TrinoConnectionOptions#isEnabled()} is
     *       {@code false}).</li>
     *   <li>Phase 1 produced at least one ERROR-level finding.</li>
     * </ul>
     *
     * <p>When Phase 2 runs, the returned {@link QueryAnalysisResult} has its remote findings
     * list populated. The original Phase 1 result is returned unchanged when Phase 2 is skipped.
     *
     * @param phase1    the result of local static analysis (Phase 1)
     * @param sql       the original SQL statement
     * @param opts      Trino connection options (may have {@code isEnabled() == false})
     * @param catalog   default catalog for the remote session (may be {@code null})
     * @param schema    default schema for the remote session (may be {@code null})
     * @return a {@link QueryAnalysisResult} augmented with Phase 2 findings, or the original
     *         Phase 1 result when Phase 2 was skipped
     */
    public static QueryAnalysisResult validate(
            QueryAnalysisResult phase1,
            String sql,
            TrinoConnectionOptions opts,
            String catalog,
            String schema) {

        if (!opts.isEnabled()) {
            return phase1;
        }

        // Skip Phase 2 when Phase 1 has any ERROR-level finding
        List<LintFinding> phase1Findings = phase1.getFindings();
        boolean phase1HasError = phase1Findings.stream()
            .anyMatch(f -> f.getSeverity() == LintFinding.Severity.ERROR);
        if (phase1HasError) {
            return phase1;
        }

        RemoteValidationResult remote;
        try (TrinoExplainClient client = TrinoExplainClient.create(opts, catalog, schema)) {
            remote = client.validate(sql);
        } catch (Exception e) {
            System.err.println("[remote-validation] Unexpected error: " + e.getMessage());
            remote = RemoteValidationResult.skipped();
        }

        if (remote.isSkipped() || remote.getFindings().isEmpty()) {
            return phase1;
        }

        return phase1.withRemoteFindings(remote.getFindings());
    }
}
