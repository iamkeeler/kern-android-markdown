# Reviewer's Journal

## 2026-06-29 - Directory Renaming DB Sync Anti-Pattern
**Learning:** In local-first or SAF folder indexing, renaming a directory changes the physical path of all nested files. Leaving child rows in the database with their old relative paths breaks queries and causes cache drift.
**Action:** Always execute a cascading path update in a single database transaction block when renaming or duplicating directory nodes.
