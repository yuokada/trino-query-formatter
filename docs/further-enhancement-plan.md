# Further Enhancement Plan for trino-query-formatter

## 1. Objectives

This plan aims to evolve `trino-query-formatter` from a useful SQL formatter/analyzer into a
team-ready quality gate and developer productivity tool.

Primary goals:

1. Improve formatting quality and configurability for real-world Trino SQL styles.
2. Expand analysis depth (semantic checks, lineage hints, better linting).
3. Strengthen CI/CD and integration workflows for team adoption.
4. Increase reliability, performance, and maintainability.

## 2. Current Baseline (Summary)

The tool already provides:

- `format` command with check/diff modes, keyword case controls, and line-length warnings.
- `analyze` command with text/JSON outputs, AST views, lint findings, and optional remote
  validation against Trino via `EXPLAIN (TYPE VALIDATE)`.
- UDF/function validation support with known-function and YAML catalog extensions.

This creates a strong base for iterative enhancements.

## 3. Enhancement Roadmap

## Phase A (Short term: 1–2 releases)

### A-1. Formatter flexibility

- Add configurable clause line-break policy (e.g., compact vs expanded `JOIN`/`WHERE` style).
- Add comma style options (`leading` / `trailing`) for teams with strict SQL style guides.
- Add optional alignment mode for selected constructs (e.g., `SELECT` aliases).

**Success criteria**

- New options are fully covered by unit tests and golden tests.
- Backward compatibility preserved with current defaults.

### A-2. Lint quality and explainability

- Expand lint rules with IDs and severity for common anti-patterns:
  - Unqualified table names in multi-catalog contexts.
  - `ORDER BY` ordinal usage (`ORDER BY 1`).
  - Non-deterministic `LIMIT` without deterministic ordering warnings.
- Add richer help text in CLI output: rule intent, risk, and suggested fix.

**Success criteria**

- Each rule has deterministic test fixtures.
- `--details full` presents actionable suggestions consistently.

### A-3. Developer ergonomics

- Add shell completion generation docs/examples (Bash/Zsh/Fish).
- Improve error messages for invalid option combinations.
- Add `--version --verbose` output with parser/runtime metadata.

**Success criteria**

- Reduced user confusion in common misconfiguration paths.
- Documented usage patterns in `README.md`.

## Phase B (Mid term: 3–5 releases)

### B-1. Project-level configuration

- Introduce config file support (e.g., `.trino-query-formatter.yml`) with precedence:
  CLI args > local config > defaults.
- Support include/exclude globs for batch formatting/analyzing.

**Success criteria**

- Teams can apply consistent behavior without repeating long CLI options.
- Config behavior is deterministic and validated via integration tests.

### B-2. Multi-file and CI workflows

- Add directory mode for recursive SQL discovery.
- Add machine-friendly summary mode for CI (counts by severity + non-zero exit policies).
- Provide GitHub Actions examples for format/lint checks.

**Success criteria**

- Easy CI onboarding with copy-paste examples.
- Predictable exit codes for policy enforcement.

### B-3. Analysis depth improvements

- Improve alias and CTE traceability in output.
- Add optional dependency graph export (JSON) for downstream tooling.
- Add rule toggles/suppression controls (`--enable-rule`, `--disable-rule`).

**Success criteria**

- More useful metadata for data platform and governance workflows.
- Stable JSON schema with versioning guidance.

## Phase C (Long term: 6+ releases)

### C-1. IDE/editor integration foundation

- Define a lightweight language-server-friendly API contract (stdin/stdout JSON mode).
- Add position-aware diagnostics where possible (line/column in findings).

**Success criteria**

- Editor plugins can consume formatter/analyzer results directly.
- Diagnostics become easier to navigate in IDEs.

### C-2. Performance and scalability

- Benchmark large SQL files and optimize parsing/formatting hot paths.
- Add optional parallel processing for independent file batches.
- Track memory usage regressions in CI benchmarks.

**Success criteria**

- Measurable speedup for batch operations.
- No significant memory regressions across releases.

### C-3. Enterprise readiness

- Add policy packs (predefined rule bundles by use case).
- Enhance remote validation support for auth profiles and secure defaults.
- Publish compatibility matrix (Trino versions, Java versions).

**Success criteria**

- Easier adoption in production-grade platform teams.
- Clear support boundaries reduce operational risk.

## 4. Cross-cutting Quality Plan

For every phase:

1. **Test strategy**
   - Unit tests for parser/formatter logic.
   - Golden snapshot tests for formatting stability.
   - Integration tests for CLI behavior and exit codes.

2. **Backward compatibility**
   - Keep defaults stable unless major version bump.
   - Mark behavior-changing options explicitly in release notes.

3. **Documentation**
   - Update README with examples for all user-facing options.
   - Maintain focused spec docs under `docs/` for non-trivial features.

4. **Observability**
   - Add optional debug logging around parsing/validation stages.
   - Standardize error categories for easier troubleshooting.

## 5. Suggested Prioritization

Recommended execution order:

1. Phase A-1 and A-2 (immediate user value).
2. Phase B-1 (configuration) to unlock team-scale adoption.
3. Phase B-2 (CI workflow) to drive consistent enforcement.
4. Phase B-3 and C tracks based on user demand and maintainer capacity.

## 6. Milestone Tracking Template

Use the following template per release:

- **Milestone**: `vX.Y.Z`
- **Scope**: (planned enhancement IDs, e.g., A-1, A-2)
- **Acceptance checks**:
  - `./mvnw validate`
  - `./mvnw test`
  - Representative CLI examples
- **Risks**: (parser compatibility, output schema change, performance)
- **Decision log**: (trade-offs and deferred items)

---

This plan is intentionally incremental: deliver small, testable improvements while preserving the
current simplicity of the CLI experience.
