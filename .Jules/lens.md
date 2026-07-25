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

## 2026-06-23 - Dialog Actions and Modal Hierarchy Audit
**Learning:** Tapping workspace settings and create dialogs shouldn't distract from the core editor layout. Button layout inside alert confirmations should keep a low visual profile (using standard `TextButton` borders/tint) to maintain page-flatness, relying on button color alignment only for the main confirm CTA (`theme.accent`).
**Action:** Retain simple flat `TextButton` boundaries for overlay dialogs to prevent elevation collisions with floating main buttons.

## 2026-06-23 - Swipe Row Content Gutter
**Learning:** Under the cardless layout system, sliding list items horizontally (e.g. to reveal actions like Delete, Edit, Share) can cause information elements placed on the extreme edges (such as file sizes or metadata) to collide directly with the action strip buttons. Establishing a minimum end-gutter of `theme.dimensions.spacingMedium` on the sliding content wrapper maintains proper typography boundaries even under full horizontal offsets.
**Action:** For swipeable or sliding lists, always enforce end-padding on the sliding item content Row to keep meta details separated from background actions.

## 2026-07-24 - Monospace Header Action Rhythm & Touch Boundaries
**Learning:** In breadcrumb file rails, text action triggers rendered in `FontFamily.Monospace` (such as sort mode or file actions) can suffer from visual weight dilution if horizontal padding matches standard body text. Applying explicit tracking (`letterSpacing = (tiny * 0.05).sp`) and trimming excess horizontal padding prevents layout displacement while keeping section label hierarchy intact.
**Action:** Use fixed tracking steps for inline monospace text buttons to distinguish actionable controls from static VFS path segment labels.

## 2026-07-24 - Landing Showcase Dual-Pane Workspace Alignment
**Learning:** Mirroring a mobile app's core editor on a landing website requires strictly adopting the app's exact design specifications — 35/65 dual-pane explorer ratio, 680px text canvas constraint, 1.6x line height, `JetBrains Mono` typography, and breadcrumb header path rendering (`Project › notes › draft.md`). Matching these invariants eliminates visual dissonance between product previews and runtime app experience.
**Action:** Always map web interactive mockups directly to app `design.md` layout boundaries and typography tokens.

