## 2024-06-27 - MarkdownParser Regex Bottleneck
**Learning:** Checking lines for lists in `splitDocument` using multiple `Regex` instances (`containsMatchIn`) adds huge overhead in the high-frequency parsing loop, slowing down overall editor responsiveness as the document grows.
**Action:** Replace `Regex` objects with manual character-by-character scan loops (e.g. `String.length`, indexing, `.isWhitespace()`) in `MarkdownParser` and other high-frequency text processing routines to gain ~94% speed improvement for those functions.
