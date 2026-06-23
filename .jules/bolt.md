## 2024-03-24 - Markdown Parser List Detection Optimization
**Learning:** In the `MarkdownParser.kt`, using regular expressions (`Regex.containsMatchIn`) to detect list line formats creates a major bottleneck when parsed iteratively line-by-line during document processing and typing, due to regex object overhead and slow evaluation.
**Action:** Replace `Regex` matching with custom character-by-character scan loops (`while` or `for` loops analyzing `String[i]`) for high-frequency parser checks. Testing showed character loops are >20x faster.
