# Application polish plan

Status: planning only. The editor direction below was accepted for documentation; implementation has not been authorized. Application-wide recommendations below are proposed for review, not accepted product requirements.

Engineering review: use [the execution packets and safety gates](application-polish-execution-plan.md) before implementing this design direction. That review corrects the active companion target, specifies editor measurement and native whitespace-tap behavior, separates storage correctness from polish, and defines task dependencies, file ownership, model/effort recommendations, and verification. Its technical corrections supersede conflicting suggestions below.

## Scope and design intent

Refine Kern's existing appearance through readable text, consistent spacing, clear controls, and considerate feedback. Preserve its current fonts, palette, restrained writing canvas, navigation structure, and feature set. Preserve unrelated working-tree changes.

This is a future-work plan, not a record of completed fixes or verified interaction contracts.

## Accepted editor direction

Source review found that the active document editor uses `theme.typography.body` (13sp) for Medium, although the design guide describes a 15sp writing baseline. Its line height is 1.55 times the body size. The text field fills width but sizes vertically to its text; its surrounding canvas explicitly clears focus when tapped.

| Property | Current implementation | Planned refinement |
| --- | --- | --- |
| Medium body text | 13sp | 16sp editor-specific baseline |
| Body line height | 1.55x, approximately 20sp | 1.6x, approximately 26sp |
| Top canvas padding | 4dp | 16dp |
| Horizontal gutters | 16dp phone / 24dp wide single pane | Retain |
| Focusable writing area | Content-height text field | Full visible writing canvas, including empty/short documents |
| Bottom scroll clearance | 220dp expanded / 100dp minimized toolbar | Preserve |

Implementation outline:

1. Introduce an editor-specific typography value rather than enlarging the shared UI body token. Keep Medium as the default, retain persisted size multipliers, and scale heading/code presentation proportionally. Apply it to both active editors through their shared `EditorScreen`; the dormant companion preview is deferred. Honor Android font scaling.
2. Increase top padding, standardize body line height, and verify centered reading bounds capped at 680dp. Preserve the author's Markdown paragraph separators and existing bottom toolbar clearance.
3. Give the native text field at least the visible writing-canvas height without clipping long documents or removing bottom scroll clearance. Remove canvas tap-to-clear-focus behavior from the document-editor path. Prefer native text-field sizing/decoration before introducing a margin-only focus fallback. Preserve native text taps, cursor placement, selection, drag scrolling, and IME ownership. Native whitespace taps may place the caret at the nearest offset; any programmatic margin-only focus fallback preserves selection and must not intercept toolbar controls or native text gestures.
4. Add coordinate-based instrumentation for taps in empty documents, below short text, and in gutters, asserting focus, IME visibility, and typing. Strengthen checks for viewport preservation, selection, toolbar clearance, saved size preferences, and large system fonts. Tests must fail against the previous behavior where applicable.
5. When implemented, record the focus regression's symptom, root cause, invariant, and automated test in `docs/editor-interaction-contracts.md`; update the design guide to match the accepted typography.

Relevant implementation: `ui/editor/EditorScreen.kt`, `ui/editor/MarkdownDocumentOutputTransformation.kt`, `ui/editor/MarkdownSpanStyleProvider.kt`, `ui/editor/CompanionPane.kt`, `ui/settings/SettingsScreen.kt`, and `ui/theme/ThemeModel.kt` under `app/src/main/java/com/attachdesign/kern/`.

## Application-wide review

Reviewed the file/workspace browser and search, create/rename/delete flows, editor header and palette, readability sheet/sidebar, standalone and embedded Settings (Appearance, Behavior, Stats, Sync, About), split workspace/file picker/fold-feedback controls, root Material theme, system bars, navigation, and existing UI/theme tests.

Evidence: current source plus the six historical device captures in `website/screenshots/`. Those captures show an older toolbar, file-row treatment, settings tabs, and readability presentation. They establish visual character, not current layout correctness. No live app walkthrough or current dark/tablet capture was available. Source-confirmed findings and proposed geometry checks are distinguished below. No browser-based detector is applicable to the native Compose UI.

