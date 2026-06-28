## 2026-06-21 - [Regex Overhead in Text Parsing Loops]
**Learning:** In the `MarkdownParser`, identifying line types (like lists) via `Regex` (`containsMatchIn`, `find`) introduces significant overhead due to pattern compilation, match state tracking, and object allocation when executed on a per-line basis during document ingestion.
**Action:** Replace `Regex` evaluations in high-frequency text parsing loops with precise manual character scanning logic, prioritizing direct char matching (`line[i] == '-'`, `line[i].isWhitespace()`) for massive throughput gains without sacrificing correctness.
