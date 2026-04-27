# trino-query-formatter

A CLI tool for formatting and analyzing Trino SQL queries, built with Java 17,
Quarkus, Picocli, and the `trino-parser`/`trino-cli` libraries.

## Quick start

```bash
# Build (produces an uber-jar by default)
./mvnw package

# Format a file
java -jar target/trino-query-formatter-1.0.0-SNAPSHOT.jar format query.sql

# Show extended build/runtime metadata
java -jar target/trino-query-formatter-1.0.0-SNAPSHOT.jar --version --verbose

# Read from stdin, write to stdout
cat query.sql | java -jar target/trino-query-formatter-1.0.0-SNAPSHOT.jar format -

# Analyze a file
java -jar target/trino-query-formatter-1.0.0-SNAPSHOT.jar analyze query.sql
```

---

## `format` subcommand

Formats one or more SQL statements. Input is split on `;` and `\G` delimiters.

### Usage

```
format [options] [<file>]
```

| Argument / Option | Default | Description |
|---|---|---|
| `<file>` | stdin | SQL file to format. Use `-` to read from stdin explicitly. |
| `--dir <path>` | — | Recursively process all `*.sql` files under this directory. |
| `--exclude <glob>` | — | Exclude glob for `--dir` mode. Repeatable. |
| `--summary` | false | Print compact file-count summary at the end of `--dir` mode. |
| `-o, --output <path>` | stdout | Write formatted output to this file instead of stdout. |
| `--check` | false | Check if the file is already formatted. Exits `1` when reformatting is needed. Not supported for stdin. |
| `--diff` | false | Print a unified diff of formatting changes. Exits `1` when differences are found. Not supported for stdin. Color auto-detected via terminal. |
| `--keyword-case <mode>` | `upper` | Keyword case: `upper` (default), `lower`, or `keep` (preserve original casing). |
| `--indent-size <n>` | `2` | Spaces per indentation level. Must be ≥ 1. |
| `--max-line-length <n>` | `0` | Warn to stderr when a formatted line exceeds this length. `0` = unlimited. |

`--dir` mode is intended for CI and currently requires either `--check` or `--diff`.

### Before / After example

Input (`query.sql`):
```sql
select id,name,status from orders join customers on orders.customer_id=customers.id where status='active' order by id;
```

Output (`format query.sql`):
```sql
SELECT
  id,
  name,
  status
FROM
  orders
JOIN customers ON (orders.customer_id = customers.id)
WHERE
  (status = 'active')
ORDER BY
  id
;
```

### Comment preservation

Inline and block comments are extracted before parsing and re-inserted after formatting.

Input:
```sql
/* copyright 2024 */
SELECT -- select columns
  id,
  name
FROM orders -- main table
;
```

Output:
```sql
/* copyright 2024 */
SELECT -- select columns
  id,
  name
FROM
  orders -- main table
;
```

### Keyword case examples

```bash
# Lowercase keywords
format --keyword-case lower query.sql
# → select * from foo where id = 1

# Preserve original casing
format --keyword-case keep query.sql
# → Select * From foo Where id = 1
```

### Check mode

```bash
format --check query.sql
# Exit 0 if already formatted, exit 1 if reformatting is needed.
# Prints "Would reformat: <file>" to stderr when differences are found.
```

### Output to file

```bash
format -o formatted.sql query.sql
# stdout is empty; formatted SQL is written to formatted.sql
```

### Stdin / pipe

```bash
echo "select * from foo;" | format -
cat *.sql | format -
```

### Diff mode

```bash
format --diff query.sql
# Prints a unified diff (like `git diff`) between the current file and its formatted version.
# Exit 0 if already formatted, exit 1 if reformatting would change the file.
```

### Regenerate golden test snapshots

```bash
UPDATE_GOLDEN=true ./mvnw test -Dtest=GoldenFormatTest
```

---

## `analyze` subcommand

Analyzes exactly one SQL statement and reports catalogs, tables, functions, joins,
CTEs, and lint findings.

### Usage

```
analyze [options] [<file>]
```