### Design assessment

The warm canvas, book-like type, restrained accent, and uncluttered document surface already establish a welcoming writing environment. Native Material top bars, list items, menus, and bottom sheets provide a sound foundation. The highest-value improvements are readable secondary text, consistent component treatment, predictable small interactions, and copy that accurately describes the action.

### Recommended first pass: readability and trustworthy interactions

**A. Readable secondary text and complete theme roles**

- Source-confirmed: Settings helper text and many metric captions use the 11sp `tiny` token. Small accent-colored labels and muted text are used on both background and surface. `ThemeAccessibilityTest` covers primary text and accent indicators, but not these text-role pairs.
- A direct luminance calculation from current built-in hex declarations gives approximately 4.40:1 for light muted text on background, 3.98:1 on surface; accent text gives 4.00:1 / 3.62:1 in light mode and 3.85:1 / 3.51:1 in dark mode. These miss this repository's 4.5:1 text requirement even where accent passes the 3:1 indicator requirement. Hemingway yellow/cyan counts are especially faint on the light surface (approximately 1.33:1 / 1.43:1).
- Plan: introduce a readable helper/caption role around 13sp with explicit line height; reserve 11sp for genuinely incidental metadata. Use primary text for suggestion counts and retain colored markers. Tune muted text minimally and distinguish accent text from indicator roles only where needed. Keep the cream, charcoal, and rust appearance.
- Complete Material color-role bindings for menus, dialogs, switches, indicators, and supporting text so unconfigured roles do not inherit unrelated fallback tones. This is a consistency risk identified from the partial `toColorScheme()` mapping; actual visible fallback drift requires live confirmation. Preserve root-owned system bars and existing intentional dark bottom chrome.
- Files: `ui/theme/ThemeModel.kt`, `theme/Theme.kt`, `theme/Type.kt`, `ui/settings/SettingsScreen.kt`, `ui/editor/EditorScreen.kt`; extend `ThemeAccessibilityTest` to actual text-role/surface pairs and interactive states.

**B. Settings rows that align and behave consistently**

- Source-confirmed: preset theme rows use Material `ListItem` with internal padding, while view/font/toggle rows start at the content edge. Settings toggles and most radio rows expose both a clickable row and an independently interactive child; the fold-feedback dialog already uses a single native toggleable row with a passive switch.
- Plan: align section labels, row labels, descriptions, and trailing controls to consistent columns; use 8dp within groups, 16dp around sections, and 24dp between major groups as starting values. Keep existing divider treatment. Reuse the fold dialog's single-action pattern for switches and native selectable-group semantics for radio rows. Make helper text wrap independently of the control.
- Source-confirmed: one scroll state is shared by all Settings tabs and the active tab uses non-saveable state. Keep separate remembered scroll positions per tab and preserve the active tab through recreation.
- Plan: allow the five auto-pair buttons to wrap at narrow widths/large fonts and give them descriptive selected-state semantics. Verify effective 48dp touch bounds; compact visual bounds alone do not prove undersized Compose hit targets. Grow the fixed-height theme JSON field as needed so error/supporting text remains visible. Disable Import for blank/invalid input rather than offering an inert action. Remove the ambiguous `(Default)` suffix from both theme names while retaining the selected radio state.
- Files: `ui/settings/SettingsScreen.kt`, with the existing pattern in `ui/editor/CompanionPane.kt`.

**C. Create and rename dialogs that respect the user's effort**

- Source-confirmed: shared `InputDialog` always initializes to an empty string, including Rename. It does not specify single-line naming, a keyboard confirmation action, or validation beyond blank input. Dialog callers dismiss immediately, and some operation failures have no visible recovery.
- Plan: prefill the current name for Rename, preserve its extension, and select the editable name. Use one-line fields, native keyboard actions, and the same validation for keyboard/button submission. Keep typed input and show an actionable inline error when an existing storage operation fails; close only on confirmed success. Avoid adding a new confirmation step.
- Files: `ui/main/dialogs/InputDialog.kt`, `ui/main/MainScreen.kt`, `ui/main/MainScreenViewModel.kt`, `ui/editor/EditorScreen.kt`, `ui/editor/EditorViewModel.kt`. Verify blank/duplicate/invalid names, keyboard submission, extension handling, failure retention, and long names against the actual storage policy.

