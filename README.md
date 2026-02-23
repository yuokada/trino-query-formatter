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

### Regenerate golden test snapshots

```bash
UPDATE_GOLDEN=true ./mvnw test -Dtest=GoldenFormatTest
```

---

## `analyze` subcommand

Analyzes one or more SQL statements and reports catalogs, tables, functions, joins,
CTEs, and lint findings.

### Usage

```
analyze [options] [<file>]
```

| Option | Default | Description |
|---|---|---|
| `<file>` | stdin | SQL file to analyze. Reads from stdin when omitted. |
| `--format <fmt>` | `text` | Output format: `text` or `json` (NDJSON — one object per statement). |
| `--details <level>` | `basic` | Detail level: `basic` (catalogs only) or `full` (all fields + lint). |
| `--output <path>` | stdout | Write output to this file instead of stdout. |
| `--show-ast` | false | Print the AST after each statement. |
| `--ast-limit <n>` | `10000` | Maximum characters for the embedded AST in JSON output. |
| `--catalog <name>` | — | Default catalog for partially qualified names (`schema.table`). |
| `--schema <name>` | — | Default schema for unqualified names (`table`), requires `--catalog`. |

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

### Catalog / schema defaults

```bash
# schema.table  →  my_catalog.schema.table
analyze --catalog my_catalog --details full query.sql

# bare_table  →  my_catalog.my_schema.bare_table
analyze --catalog my_catalog --schema my_schema --details full query.sql
```

### Lint rules

| Rule | Severity | Trigger |
|---|---|---|
| `W001` | WARNING | `SELECT *` detected; prefer an explicit column list. |
| `E001` | ERROR | `DELETE` without a `WHERE` clause will affect all rows. |

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
| `1` | WARNING — `--check` found statements that need reformatting. |
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
