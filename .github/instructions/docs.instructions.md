---
applyTo: "README.md,pom.xml,.github/workflows/**/*.yml,.github/workflows/**/*.yaml"
---

When editing documentation, build metadata, or workflow files in this repository:

- Keep examples, version references, and command snippets consistent with `pom.xml` and the actual CLI behavior.
- Use portable commands and avoid references to local absolute paths or developer-specific environments.
- When changing build or release behavior, preserve the Maven Wrapper based workflow unless there is a clear reason to change it.
- If user-facing CLI behavior changes, update `README.md` in the same change.
