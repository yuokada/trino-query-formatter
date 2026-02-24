# trino-query-formatter

A CLI tool for formatting and analyzing Trino SQL queries, built with Java 17,
Quarkus, Picocli, and the `trino-parser`/`trino-cli` libraries.

## Quick start

```bash
# Build (produces an über-jar by default)
./mvnw package

# Format a file
java -jar target/trino-query-formatter-1.0.0-SNAPSHOT-runner.jar format query.sql

# Read from stdin, write to stdout
cat query.sql | java -jar target/trino-query-formatter-1.0.0-SNAPSHOT-runner.jar format -

# Analyze a file
java -jar target/trino-query-formatter-1.0.0-SNAPSHOT-runner.jar analyze query.sql
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
| `-o, --output <path>` | stdout | Write formatted output to this file instead of stdout. |
| `--check` | false | Check if the file is already formatted. Exits `1` when reformatting is needed. Not supported for stdin. |
| `--diff` | false | Print a unified diff of formatting changes. Exits `1` when differences are found. Not supported for stdin. Color auto-detected via terminal. |
| `--keyword-case <mode>` | `upper` | Keyword case: `upper` (default), `lower`, or `keep` (preserve original casing). |
| `--indent-size <n>` | `2` | Spaces per indentation level. Must be ≥ 1. |
| `--max-line-length <n>` | `0` | Warn to stderr when a formatted line exceeds this length. `0` = unlimited. |

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
| `--format <fmt>` | `text` | Output format: `text` or `json` (single object for the single query). |
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
```

### JSON output (full details)

```bash
analyze --format json --details full query.sql
```

```json
{"queryType":"Query","catalogs":["catalog1"],"tables":["catalog1.s.orders"],"usesSelectStar":true,"ctes":[],"joins":[],"functionsScalar":[],"functionsAggregate":[],"functionsWindow":[],"writeTargets":[],"findings":[{"ruleId":"W001","severity":"WARNING","message":"SELECT * detected; prefer explicit column list"}],"hasLimit":false}
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

The über-jar is produced at `target/trino-query-formatter-1.0.0-SNAPSHOT-runner.jar`.

For more information on Quarkus, see <https://quarkus.io/>.
