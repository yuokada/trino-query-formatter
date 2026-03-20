# GitHub Copilot Instructions

Follow these repository instructions when working in this project.

## General guidance

- Keep changes focused and consistent with the existing Java 17, Quarkus, and Picocli CLI architecture.
- Write all new or updated repository instructions, code comments, and documentation edits in English.
- Avoid machine-specific paths, local-only assumptions, and environment-dependent behavior unless explicitly required.
- Preserve the current command-line interface and output behavior unless the task explicitly changes user-facing behavior.
- Keep diffs small and avoid unrelated refactors.

## Project context

- Production code lives under `src/main/java/io/github/yuokada`.
- Tests live under `src/test/java` and test resources live under `src/test/resources`.
- Sample SQL files live under `src/main/resources/queries`.
- Test fixtures (including SQL files) live under `src/test/resources/queries`.
- Maven Wrapper is the standard entry point for build and test commands.

## Validation

- Prefer `./mvnw validate` for style and structural checks.
- Prefer `./mvnw test` for unit and integration-relevant test coverage in the default profile.
- If a change affects packaging or runtime behavior, consider `./mvnw package` as an additional verification step.
- Clearly separate checks you executed from checks you did not execute.
