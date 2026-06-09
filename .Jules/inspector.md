## 2026-06-09 - Escaped String Templates in Generated Code
**Learning:** Automated code generation or refactoring tools can mistakenly escape Kotlin string templates (e.g. `\${variable}`) to prevent interpolation in their own templates, leaving literal backslashes in the committed codebase that compile successfully but fail at runtime.
**Action:** Run a search or static analysis check for escaped string templates `\$` in Kotlin files during code reviews.
