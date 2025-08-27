# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java/io/github/yuokada`: Java sources
  - `core`: core logic (e.g., `QueryAnalyzer`).
  - `subcommand`: CLI subcommands (`Format`, `Analyze`).
  - `EntryCommand.java`: top-level Picocli command.
- `src/test/java`: JUnit 5 tests (`*Test.java`).
- `src/main/resources/queries`: sample SQL for fixtures/manual runs.
- `src/main/docker`: Dockerfiles for native images.
- Root: `pom.xml`, `mvnw*`, `Makefile`, `checkstyle.xml`.

## Build, Test, and Development Commands
- Dev run: `./mvnw compile quarkus:dev -Dquarkus.args='format src/main/resources/queries/query1.sql'` — hot reload + Picocli.
- Unit tests: `./mvnw test` — runs JUnit 5.
- Package (JAR): `./mvnw package`; run `java -jar target/quarkus-app/quarkus-run.jar --help`.
- Uber-jar: `./mvnw package -Dquarkus.package.jar.type=uber-jar`; run `java -jar target/*-runner.jar`.
- Native: `./mvnw package -Dnative` (or `-Dquarkus.native.container-build=true`).
- GraalVM (asdf): `make build_with_graalvm` (requires Java 17 GraalVM).

## Coding Style & Naming Conventions
- Java 17, Quarkus 3.x, Picocli.
- Checkstyle enforced at `mvn validate`: 100-char lines, Javadoc, no `*` imports, whitespace rules.
- Prefer 2-space indentation; descriptive names (e.g., `QueryAnalyzer`, `Format`).
- Packages: `io.github.yuokada.<module>`.

## Testing Guidelines
- Framework: JUnit 5; tests live in `src/test/java`.
- Naming: `ClassNameTest.java`; one concern per test class.
- Fixtures: use files under `src/main/resources/queries`.
- Run: `./mvnw test`. Native profile: `./mvnw -Pnative verify` (integration tests via Failsafe; keep ITs enabled).

## Commit & Pull Request Guidelines
- Commits: short, imperative subjects; include scope when useful.
  - Examples: `Bump quarkus to 3.20`, `Update pom.xml`.
- PRs: clear description, link issues, include example CLI command/output for user-facing changes.
- Ensure CI passes; minimal diffs; avoid unrelated refactors.

## Security & Configuration Tips
- Use Maven Wrapper (`./mvnw`) and Java 17.
- For native builds, prefer container build or ensure GraalVM via `asdf`.
- Do not commit secrets; releases use GitHub Actions with repo-scoped tokens.