**D. Clear deletion and workspace-removal language**

- Source-confirmed: confirmation copy says deletion cannot be undone, but file deletion uses a temporary trash move with an Undo snackbar. Workspace deletion removes database references rather than deleting the linked directory. Delete styling also differs between menus, sheets, dialogs, and statistics reset.
- Plan: use `Remove workspace` and explicitly say files remain on the device for that operation. Describe document/folder deletion according to actual behavior, identify the target by name, and retain confirmations for destructive operations. Use a consistent accessible destructive text role. Confirm Undo is reachable and performs restoration before promising it in copy; the editor currently navigates away before emitting its Undo event, so that path needs explicit lifecycle/host testing.
- Files: browser/editor confirmation and overflow components, `MainScreenViewModel.deleteProject`, `deleteNode`, and `EditorViewModel.deleteCurrentFile`. Do not change deletion semantics as a cosmetic side effect.

### Recommended second pass: spacing, browsing, and visual consistency

**E. File rows, search results, and breadcrumbs**

- Source-confirmed: active file/project/search rows use Material `ListItem`, but fonts and weights are applied differently; filenames and paths have no explicit overflow policy. The breadcrumb and action group share a fixed-height row, and every path segment is rendered without width allocation. Project breadcrumb text is forced to lowercase.
- Plan: share row text roles, icon footprint, baselines, and metadata spacing between browsing/search. Keep Material row structure and the overflow action. Preserve filename/project casing. Give long names a clear maximum line policy and ellipsis; let supporting paths wrap within reserved space. On narrow screens, constrain/scroll the breadcrumb independently so Add workspace and Sort remain reachable. Verify the row height at large fonts before choosing a fixed size.
- Show full sort names in the menu (`Name A–Z`, `Name Z–A`, `Recently modified`) and announce the current choice while keeping the compact trigger. Name each overflow action for its item in accessibility semantics.
- Optional final detail: replace file/folder emoji and the sidebar's text `✕` with matching Material vectors, keeping footprint and hierarchy. This changes a recognizable visual detail and should receive a before/after review; it is lower priority than layout and interaction fixes.
- File: `ui/main/MainScreen.kt`; sidebar close control in `ui/editor/EditorScreen.kt`.

**F. Search that opens ready to type**

- Source-confirmed: opening search changes visibility without requesting input focus; the root BackHandler always handles app exit rather than dismissing search. The trailing Close control always exits search.
- Plan: focus the search input and show the keyboard on opening. Provide a clear-query action when text exists, retain an explicit close path, and make Back close active search before the existing root behavior. Keep file-history back/forward separate. Preserve query/search state through recreation. Test ordering with the keyboard and predictive Back on-device.
- File: `ui/main/MainScreen.kt`; extend `MainScreenTest`.

**G. Welcoming empty, loading, and failure states**

- Source-confirmed: empty states refer to `[+ workspace]`, `[+ file]`, and `[+ folder]`, although current controls use `Add workspace` and icon buttons. The `[ empty ]` decoration also appears for no search matches. Some share/folder-picker failures only log or handle success. The companion file picker converts load errors to an empty list.
- Plan: use short instructions that match visible control names and one contextual action (`Create a document`, `Add workspace`, or `Clear search`). Reserve decorative metadata for where it adds meaning. Distinguish inaccessible folder/load failures from truly empty folders and offer Retry or Choose another folder. Use the existing SnackbarHost for actionable failures and preserve input; avoid routine success messages after every edit.
- Reuse one restrained native loading treatment and spacing across readability/file picking; prefer an existing linear indicator or a simple content placeholder without adding decorative animation. Keep the current Sync feature status accurate and quiet; changing feature promises is outside this pass.
- Files: `ui/main/MainScreen.kt`, related view models, `ui/editor/CompanionPane.kt`, `MetricsTab`, and theme import feedback.