| Option | Default | Description |
|---|---|---|
| `<file>` | stdin | SQL file to analyze. Reads from stdin when omitted. |
| `--dir <path>` | — | Recursively analyze all `*.sql` files under this directory. |
| `--exclude <glob>` | — | Exclude glob for `--dir` mode. Repeatable. |
| `--summary` | false | Print compact file-count summary (`text`) or append summary object (`json`) in `--dir` mode. |
| `--format <fmt>` | `text` | Output format: `text` or `json` (single object in single-file mode, JSON array in `--dir` mode). |
| `--details <level>` | `basic` | Detail level: `basic` (catalogs only) or `full` (all fields + lint). |
| `--output <path>` | stdout | Write output to this file instead of stdout. |
| `--show-ast` | false | Print the AST for the query. |
| `--ast-view <mode>` | `tree` | AST display mode: `tree` (enriched), `outline` (clause summary), `raw` (class names). |
| `--ast-depth <n>` | `0` | Maximum AST depth to display. `0` = unlimited. |
| `--ast-limit <n>` | `10000` | Maximum characters for the embedded AST in JSON output. |
| `--catalog <name>` | — | Default catalog for partially qualified names (`schema.table`). |
| `--schema <name>` | — | Default schema for unqualified names (`table`), requires `--catalog`. |
| `--validate-functions` | false | Flag functions that are not Trino built-ins as **W002** lint warnings. |
| `--known-functions <list\|@file>` | — | Comma-separated list of additional known function names, or `@path` to a text file (one name per line). Requires `--validate-functions`. |
| `--udf-catalog <path>` | — | YAML file with UDF definitions for arity validation (**W003**). Functions listed are also treated as known (suppresses W002). |
| `--server <host:port>` | — | Trino server for Phase 2 remote validation via `EXPLAIN (TYPE VALIDATE)`. Omit to run local analysis only. |
| `--server-user <name>` | OS user | User name for the Trino session. Falls back to `TRINO_USER` env var. |
| `--server-password` | — | Password for Basic auth. Omit the value to be prompted interactively. Falls back to `TRINO_PASSWORD`. |
| `--server-access-token <token>` | — | Bearer token for OAuth2/JWT. Falls back to `TRINO_ACCESS_TOKEN`. |
| `--server-ssl` | false | Enable TLS for the Trino connection. |
| `--server-ssl-trust-all` | false | Disable TLS certificate verification (development only). |
| `--explain-timeout <sec>` | `30` | Timeout in seconds for the remote `EXPLAIN (TYPE VALIDATE)` request. |

### Text output (basic — default)

```bash
analyze query.sql
# =========================
# Catalogs: [my_catalog]
```

### Text output (full details)

```bash
analyze --details full query.sql
# =========================
# Catalogs: [catalog1]
# QueryType: Query
# Tables: [catalog1.s.orders]
# Joins: [LEFT:on]
# Flags: usesSelectStar=false, hasLimit=true, hasWhereOnDelete=null
# Functions: scalar=[lower], aggregate=[count], window=[]
# Lint: [WARNING] W001: SELECT * detected; prefer explicit column list
#   Why: SELECT * couples queries to the table schema; adding a column can break consumers.
#   Fix: List only the columns you need instead of using SELECT *.
```

### JSON output (full details)

```bash
analyze --format json --details full query.sql
```

```json
{"queryType":"Query","catalogs":["catalog1"],"tables":["catalog1.s.orders"],"usesSelectStar":true,"ctes":[],"joins":[],"functionsScalar":[],"functionsAggregate":[],"functionsWindow":[],"writeTargets":[],"findings":[{"ruleId":"W001","severity":"WARNING","message":"SELECT * detected; prefer explicit column list","hint":"SELECT * couples queries to the table schema; adding a column can break consumers.","fix":"List only the columns you need instead of using SELECT *."}],"hasLimit":false}
```

### Directory mode for CI (analyze)

```bash
analyze --dir sql --exclude "**/test/**" --format json --summary
```

`--dir` returns the highest severity across files:
- `0`: all clean
- `1`: at least one warning
- `2`: at least one error

### Helpful option validation

The CLI now emits explicit guidance for common option combinations:

```bash
analyze --format json --details basic query.sql
# stderr: Warning: --details basic suppresses extended fields; use --details full for complete JSON.

analyze --udf-catalog udf.yml query.sql
# stderr: Info: --udf-catalog implies function existence checking; pass --validate-functions to also enable W002.
```

Missing files are also reported without a Java stack trace:

```bash
format missing.sql
# stderr: Error: SQL file not found: missing.sql
```

## Shell completion

The CLI provides a built-in `generate-completion` subcommand.

### Bash

