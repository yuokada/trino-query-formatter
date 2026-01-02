# trino-query-formatter

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## CLI Usage

This project provides Picocli commands to format and analyze SQL.

- Format a SQL file:
  - `./mvnw compile quarkus:dev -Dquarkus.args='format src/main/resources/queries/sample.sql'`

- Analyze a SQL file (text output; catalogs only):
  - `./mvnw compile quarkus:dev -Dquarkus.args='analyze src/main/resources/queries/sample.sql'`

- Analyze with JSON output (NDJSON: one JSON per statement):
  - `./mvnw compile quarkus:dev -Dquarkus.args='analyze --format json src/main/resources/queries/sample.sql'`

- Analyze and show AST (text):
  - `./mvnw compile quarkus:dev -Dquarkus.args='analyze --show-ast src/main/resources/queries/sample.sql'`

- Analyze with JSON and embed AST:
  - `./mvnw compile quarkus:dev -Dquarkus.args='analyze --format json --show-ast src/main/resources/queries/sample.sql'`
  - Limit embedded AST size (chars):
    - `./mvnw compile quarkus:dev -Dquarkus.args='analyze --format json --show-ast --ast-limit 2000 src/main/resources/queries/sample.sql'`

- Analyze from STDIN:
  - `cat src/main/resources/queries/sample.sql | ./mvnw compile quarkus:dev -Dquarkus.args='analyze --format json'`

### Detail level and file output

- Detail level: `--details basic|full` (default: `basic`)
  - JSON: `basic` emits `queryType` and `catalogs`; `full` includes tables and flags
  - Text: `full` prints extended lines (QueryType/Tables/Flags). `basic` keeps legacy output
- Write output to file: `--output <path>`
  - Writes the entire output to the specified file. Stdout remains empty when `--output` is used.
  - Output is written incrementally to the file (streaming) to reduce memory usage.
  - Arrays in text/JSON outputs are sorted for stable diffs.

### Catalog/Schema defaults

- Default catalog: `--catalog <name>`
  - Resolves partially qualified names like `schema.table` to `<catalog>.schema.table`.
- Default schema: `--schema <name>`
  - Resolves unqualified names like `table` to `<catalog>.<schema>.table` (when both are provided).
- Examples:
  - `./mvnw compile quarkus:dev -Dquarkus.args='analyze --details full --catalog c1 "SELECT * FROM s.t"'`
  - `./mvnw compile quarkus:dev -Dquarkus.args='analyze --details full --catalog c1 --schema s1 "SELECT * FROM t"'`
  - `./mvnw compile quarkus:dev -Dquarkus.args='analyze --format json --details full --catalog c1 --schema s1 src/main/resources/queries/sample.sql'`


## Roadmap (Next Small Tasks)

- Schema reporting: add `schemas` in details=full.


## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/trino-query-formatter-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- Picocli ([guide](https://quarkus.io/guides/picocli)): Develop command line applications with Picocli

## Provided Code

### Picocli Example

Hello and goodbye are civilization fundamentals. Let's not forget it with this example picocli application by changing the <code>command</code> and <code>parameters</code>.

[Related guide section...](https://quarkus.io/guides/picocli#command-line-application-with-multiple-commands)

Also for picocli applications the dev mode is supported. When running dev mode, the picocli application is executed and on press of the Enter key, is restarted.

As picocli applications will often require arguments to be passed on the commandline, this is also possible in dev mode via:

```shell script
./mvnw compile quarkus:dev -Dquarkus.args='Quarky'
```
