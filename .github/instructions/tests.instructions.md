---
applyTo: "src/test/java/**/*.java,src/test/resources/**/*.sql,src/test/resources/**/*.txt,src/main/resources/queries/**/*.sql"
---

When editing tests or SQL fixtures in this repository:

- Keep test inputs and expected outputs easy to read and representative of real Trino SQL usage.
- Prefer extending existing test coverage near the affected behavior instead of adding disconnected tests.
- Update golden files or SQL fixtures only when the intended formatter or analyzer behavior changes.
- Preserve deterministic assertions so tests remain stable across environments.