```bash
source <(trino-query-formatter generate-completion --shell bash)
```

To install it permanently:

```bash
trino-query-formatter generate-completion --shell bash > ~/.bash_completion.d/trino-query-formatter
```

### Zsh

The generated script is bash-compatible and works in Zsh via `bashcompinit`.

```bash
autoload -U +X bashcompinit && bashcompinit
source <(trino-query-formatter generate-completion --shell zsh)
```

To install it permanently:

```bash
autoload -U +X bashcompinit && bashcompinit
trino-query-formatter generate-completion --shell zsh > ~/.zsh/completions/_trino-query-formatter
```

### Fish

```bash
trino-query-formatter generate-completion --shell fish > ~/.config/fish/completions/trino-query-formatter.fish
```

Example loader scripts are committed under [`completions/`](completions/).

## Benchmark

Measure formatter/analyzer performance for generated large SQL:

```bash
trino-query-formatter benchmark --segments 500 --warmup 3 --iterations 10 --mode both
```

Output includes average/min/p95/max time and memory delta.

## JSON-RPC endpoint

The CLI provides a minimal JSON-RPC 2.0 endpoint for editor integration:

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"format","params":{"sql":"select * from foo;"}}' \
  | trino-query-formatter json-rpc
```

Supported methods:

- `format` params: `sql` (required), `keywordCase` (`upper|lower|keep`), `indentSize` (>=1)
- `analyze` params: `sql` (required)

## Formatting rules

CASE/JOIN/サブクエリの整形規則は以下を参照してください。

- [`docs/formatting-rules-ja.md`](docs/formatting-rules-ja.md)

## Project config

Shared defaults can be stored in `.trino-query-formatter.yml`.

Search order:

1. `--config <path>`
2. `.trino-query-formatter.yml` in the current working directory
3. `.trino-query-formatter.yml` in the user home directory

Precedence is `CLI args > config file > built-in defaults`.

Example:

```yaml
analyze:
  format: json
  details: full
  validate-functions: true
  udf-catalog: udf.yml
  server: trino.internal:8080

format:
  check: false
  diff: false
  keyword-case: upper

lint:
  disable-rules:
    - W001
  enable-rules: []
```

Notes:

- Unknown config keys emit a warning and do not abort the command.
- Relative paths like `udf-catalog: udf.yml` are resolved relative to the config file location.
- CLI flags always override config values.

Examples:

```bash
# Use defaults from the current directory config
trino-query-formatter analyze query.sql

# Use an explicit config file
trino-query-formatter --config ci/trino-query-formatter.yml format query.sql
```

### Multiple statements are rejected

```bash
analyze multi.sql
# stderr: analyze supports exactly one query; found multiple statements
# exit code: 1
```

### Catalog / schema defaults

```bash
# schema.table  →  my_catalog.schema.table
analyze --catalog my_catalog --details full query.sql

# bare_table  →  my_catalog.my_schema.bare_table
analyze --catalog my_catalog --schema my_schema --details full query.sql
```

### UDF / function validation

When `--validate-functions` is passed, each function call is looked up against the Trino 435
built-in function catalog. Functions not found there produce a `W002` lint warning.
Use `--known-functions` to declare project-specific UDFs so they are not flagged.

```bash
# Flag unknown functions
analyze --validate-functions --details full query.sql

# Suppress warnings for known UDFs
analyze --validate-functions --known-functions "my_etl_transform,date_to_epoch" query.sql

# Load UDF list from file (one name per line, # comments ignored)
analyze --validate-functions --known-functions @udfs.txt query.sql
```

Example output with an unknown function:
```
=========================
Catalogs: [catalog1]
QueryType: Query
Tables: [catalog1.s.t]
Functions: scalar=[my_etl_transform,upper], aggregate=[count], window=[]
UnknownFunctions: [my_etl_transform]
Lint: [WARNING] W002: Unknown function: my_etl_transform; may be a custom UDF or typo
```

### UDF validation scope

The following table summarises what can and cannot be validated without a live Trino cluster.

| Validation | Supported | Method |
|---|---|---|
| Function existence | ✅ (W002) | Look up against built-in catalog |
| **Argument count (arity)** | ✅ (W003) | `--udf-catalog` + `FunctionCall.getArguments().size()` |
| Namespace / catalog-specific UDFs | 🔜 Planned | `FunctionCall.getName()` prefix check |
| Argument types | ❌ Not possible | Requires cluster type inference |
| Return type | ❌ Not possible | Requires cluster type inference |

#### UDF definition file (`--udf-catalog`)

A YAML file declares project-specific UDFs with optional arity constraints:

```yaml
# udfs.yaml
functions:
  - name: my_etl_transform
    description: "ETL transformation function"
    arity: 2               # exact argument count

  - name: date_to_epoch
    arity: 1

  - name: my_variadic_udf
    minArgs: 1             # variable-length: accepts 1 or more arguments

  - name: bounded_udf
    minArgs: 2
    maxArgs: 4             # accepts 2 to 4 arguments
