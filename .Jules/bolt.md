# Bolt's Journal ⚡

## 2026-06-07 - Single-Pass Regex Matching for Inline Editor Analysis
**Learning:** Running multiple independent regex scans over large text documents during user editing loops causes significant CPU overhead. Compiling a single unified regex pattern utilizing alternative branches `\b(word1|word2|...)\b` allows the matching engine to process all matches in a single $O(N)$ document scan, reducing regex search overhead proportionally to the number of search terms.
**Action:** Always combine search term patterns into a single unified regex matcher when looking up dictionaries or simple word lists in active editor analysis paths.
