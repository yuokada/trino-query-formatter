---
applyTo: "src/main/java/**/*.java"
---

When editing Java source files in this repository:

- Preserve the existing package layout under `io.github.yuokada`.
- Keep command handling in the Picocli command layer and reusable SQL logic in dedicated core classes.
- Target Java 17 and existing Quarkus conventions already used in the project.
- Follow the repository formatting and style expectations enforced by Checkstyle, including explicit imports and readable line lengths.
- Prefer focused changes that do not alter CLI semantics unless the task explicitly requires it.