```

```bash
# Arity validation only (W003)
analyze --udf-catalog udfs.yaml query.sql

# Arity validation + unknown-function detection (W002 + W003)
analyze --validate-functions --udf-catalog udfs.yaml query.sql

# Example output when argument count does not match
# Lint: [WARNING] W003: Function my_etl_transform expects 2 argument(s), got 1
```

### Lint rules

**Phase 1 — local static analysis (always runs):**

| Rule | Severity | Trigger |
|---|---|---|
| `W001` | WARNING | `SELECT *` detected; prefer an explicit column list. |
| `W002` | WARNING | Function name not found in Trino built-in catalog (requires `--validate-functions`). |
| `W003` | WARNING | Function call arity does not match the UDF definition (requires `--udf-catalog`). |
| `W005` | WARNING | `ORDER BY` uses a positional reference (e.g. `ORDER BY 1`); use explicit column names. |
| `W006` | WARNING | Top-level `LIMIT` without `ORDER BY`; result set order is non-deterministic. |
| `W007` | WARNING | Query spans multiple catalogs and contains unqualified table references. |
| `E001` | ERROR | `DELETE` without a `WHERE` clause will affect all rows. |

**Phase 2 — remote validation via `EXPLAIN (TYPE VALIDATE)` (requires `--server`, runs only when Phase 1 has no errors):**

| Rule | Severity | Trigger |
|---|---|---|
| `W004` | WARNING | Other `USER_ERROR` from the Trino server not covered by E002–E005. |
| `E002` | ERROR | Table or view not found (`TABLE_NOT_FOUND`). |
| `E003` | ERROR | Column not found (`COLUMN_NOT_FOUND`). |
| `E004` | ERROR | Function not found on the server (`FUNCTION_NOT_FOUND`). |
| `E005` | ERROR | Type mismatch (`TYPE_MISMATCH`). |

---

## Global options

These options are placed **before** the subcommand name.

| Option | Description |
|---|---|
| `-v, --verbose` | Print progress messages to stderr. |
| `--quiet` | Suppress informational stderr messages (e.g. `--check` diff notice). |

```bash
java -jar ... -v format query.sql
java -jar ... --quiet format --check query.sql
```

---

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Success. |
| `1` | WARNING/validation failure — `--check` found statements that need reformatting, or `analyze` received multiple statements. |
| `2` | ERROR — parse error, missing file, or invalid option value. |
| `3` | EXCEPTION — unexpected runtime error. |

---

## Building

```bash
# Run tests (includes checkstyle)
./mvnw verify

# Run a single test class
./mvnw test -Dtest=QueryAnalyzerTest

# Package as über-jar (default)
./mvnw package

# Dev mode (live reload)
./mvnw compile quarkus:dev

# Build native executable (requires GraalVM)
./mvnw package -Dnative

# Build native in container (no GraalVM needed)
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

### Integration tests (Docker required)

The integration tests in `TrinoExplainClientContainerTest` and `AnalyzeCommandContainerTest`
start a real Trino 435 container via [Testcontainers](https://testcontainers.com/) to verify
the end-to-end `EXPLAIN (TYPE VALIDATE)` flow. They are skipped automatically when Docker is
not available.

```bash
# Start Docker Desktop (macOS), then:

# Run only the integration tests
./mvnw test -Dtest="TrinoExplainClientContainerTest,AnalyzeCommandContainerTest"

# Run the full suite including integration tests
./mvnw verify
```

The first run pulls the `trinodb/trino:435` image (~700 MB). Subsequent runs reuse the cached
image. The container is started once per test class and stopped when the class finishes.

The uber-jar is produced at `target/trino-query-formatter-<version>.jar`.

For more information on Quarkus, see <https://quarkus.io/>.
