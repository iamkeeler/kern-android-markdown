# Lens's Journal - Critical IA & Visual Hierarchy Learnings

This journal tracks unique visual hierarchy and information architecture insights specific to the Modern Android Markdown Editor.

## 2026-06-07 - Cardless Layout Grouping in Sidebars
**Learning:** Under the strict "No Cards/Dividers" design constraint, separating layout sections inside narrow views (like sidebars) cannot rely on background cards. Visual grouping is instead achieved through proportional vertical space step increments (e.g., 8dp vertical padding within items, 16dp spacing between related controls, and 24dp sections) and typographic weight hierarchy (e.g., small bold labels for headings, uppercase monospace for metadata values).
**Action:** Lay out controls directly on the primary `theme.surface` backing without enclosing card boundaries. Use text elements and margins to establish visual blocks.

## 2026-06-07 - Unified VFS Navigation State
**Learning:** Linking the Project Explorer VFS state directly to the editor's active document state simplifies dual-pane layouts. Tapping breadcrumb directories on single-pane and large-pane displays benefits from utilizing the same ViewModel queries, ensuring navigation transitions always load and highlight neighboring folder siblings instantly.
**Action:** Share VFS directory-drill flows between side rails and overlay sheets using unified ViewModel flows.

## 2026-06-08 - Settings Screen as a Book of Chapters
**Learning:** The settings screen used `spacedBy(24.dp)` between sections, but radio rows already carry `12dp` vertical padding — leaving only ~24dp of *perceived* gap between the last row of one section and the header of the next. This is the same rhythm as content *inside* the section, so the eye cannot find the chapter breaks. Section headers at 11.sp monospace were also smaller than 15.sp body text — typographically inverted. The fix: `spacedBy(40.dp)` for paragraph breaks, `14.dp` under each header, `12sp`/`1.5sp` letter-spacing for the headers, and consistent `13dp` row padding throughout.
**Action:** For any list-of-sections screen: inter-section gap must be at least 1.5× the intra-section row height. Section headers must be visually larger (size or tracking) than body text, even if they are muted — hierarchy is about weight, not just color.

## 2026-06-08 - Floating Actions and Core Task Hierarchy
**Learning:** Multiple stacked floating action buttons of the same size and styling (e.g., "+ Folder" and "+ File") dilute the primary CTA. If the user's core task is to write a document, creating a file must hold the highest visual weight. The eye shouldn't have to read labels to know what to do next.
**Action:** Extend `MinimalOutlinedButton` with an `isPrimary` prop to allow solid accent backgrounds for primary actions, leaving secondary actions as low-emphasis ghost buttons.