**H. Readability, Stats, About, and split-pane finishing**

- Source-confirmed: Stats uses left-aligned monospace values at 26sp with small labels, while Readability uses centered values with different type sizes/padding. Both use paired tiles, and About places long library names beside unbounded license strings. Readability adds its own 16dp horizontal padding when nested inside the already padded sidebar.
- Plan: retain current tiles but align their caption roles, numeric styles, and internal spacing. Stack paired metrics when content/font scale no longer fits. Use one owner for outer content padding so the same Readability content does not acquire an extra inset in a sidebar. Allow About details to move below labels at narrow widths, and give its text links a full comfortable tap row.
- Keep standalone Settings and embedded Settings visually related, using a common content gutter rule (16dp compact / 24dp wide as the starting point) and a readable maximum width on large standalone windows. Preserve each editor's own IME inset and focus. Explain why the current file is unavailable in the split picker, and use consistent filename overflow behavior across both editor headers and the picker.
- Files: `ui/settings/SettingsScreen.kt`, `MetricsTab`, `SidebarPane`, `ReferencePicker`, and editor headers. Large-font clipping and tablet balance remain validation targets, not observed live failures.

### Lower-priority finishing and documentation

- Verify existing entry/reveal/overscroll motion with Android Remove animations enabled. The editor uses a 0.3-to-1 navigation scale, a separate 500ms reveal, and custom overscroll. Simplify only if live review shows redundant movement or ignored duration settings; preserve the established overscroll contract and avoid adding animation.
- Reconcile stale palette/type descriptions in `contributor-guides/design-guide.md` with the actual cream/charcoal/rust tokens and accepted editor scale during implementation. The older white/blue prose must not prompt a palette redesign.
- Keep the existing screen hierarchy, bottom action bar, tabs, quotes, themes, storage model, fold behavior, and feature availability. Do not add onboarding screens, decorative cards, new sounds, or new feature work as part of polish.

## Proposed delivery sequence and acceptance

1. Editor readability, spacing, and focus from the accepted direction above.
2. Secondary-text roles, contrast, settings alignment, and single-action semantics (A–B).
3. Name-entry, action copy, and reliable operation feedback (C–D, G).
4. Browser/search consistency and adaptive details (E–F, H).
5. One visual QA round across representative device classes, then a single consolidated correction/confirmation round. Keep optional icon and motion changes out unless the evidence supports them.

Acceptance: the app still feels like Kern at first glance; readable text passes the repository's contrast requirements; touch targets and spoken state are unambiguous; long names/large fonts do not displace actions; keyboard, Back, and selection remain native; failed actions retain a clear recovery path; confirmations describe actual storage behavior. Capture before/after screenshots from the same build context and document/font/theme settings so comparisons are meaningful.

## Verification required during implementation

Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` and `./gradlew :app:connectedDebugAndroidTest`. Run only one connected-test invocation at a time. Visually inspect phone and foldable/tablet layouts in both orientations, light/dark themes, and keyboard-open/closed states, including large Android font settings. Editor changes are incomplete until all applicable interaction contracts are satisfied.

Add targeted coverage to `MainScreenTest` for search focus/Back, deep breadcrumbs, empty-state actions, and name-entry/recovery. Add Settings instrumentation for tab-state restoration, single toggle/radio semantics, long helper text, auto-pair wrapping, and theme import errors. Extend view-model/storage tests for the existing operation results that drive feedback, and theme tests for the real text roles and surfaces. Use coordinate-based assertions for hit areas instead of relying solely on semantic clicks. Check TalkBack traversal and 1.0x/1.3x/2.0x system fonts on compact phones and a foldable/tablet; physical fold-gesture checks remain separate from emulator layout verification.

Review limitation: no device or emulator was connected during the planning review. Existing repository screenshots are historical visual references; source is authoritative for current behavior. No application code, tests, or existing interaction contracts were changed as part of planning.
