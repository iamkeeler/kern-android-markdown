# Application polish: engineering review and execution packets

Status: implementation authorized by the user; execution in progress. This is the engineering execution companion to [the design review](application-polish-plan.md); its corrections and gates supersede conflicting implementation suggestions there. Progress and exact verification evidence are tracked in [the execution record](application-polish-verification.md). The accepted editor direction remains 16sp Medium, 1.6x body line height, 16dp top padding, and full-canvas focus.

## Review method and conclusion

Two independent senior Android/Kotlin reviewers started with fresh contexts, read repository guidance and source, and checked editor work and application/storage work separately. The integrating reviewer checked theme serialization, Material role mapping, dependency declarations, lint configuration, and current official OpenAI model guidance. No builds, instrumentation, or device walkthroughs were run during this review.

The visual direction is executable, but the original plan was not safe to dispatch as broad A–H assignments. It mixed presentation with storage correctness, named dormant rendering code as an active target, left focus measurement and operation ownership underspecified, and lacked shared-file coordination. Use the bounded packets below. Correctness work is a separate lane, not an incidental consequence of a styling task.

## Corrections established by source review

Locations are repository-relative and refer to this review's working-tree snapshot; rediscover symbols before editing.

| Finding | Evidence | Required change to execution |
| --- | --- | --- |
| Increasing field height does not cover external gutters | `EditorScreen.kt:390–410,819–855`: padding precedes the field, background clears focus, outer column scrolls | Use full-canvas native field decoration; put gutters inside that interaction area; measure a finite viewport before scrolling. |
| 680dp reading cap is ineffective | `EditorScreen.kt:821`: `fillMaxSize()` precedes `widthIn`, no centering wrapper | Correct constraints and test actual text bounds, while keeping the surrounding canvas focusable. |
| Companion preview is dormant | `CompanionPane()` has no production call sites; `EditorScreen.kt:540` recursively creates the second editor | Apply editor work once to the shared active path. Do not reactivate or restyle dormant preview code. `ReferencePicker` and fold controls in the same file are active. |
| Existing tests cannot prove promised geometry | `EditorScreenTest.kt:674` never scrolls or taps a visible line; toolbar test at 446 does not check IME/clearance | Replace weak assertions with real coordinates, visible-line anchors, real IME state, and measured toolbar/text bounds. |
| Linked-folder trash move is incorrect | `StorageManager.renameFile:385` discards destination parent for SAF; `FileOperationsManager.moveToTrash:254` records `.trash/...` | Block trash/Undo copy changes until backend move/restore semantics are characterized and safe. |
| Deletion success and Undo lifetime are unreliable | `FileOperationsManager.deleteNode:152` ignores false disk deletion; view models start independent five-second purge jobs; editor navigates before emitting Undo | Give deletion one session owner and one terminal decision; do not equate a snackbar event with successful restoration. |
| Access denied is treated as empty and can prune metadata | `StorageManager.listDirectory:173`; existing revoked-grant test at `StorageManagerTest.kt:82`; `MainScreenViewModel.scanProject:428` | Introduce explicit listing outcomes; never prune after failed/incomplete enumeration. Update the old test while preserving startup resilience. |
| Operation success is not available consistently | Browser create silently returns on failure; create-folder inserts metadata regardless of disk result; rename results are ignored | Establish naming/result contracts before retaining dialogs until success. UI callbacks must not invent success. |
| Workspace removal leaves stale application state | `MainScreenViewModel.deleteProject:582` removes Room rows without repairing active path/history/selection | Repair state separately from removal wording; disk data and shared URI grants remain untouched. |
| Theme export reads packed Color storage as ARGB | `SettingsScreen.kt:328–333` masks `Color.value.toLong()`; it also omits custom semantic colors/divider | Fix export with supported ARGB conversion and round-trip tests before promising theme import/export consistency. |
| Theme compatibility and actual roles were underspecified | `AppThemeJson` persists palettes; Material mapping supplies only a subset of roles; saved presets are JSON rows | Preserve old/custom JSON compatibility; test roles actually used, not only raw declarations; do not silently rewrite user themes. |
| Passing lint task can still contain errors | `app/build.gradle.kts:153`: `abortOnError=false`; release workflow separately examines reports | Require fresh report inspection and no new lint errors, plus the release policy for all existing errors. Exit code alone is insufficient. |
| Dependency stability cannot be assumed | BOM `2026.03.01`; explicit Material `1.5.0-alpha01`; Kotlin `2.3.20`; AGP `9.0.1`; min/target SDK 26/36 | Resolve APIs against installed dependencies. No dependency upgrades or framework migrations in polish. Flag unavailable APIs rather than pasting current-doc examples blindly. |

## Decisions implementers must preserve

### Editor metrics and interaction

- Editor text has its own token; shared application `body=13sp` stays unchanged. Keep saved multipliers 0.8/1.0/1.2/1.4 and Medium=1.0. Body sizes become 12.8/16/19.2/22.4sp; body line heights become 20.48/25.6/30.72/35.84sp before Android font scaling.
- Preserve current heading ratios 1.75/1.55/1.35/1.15 (H4–H6 share 1.15), code ratio 0.9, and existing weights/families. Line metrics must accommodate larger heading runs; do not force body-height boxes around them. Test actual `TextLayoutResult`, not only source strings.
- Preserve one document-level `TextFieldState`. Type/family/size and configuration changes must not write to source, reset selection, drop undo/redo, or replace a live editing session. Observe platform composition behavior; do not force a composition commit merely to change typography.
- Keep the current outer document scroll as the effective vertical scroll owner. Capture finite viewport constraints outside `verticalScroll`; use a minimum decorated height, not a fixed/maximum content height. Use a native `TextFieldDecorator`, calling its inner field once, to include top/side/bottom whitespace in the field's native hit/semantics area. Cap and center the inner text at 680dp using constraints that permit shrinking. Keep the toolbar outside that focus area at its current z-order.
- No transparent clickable overlay, pointer interception over text, whole-document replacement, intrinsic sizing, second effective vertical scroller, or duplicate IME padding. If the installed decorator API cannot satisfy a required gutter, return a concrete prototype result to the integrator before adding a documented margin-only fallback.
- Text and decoration taps retain the installed field's native nearest-offset placement, which can collapse a selection or put the caret at the end when tapping below short text. Do not add custom unconditional end-of-document movement. Any programmatic margin-only focus fallback preserves the current selection. Blank-canvas taps focus/show IME without synthesizing source text. Tests characterize native whitespace behavior before fixing expectations.
- Keep 220dp/100dp expanded/minimized scroll clearance and existing 16dp/24dp gutters. Move padding ownership as required for native focus without double-applying it.
- Preserve split eligibility: 720dp overall, 340dp per pane, existing user/system multiplier adjustment. Do not automatically multiply thresholds by 16/13. Validate 16sp at actual available pane width including spacers/gutters. Any availability change requires a separate product decision.

### Themes, settings, and navigation

- Apply new helper-text roles only to explanatory/caption text; do not globally enlarge `tiny`. Preserve current font families and surface-specific metric alignment unless a visual comparison supports a narrow adjustment.
- Built-in text roles require 4.5:1 on their actual rendered backgrounds; indicators require 3:1. Calculate alpha-composited colors too. Include on-accent text, muted descriptions, destructive text, switch track/thumb/outline states, menus, and root system bars. Decorative non-interactive elements and disabled controls are not falsely reported as active indicators.
- Keep old JSON imports working with defaulted additions only where necessary. Test legacy built-in preset JSON as well as new defaults. Do not identify built-ins solely by a display name shared by custom imports or silently migrate saved custom palettes. Built-in preset update policy must be explicit and tested; until identity is reliable, preserve stored palettes and use accessible presentation roles rather than rewriting rows.
- Each standalone/embedded Settings host owns its tab selection and per-tab scroll independently. Preserve on recreation; keep while its host exists, and allow a deliberately closed/reopened embedded host to start fresh. No accidental cross-editor global state.
- Search opens ready to type on explicit activation. Platform Back first dismisses the visible IME where Android owns that dispatch; the next Back closes search; only subsequent Back reaches existing root exit handling. Clear empties the query without closing; Close exits. Reset the root double-back latch when search consumes Back. Recreation preserves query/visibility but does not forcibly show the keyboard or steal focus from another route.
- Use available pane constraints for wrapping, never only whole-device width. Keep current navigation hierarchy, colors, bottom bar, tabs, and fold behavior.

### Names, operation state, and storage safety

- Naming is a leaf-name operation, not move/path entry. Reject blank trimmed names, `.`/`..`, NUL/control characters, and path separators; preserve Unicode and internal spaces. Do not invent a universal provider filename-length limit. Provider-specific rejections produce a recoverable error.
- Create retains the current `.md` default, recognizing existing `.md` case-insensitively so `.MD` is not doubled. Do not broaden creation to other extensions in polish. Rename displays the full existing name and initially selects the stem; it applies the explicitly submitted full name, preserving the extension by default without silently undoing deliberate extension edits. Existing `.markdown` and `.txt` names remain valid. Exact same-name rename succeeds as a no-op.
- Detect collisions in the operation layer immediately before commit, not only on text change. Never overwrite an existing document. Provider-normalized names/returned URIs must be read back and used for identity; do not assume the requested name was stored verbatim. Do not claim atomic collision protection across arbitrary SAF providers without evidence.
- Workspace display aliases are not filesystem leaf names: preserve their separate existing display-name policy and never rename a linked directory or regenerate an internal project path from an alias edit.
- Capture target project/path, original identity, draft, and request ID when opening a dialog. Use explicit Idle/Submitting/Failed/Succeeded state. Button and IME dispatch the same command; one in-flight request per dialog. Save draft/target at the host with `rememberSaveable` and an explicit primitive/custom saver; keep in-flight mutation/result ownership in the existing correctly scoped ViewModel. This avoids assuming current manual factories already supply a framework-restored `SavedStateHandle`. Do not add `SavedStateHandle()` as a default argument and call that restoration. If framework saved-state ownership is later selected instead, explicitly wire CreationExtras/factories, including the registered secondary editor.
- Restore draft/target through activity recreation and reconnect to the retained request. While submitting, dismissing presentation must not cancel an already committed disk operation; a late result cannot close a newer dialog or navigate to a stale target. On process recreation, formerly Submitting requests enter an interrupted/reconciliation state, never an endless spinner or an automatically replayed mutation; refresh actual storage/identity before permitting another submission.
- Close/navigation only follows confirmed disk/Room success. A storage failure leaves draft and relevant metadata intact. Treat partial disk/Room success as an explicit reconciliation case with actual identity; never replay the disk mutation blindly. Preserve coroutine cancellation; do not wrap cancellation in ordinary failure/empty results.
- Preserve the tested existing creation-destination rule when a project is supplied; do not reinterpret the active browser path as a UI cleanup. Characterize it in tests before altering signatures.
- Directory outcomes distinguish Success(empty/nonempty), AccessDenied, Missing, and Failure. Pruning requires a complete successful recursive scan whose project/mutation generation is still current at commit. Failure/cancellation anywhere must prevent destructive pruning and preserve existing index entries; startup still reaches usable UI. A compatibility list API may remain for callers that do not reconcile, but scan/picker paths must consume explicit outcomes. Do not rely on an API that itself silently maps provider failures to an empty list to establish enumeration success.
- Scope scan-generation ownership per project and synchronize generation validation with the metadata commit. Create/rename/move/remove and overlapping scan requests invalidate stale reconciliation; deleting a workspace prevents any late scan from re-inserting its rows. The commit must still verify project existence. A generation check outside the mutation/commit boundary is not sufficient to prevent races.
- Workspace removal applies to eligible linked workspaces. It removes the association/index only, repairs active/selected destination and history to a valid remaining/default destination, and leaves disk contents and URI grants intact. Releasing shared grants or removing the default sandbox is outside this pass.

### Deletion is a separate correctness lane

- Do not ship stronger Undo promises or change trash behavior as part of a copy-only task. First establish provider capabilities, actual move destinations, no-overwrite restore, and truthful permanent-delete results. A same-parent SAF rename is not a move. An unsupported safe move must fail without initiating destructive fallback; do not replace it with text read/write/delete, especially for folders or binary assets.
- A supported move/rename must return actual post-operation identity. If a multi-step provider operation partly succeeds, report the real location and reconcile metadata; never report an unchanged source or invent a `.trash` path. Keep recoverable data and test failure at each step. This lane may require more than a polish-sized change; the integrator can defer it without blocking independent visual packets.
- Proposed bounded lifetime: Undo is session-only, owned by one activity-owned ViewModel obtained above route entries and shared by browser, primary editor, and secondary editor. A plain `remember` coordinator is insufficient for rotation. One deletion receipt reaches one terminal action: restore or finalization. Purge must not race an offered/queued Undo or accessibility-extended snackbar. Presentation has its own receipt/lease identity: cancellation on rotation/background requeues the offer, rejects stale callbacks, and neither finalizes nor leaves it permanently Offered. Restoring a document refreshes the current destination without automatically reopening an editor or replacing a live draft.
- Process death loses the session-only Undo UI; completed moves with unfinalized trash remain on disk rather than being purged by a lost timer. This does not promise a persisted original-path receipt or automatic recovery UI. Retain current `.trash` browsing/scanning visibility; hiding it or adding cleanup is outside this pass. Death/cancellation during a provider operation is not transactional: preserve discoverable actual data, reconcile when accessed again, and do not claim guaranteed rollback or successful delivery to the coordinator. Persistent receipts, restart cleanup, and guaranteed cross-process Undo are separate follow-up work. Retain the current confirmation until this lifetime is accepted for implementation; document it in tests and copy. Restore collisions/failures keep trash recoverable and show an error; final deletion failures do not erase metadata or announce success.

## Model and reasoning recommendations

These assignments are engineering judgment for this repository, informed by current official guidance, not OpenAI benchmarks of Kern. Use model IDs supported by the current client and set effort explicitly for every dispatched packet.

- `gpt-6.1-sol`, `medium`: default implementation model for bounded Kotlin/Compose changes.
- `gpt-6.1-sol`, `high`: editor input/measurement, persistence, storage, lifecycle, and independent code review with interacting edge cases.
- `gpt-6-luna`, `low`: mechanical documentation/copy work only after behavior and wording are fixed; no storage, IME, or contrast policy decisions.
- `gpt-6-luna`, `high`: bounded evidence/report collection with an exact checklist. Low is adequate for routine report extraction; high is the documented explicit Luna starting point for agent work.
- `gpt-6-astra`, `medium`: optional independent final reviewer for the integrated cross-cutting change or escalation of an unresolved correctness design. Do not dispatch every styling packet to Astra.
- No default `xhigh`, `max`, or `ultra`: increase only when a representative failed task shows a quality benefit. Model/effort does not substitute for tests or review.

Basis: [OpenAI subagent guidance](https://learn.chatgpt.com/docs/agent-configuration/subagents) distinguishes demanding Sol work from narrow Luna work and cautions about parallel writers. [Model selection](https://learn.chatgpt.com/docs/model-selection) treats model/effort as a starting point to validate on representative tasks. [Reasoning effort guidance](https://developers.openai.com/api/docs/guides/deployment-checklist) recommends medium/high for diagnosis and code reasoning and reserving higher levels for measured gains. Consult these again when dispatching later; availability may change.

## Execution rules and file ownership

1. P00 is mandatory. Current code includes uncommitted editor/foldable changes. Record their exact baseline and preserve them. Never start an isolated checkout at HEAD that silently omits them. If isolation is requested, establish an agreed snapshot containing needed changes before branching.
2. The root integrator owns this plan, global documentation, sequencing, and final verification. Subagents get fresh contexts (`fork_turns=none` when supported), one packet, the accepted baseline, relevant guidance, allowed files, and dependency results. They reread source; line numbers are navigational aids, not stable contracts.
3. One writer per file. “Different features” is insufficient isolation: Settings packets all touch `SettingsScreen.kt`; browser packets share `MainScreen.kt`/`MainScreenViewModel.kt`; editor packets share `EditorScreen.kt`/`EditorViewModel.kt`; storage packets share the managers. Run these serially in a shared checkout. New helper/test files do not authorize simultaneous integration into their callers.
4. Safe parallel work is read-only review, independent test design, or disjoint files after the integrator confirms the write sets. Agents must not run Gradle simultaneously against the same checkout/device/results directories. One verification owner queues connected tests.
5. Each packet delivers one behavior and its tests in a reviewable diff. If an API change needs another file, request a write-set expansion from the integrator; do not quietly absorb another packet or fix unrelated defects. No public commits, releases, or dependency changes are implicit.
6. Test-only packets may prototype a failing case, but implementer owns final regression assertions too. Editor defects require evidence that the test fails against the faulty baseline. A packet remains integration-pending until all required final checks pass.

## Dispatch table

Prefix `P` is the polish lane; `S` is correctness work discovered during review. “Depends” is a code dependency, not permission to parallelize shared-file writers.

| ID | Single outcome | Depends | Model / effort |
| --- | --- | --- | --- |
| P00 | Baseline, toolchain, API and verification inventory | — | Sol / medium |
| P01 | Editor metric contract and tokens | P00 | Sol / high |
| P02 | Active editor typography and style ranges | P01 | Sol / high |
| P03 | Canvas geometry and native focus | P02 | Sol / high |
| P04 | Editor adaptive/interaction regression gate | P03 | Sol / high |
| P05 | Theme export/import round-trip correctness | P00 | Sol / high |
| P06A | Accessible role definitions and Material mappings | P05 | Sol / high |
| P06B | Apply accessible roles to active controls/text | P06A | Sol / medium |
| P07 | Settings row semantics/alignment | P06B | Sol / medium |
| P08 | Settings tab state and import submission | P07 | Sol / medium |
| P09 | Search focus, clear/close, and Back | P00 | Sol / high |
| P10 | Browser row/breadcrumb presentation | P06B, P09 | Sol / medium |
| P11 | Metrics/About/Settings adaptive presentation | P04, P07, P08 | Sol / medium |
| P12 | Accurate empty-state copy and existing actions | P09, P10 | Luna / low |
| S01 | Filename policy and truthful operation results | P00 | Sol / high |
| S02 | Browser create/rename dialog state | S01 | Sol / high |
| S03 | Editor rename state and active-path identity | S01, S02, P04 | Sol / high |
| S04A | Explicit directory-listing outcomes | P00 | Sol / high |
| S04B | Mutation-aware non-destructive scanning | S01, S02, S03, S04A | Sol / high |
| S05 | Browser/picker error recovery | S04B | Sol / medium |
| S06 | Linked workspace removal state/copy | S04B | Sol / high |
| S07A | Safe same-project move capabilities | S01 | Sol / high |
| S07B | Truthful trash/restore/delete results | S07A | Sol / high |
| S08 | Session deletion coordinator | S07B | Sol / high |
| S09 | Root Undo host and route integration | S08, S03 | Sol / high |
| P13 | Remaining operation copy and destructive styling | P06B; per completed flow: S02/S03/S05/S06/S09 | Luna / low |
| V01 | Independent integrated code review | selected packets | Sol / high; optional Astra / medium |
| V02 | Required tests, lint reports, release build, device QA | V01 and corrections | Sol / high; Luna / high for bounded report extraction |
| V03 | Reconciled guides and evidence handoff | V02 | Luna / low, integrator review |

P01–P04 can proceed independently of storage corrections. P05–P12 can proceed when shared-file ownership permits. S01–S09 are separate correctness packets: do not label them completed by changing messages. If those are deferred, omit dependent P13 portions and explicitly list unresolved behavior in handoff. No deletion feature or recovery promise ships partially integrated.

Conservative dispatch order in the shared checkout: P00 → P01 → P02 → P03 → P04 → P05 → P06A → P06B → P07 → P08 → P09 → P10 → P11 → P12. Then the separately selected correctness lane: S01 → S02 → S03 → S04A → S04B → S05 → S06 → S07A → S07B → S08 → S09 → P13. Finish the selected scope with V01 → V02 → V03. This is intentionally serial for writers; parallelize read-only reviews or genuinely disjoint work only after checking file locks. A practical four-slot limit is root integrator + up to two disjoint workers + one read-only reviewer, not four writers on shared screen files.

## Packet contracts

Paths below are relative to `app/src/main/java/com/attachdesign/kern/` unless they name repository documentation or tests. Referenced existing test classes are starting points, not proof of current coverage. Suggested new names describe required regressions; they do not claim tests exist.

### P00 — Baseline and API inventory

- Allowed writes: a new verification record under `docs/`; only test-runner configuration if discovery proves instrumentation cannot use the intended AndroidJUnitRunner, as a separately reviewed minimal prerequisite.
- Record branch/HEAD, preexisting diffs, installed JDK/SDK/AGP/Kotlin, resolved Compose foundation/Material versions, device availability, and the actual test runner in the merged test manifest. Inventory browser/route/secondary ViewModel owners, saveable composition hosts, manual factories, and CreationExtras before changing operation state. Inspect `dependencyInsight` if needed; no upgrades. Establish a reproducible baseline run and fresh lint report, separating preexisting failures from new ones.
- Deliver exact commands/results, failure triage, version/API availability for `TextFieldDecorator` and saveable tab state, and current phone/wide screenshots if available. No UI writer starts without an accepted snapshot. Missing emulator is explicitly a verification blocker, not a pass.

### P01 — Editor metric contract

- Writes: `ui/theme/ThemeModel.kt` or a narrowly scoped editor-metrics helper, font-setting reads in `EditorViewModel.kt`/`SettingsScreen.kt`, and new helper/VM tests. These narrow caller writes belong to this packet's lock set. Root integrates design-guide updates.
- Define the numeric metrics above once and preserve size keys/defaults. Normalize missing/malformed/non-finite values to 1.0; clamp finite out-of-range values to 0.8–1.4; preserve continuous in-range values rather than snapping them. Use this single normalization policy for Settings, actual editor metrics, and the multiplier passed to split eligibility. Do not write settings during composition.
- Tests: all preset sizes, heading/code ratios, NaN/infinity/malformed/default/boundary/continuous values and normalized eligibility inputs. No UI typography token changes.

### P02 — Typography consumers and projection

- Writes: active document field in `ui/editor/EditorScreen.kt`, `MarkdownDocumentOutputTransformation.kt`/`MarkdownSpanStyleProvider.kt` only as needed, rendering tests.
- Consume P01 consistently in all three view modes and both recursive editors. Include actual resolved metric dependencies in `remember` keys. Do not alter Markdown projection offsets/parser rules or dormant preview rendering.
- Acceptance: measured heading/inline/code runs have complete styling, no clipped baseline/line bounds, correct serif/sans/mono behavior, and expected RAW sizing. Source/newlines/trailing whitespace and selection/undo/redo survive size/family changes; verify save/reload of an exact plain-text fixture.

### P03 — Canvas geometry and focus

- Writes: document-mode canvas/field in `EditorScreen.kt`; focused `EditorScreenTest` cases. Preserve the legacy paragraph-mode branch.
- Implement the finite viewport/native decorator approach above; top text inset=16dp, centered inner text ≤680dp, full available hit area, current bottom scroll clearance, same scroll/IME ownership. No input overlay or auto-scroll-on-focus.
- Acceptance: coordinate taps at empty-canvas center, below a one-line document, top padding, side gutter, and wide side margin focus/show real IME and accept typing. Re-tapping after manually dismissing IME reopens it. Toolbar/menu taps do not trigger canvas actions or close IME. Drag scrolling and long-press/cross-paragraph selection remain native.

### P04 — Editor interaction/adaptive gate

- Writes: editor instrumentation/rendering tests and, through the integrator, `docs/editor-interaction-contracts.md`.
- Separate stable-viewport focus and IME-transition tests. Scroll a long fixture to a known visible line that remains clear of the settled keyboard/palette and is far enough from scroll-range limits to avoid clamping. Record that line's viewport position, tap its coordinate, and require the same anchor within 1dp converted to device pixels after settling. Do not compare only full-field top bounds or excuse arbitrary jumps as IME adjustment. Test necessary cursor-reveal scrolling separately. Verify last-line bounds above toolbar after scrolling, expanded/minimized, IME open/closed.
- Exercise primary and second editor independently, original split thresholds, size multipliers/system font settings, hinge bounds, compact suspension/restoration, selection/copy, syntax reveal, and focus after closing Settings. Add a `documentEditorEnabled=false` smoke case for prior background focus, paragraph editing, and toolbar routing, since the parent canvas is shared. Activity recreation preserves both drafts. Existing regression tests run too; no unrelated fold recognizer changes.

### P05 — Theme serialization fidelity

- Writes: narrow export/import helper in `ui/theme`, export caller in `SettingsScreen.kt`, new JVM tests.
- Use supported `Color.toArgb()` conversion, preserve schema-compatible color/alpha representation, and export every persisted theme field including custom danger/warning/info/success/header divider. Do not serialize derived presentation roles as if user declared them.
- Acceptance: known nonzero colors round-trip, both built-ins round-trip, old JSON imports, custom semantic colors survive, and malformed input does not claim success. No palette tuning in this packet.

### P06A — Accessible role definitions and Material mapping

- Writes: `ui/theme/ThemeModel.kt`, `theme/Theme.kt`/`Type.kt`, `ThemeAccessibilityTest` and narrowly scoped mapping/compatibility tests.
- Introduce minimal readable helper and accessible color roles; preserve persisted theme compatibility and root system bars. Resolve fallback roles used by existing controls instead of remapping every unused role. Compare actual old preset rows and custom themes; no name-based forced replacement. No screen rewrites in this packet.
- Acceptance: computed text/indicator roles satisfy repository requirements on intended surfaces; alpha backgrounds and on-primary pairs included. Legacy/custom JSON stays compatible and the saved-preset update policy is tested. Definitions alone do not complete accessibility delivery; P06B must wire and test actual consumers.

### P06B — Accessible role consumers

- Writes: specific text/control color and helper-type bindings in `SettingsScreen.kt`, `MainScreen.kt`, `EditorScreen.kt`; theme UI instrumentation. Hold all three screen locks; change no screen state/operation logic.
- Apply P06A roles to active muted/accent/destructive text and existing Material controls, including primary-colored counts and semantic marker colors. Enumerate bindings in the handoff so no name-based or global token replacement sneaks in.
- Acceptance: actual text/indicator contrasts pass on rendered surfaces, built-in appearance stays recognizable, and controls retain state semantics. Verify root-owned system-bar icon direction on both themes across main/editor/settings; no screen-local system-bar side effect. These tests validate consumers rather than duplicating constant-only unit assertions.

### P07 — Settings rows

- Writes: `ui/settings/SettingsScreen.kt`, new `SettingsScreenTest` or named Settings component tests.
- Standardize columns/spacing, use native row toggle/selectable semantics with passive child controls, preserve already-passive theme radios, wrap auto-pair actions with explicit state labels. Keep control visual style and tab structure.
- Acceptance: one focusable action per setting, one mutation per tap, all radio groups announced correctly, full effective touch bounds, descriptions wrap without colliding with controls at 2x fonts. No storage-result or tab-state refactor here.

### P08 — Settings state and theme import operation

- Writes: `SettingsScreen.kt`, its tests; a small state helper only if needed.
- Save active tab and per-tab scroll by stable tab identity in each host; do not hoist into one global singleton. Preserve JSON draft across recreation, grow input/error area, disable blank/invalid/pending submission, await database success before clearing draft/showing success, and retain on failure. No exported-color changes beyond P05.
- Acceptance: switch tabs at different scroll depths, recreate, reopen a different Settings host, double-tap Import, and inject DB failure. Verify one submission and no false `Theme applied` message. No creation of duplicate presets merely from tab restoration.

### P09 — Search focus and Back

- Writes: `ui/main/MainScreen.kt`, `MainScreenTest.kt`.
- Save query/visibility, focus only on explicit activation after composition, and implement the IME-first/then-search Back policy. Clear and Close remain distinct. Keep file-history buttons and root exit behavior outside search unchanged.
- Acceptance: typed input without extra tap, query clear keeps search, Back ordering with real IME, exit latch reset, configuration restoration, no focus steal on return from editor. Do not replace the search component or upgrade Material.

### P10 — Browser presentation

- Writes: browser row/header functions in `MainScreen.kt`, `MainScreenTest.kt`.
- Use consistent row type roles, preserve project casing, constrain breadcrumbs beside always-reachable actions, apply explicit filename/path overflow. Retain current icons; optional vector replacement is deferred. Menu labels can be descriptive while trigger stays compact.
- Acceptance: a long Unicode filename and deeply nested path at compact width/2x font keep Sort and overflow reachable; row/overflow perform distinct actions; no drag/file-history regression. No folder traversal or storage changes.

### P11 — Supporting layout

- Writes: `SettingsScreen.kt` (Stats/About/gutters), `MetricsTab`/`SidebarPane` in `EditorScreen.kt`; targeted instrumentation. Serialize with all other writes to these files.
- One outer padding owner, helper roles from P06A/P06B, wrap metric pairs based on available pane width, stack About metadata where needed, accessible link tap rows. Preserve existing tiles/alignment unless a matched screenshot demonstrates a local correction. Use 16dp compact /24dp wide as outer-gutter tokens, no broad 8dp-grid rewrite of existing 4/12dp tokens.
- Acceptance: phone/wide/embedded Settings, readability sheet/sidebar, long values/licenses, both themes, 2x font, no clipping. Existing metric calculations and navigation are unchanged.

### P12 — Empty-state instructions

- Writes: explicit string usages in `MainScreen.kt` and affected assertions only.
- Replace stale bracketed control references, use state-specific concise text and an existing relevant action. Preserve Sync/privacy/product claims. Do not advertise new storage recovery or Undo before dependent correctness work.
- Acceptance: empty workspace/folder/search states each name the control/action actually present. Existing actions still reach their original destination.

### S01 — Naming policy and operation outcomes

- Writes: small naming/result types in `data/storage`, `StorageManager.kt`/`FileOperationsManager.kt` naming methods, tests. No trash/move rewrite.
- Implement the leaf-name decisions above, expose real success/failure/partial outcome identities, prevent overwrite, preserve cancellation, and own disk/Room partial reconciliation inside the operation layer. Add missing create-file/folder orchestration to the existing `FileOperationsManager` rather than introducing a parallel repository. S02 will migrate the browser callers and remove their unconditional metadata insertion; until then these new contracts do not fix the old UI call path. Workspace display aliases remain a separate database-only operation. Use provider doubles or a test DocumentsProvider rather than only mocked success booleans.
- Acceptance: existing destination, same-name no-op, `.MD`, existing `.markdown`/`.txt` rename, separators/dot names, Unicode, permission denial, provider normalization, false disk result, and DB failure after disk success. Partial outcomes preserve discoverable actual data and are not retried blindly. Existing source contents remain exact.

### S02 — Browser naming dialogs

- Writes: `InputDialog.kt`, browser dialog state/callers in `MainScreen.kt`/`MainScreenViewModel.kt`, their tests. Depends on S01's stable result contract.
- Preserve current destination policy. Delegate file/folder creation/rename to S01's operation APIs and remove duplicate/unconditional Room writes from the VM. Keep workspace alias validation/results separate. Add initial full name/selected stem for Rename and explicit operation state/target/request identity. The composition host MUST save draft/target/identity; its scoped VM retains mutation/results. Retain input/errors until success; one command for IME/button. Remove optimistic immediate dismissal only after correct result wiring.
- Acceptance: create/rename file/folder and workspace-alias flows, no Room insertion on failed create, double submission, failure then correction, rotation while pending, stale late result after new dialog, success navigation only once. Draft/target survive recreation; committed work is not canceled by presentation disposal. Process-restored pending state is reconciled without auto-replay or indefinite loading. Test factories/hosts prove actual restoration rather than testing an unregistered `SavedStateHandle`.

### S03 — Editor naming integration

- Writes: `EditorScreen.kt`/`EditorViewModel.kt`, `Navigation.kt`/`NavigationKeys.kt` and a narrow session-key helper only if required, editor/navigation tests; reuse S02's dialog component. Hold navigation and both editor locks.
- Save the active draft before rename; update editor identity from the actual result without reloading over live text. Reconcile the navigation key/session bookkeeping as needed after inspecting existing re-entry/secondary-editor logic. Do not assume changing only `activeFilePath` fixes a stale route key.
- Acceptance: failed rename keeps dialog/draft, success keeps selection/undo and subsequent saves target the new file, recreation/back/re-entry does not reopen the old path or replace its retained VM. The visible primary/secondary pair cannot collide on one file; a global registry across all back-stack entries is outside this bounded change. Add editor contract records and full release verification.

### S04A — Explicit listing outcomes

- Writes: listing result/helper in `data/storage`, `StorageManager.kt`, storage tests. Inventory every list caller, including methods whose provider wrapper already swallows errors. Add an explicit outcome API; keep a compatibility API until callers migrate.
- Separate complete successful enumeration from empty/failure; preserve cancellation. Do not infer success from a swallowed provider exception. Do not change callers or scan pruning in this packet.
- Acceptance: genuine empty/nonempty success, revoked grant, null provider listing, missing folder, partial enumeration failure, cancellation, then successful retry. The existing revoked-permission compatibility test remains meaningful; add explicit-outcome assertions. API availability alone is not a completed recovery fix.

### S04B — Mutation-aware scan reconciliation

- Writes: scan/list callers and mutations in `MainScreenViewModel.kt`, a narrowly scoped project-generation/commit helper in `data/storage`, generation hooks in `FileOperationsManager.kt`, and storage/VM race tests. Enumerate mutation hooks, including create/rename/move/remove, before dispatch; no concurrent writers to these files.
- Consume S04A's outcomes and build a complete scan before pruning. Guard generation/project existence in the same synchronization boundary as metadata commit. Overlapping/late scans, canceled/failed enumeration, and concurrent mutations must not prune fresh entries or resurrect removed projects. Keep startup usable on access failure.
- Acceptance: nested failure/cancellation, newer scan finishing first, create/rename/move during enumeration, workspace removed before commit, and successful retry. S06 depends on this contract and invalidates generations on removal; do not rely only on display-request IDs. No search/dialog presentation work here.

### S05 — Recovery presentation

- Writes: browser recovery/error paths in `MainScreen.kt`/`MainScreenViewModel.kt`, active `ReferencePicker` in `CompanionPane.kt`, `EditorViewModel.listReferenceFiles`, relevant tests. Dormant `CompanionPane()` stays untouched.
- Consume S04A/S04B outcomes and provide existing Refresh/reselect paths for errors; keep true-empty messaging distinct. Preserve selected project/path, ignore stale asynchronous results, and keep the same-file guard with an explanation. Route share/folder-link failures through existing transient feedback without false success; results belong to the retained request, not an already disposed dialog.
- Acceptance: denied and restored permissions, retry/reselect, project switch during load, filtering to supported documents, and no index pruning after failure. Safe caller recovery is tested end-to-end.

### S06 — Workspace removal

- Writes: `MainScreenViewModel.kt`, removal caller/copy, browser/VM tests.
- Apply `Remove workspace` only to eligible linked associations. Leave files and grants alone, clear stale history entries, repair selection/current destination atomically, and invalidate S04B's project generation before removal so late loads/scans cannot commit. Keep relinking possible.
- Acceptance: remove active/inactive linked workspace, back/forward after removal, recreation, duplicate URI association, and relink; disk files remain byte-identical. Default sandbox removal and permission-release ownership stay outside scope.

### S07A — Provider-safe move capability

- Writes: a same-project safe-move result/capability API in `StorageManager.kt` and focused provider/filesystem tests. Do not silently rewrite the unrelated generic cross-project move API.
- Characterize internal filesystem and SAF native move support. Return actual destination identity for supported operations or unsupported failure before mutation. Keep binary/folder data intact; forbid generic text copy/delete fallback. Explicitly represent partial move/rename outcomes and actual location.
- Acceptance: provider parent/URI assertions (not just rename success), nested folders/binary asset checksum, unsupported native move, denied grant, rename-after-move failure, and existing destination. No phantom `.trash` identity or claimed rollback after partial mutation. No UI or Room reconciliation changes yet.

### S07B — Truthful trash/restore/delete results

- Writes: `FileOperationsManager.kt` trash/restore/final-delete methods, narrow S07A integration, focused disk/Room tests. Independent integrator review required before exposing UI promises.
- Consume S07A actual identities, make trash names collision-resistant and still check destination existence, prohibit overwrite on restore, and reconcile Room on partial outcomes. Final delete false/exception must return failure with metadata retained; an already absent file is a separately characterized idempotency case, not a disguised permission error.
- Acceptance: collisions both into trash and on restore, same-timestamp operations, nested descendants, partial move/disk-success plus DB failure, actual-provider normalized identity, rejected restore, false final-delete result, and recoverable error state. No success/Undo event is created for unsupported/partial results that have not been reconciled.

### S08 — Deletion session coordinator

- Writes: a small activity-owned operation ViewModel/coordinator above route scope and deterministic JVM tests using fake time/results. Do not change screen UI or introduce a persistent database receipt schema in this packet; S09 wires the actual owner.
- Implement session-only receipt and presentation-lease identities and serialized state transitions, surviving route changes/configuration. No independent fixed five-second purge job races the snackbar. Queued/offered/undoing/finalizing/failed operations have one owner; a canceled presentation lease requeues, rejects stale callbacks, and is not dismissal. Unsupported/partial backend outcomes retain actual identity/reconciliation status instead of entering the success receipt queue. State the process-death retention limitation explicitly.
- Acceptance: queued deletes, delayed/extended presentation, Undo exactly once, duplicate stale callback, restore conflict/failure, finalization failure, cancellation/background, and coordinator recreation semantics. No operation can both restore and purge.

### S09 — Root Undo integration

- Writes: `Navigation.kt`, `MainScreen.kt`/`MainScreenViewModel.kt`, `EditorScreen.kt`/`EditorViewModel.kt`, the S08 coordinator integration, `ui/common/SnackbarEvent.kt` only if its payload contract must change, and browser/editor instrumentation. This packet is serialized with all navigation/editor/browser writers.
- Obtain one activity-owned S08 ViewModel above entries and inject it into browser/primary/secondary deletion paths. Host deletion feedback outside the departing editor, deliver reconciled receipts, and remove the old route-scoped purge jobs. Drive terminal actions from a matching active presentation result; requeue canceled presentation on rotation/background. Keep unrelated snackbar behavior and layout intact; position feedback clear of IME/palette using existing inset ownership.
- Acceptance: delete from browser and both editors, leave route immediately, rotate, queue another delete, use accessibility-long snackbar timing, Undo failure, and background/return. Unsupported SAF deletion leaves source intact, names/keeps the target and reports actionable failure with no success/Undo offer. Partial outcomes show actual-location recovery and never silently enter ordinary success. Undo refreshes data without replacing another active editor's draft or reopening a deleted document automatically. Do not promise behavior across process death.

### P13 — Result-aware action copy

- Writes: specific confirmation/menu/error strings and destructive color bindings in completed flows plus their assertions; no operation changes.
- Match actual remove/delete/Undo behavior, name the target, and use accessible destructive roles. Only update messages for correctness packets that have passed; do not mask deferred defects with reassuring wording.
- Acceptance: each message is supported by an operation-result test, Undo is reachable where advertised, errors name a real recovery action, and no new product/privacy/sync claims are introduced.

### V01–V03 — Review, verification, and handoff

- V01 is a fresh-context, read-only review against the accepted baseline and selected packet contracts; reviewer is not the author of the risky change. Report bugs/test gaps with verified source locations. Root resolves findings before V02.
- V02 has one verification owner. Run the matrix below, inspect results rather than relying on command completion, and gather matched screenshots. Optional Luna report extraction must use the exact report paths/checklist and may not declare the change complete. Correct failures in their owning packet, then rerun affected checks plus required integrated editor checks after the final fix.
- V03 is documentation only: reconcile stale design-guide palette/type prose with actual accepted tokens, append editor regression records, map each packet to changed files/test names/results, list deferred S packets and device limitations. Root reviews for accuracy. No release or publication is implicit.

## Verification matrix and stop conditions

At minimum, run the repository's required commands on the final integrated snapshot:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

For PR/release handoff, also satisfy the broader `contributor-guides/coding-standards.md` checks (`test`, `lint`, `bundleRelease`) and existing release policy, without running publication/signing-upload jobs. Do not invent credentials or alter signing to claim a release check passed.

- Inspect fresh `app/build/reports/lint-results-*` for errors because `abortOnError=false`; compare to baseline, and apply the existing CI no-errors release rule. Missing or stale reports fail evidence collection. Test result counts, skipped/failed cases, runner/device identity, and exact revision belong in handoff. No-device/zero-tests is not an instrumentation pass.
- Real IME assertions must use a keyboard-capable test device with the relevant test configuration recorded/restored, not a mocked focus flag. Wait for actual layout/IME state; no fixed sleeps as test synchronization. Coordinate targets derive from measured bounds and density, and avoid toolbar/system areas.
- Regression tests for source/state/operation results accompany implementation. Do not add screenshot-only or constant-mirroring tests and call them behavior protection. Use fake clock/provider failures where needed and a real test provider for SAF path/capability semantics.
- Directed device coverage, not an unbounded Cartesian matrix: API 36 compact phone at 1x and 2x fonts in both themes; a landscape/1.3x pass for keyboard and wrapping; wide tablet/foldable panes including actual 340dp and threshold suspension/restoration; API 26 smoke for edited native controls/input/storage paths. Match expected thresholds to the unchanged layout policy. RTL/long Unicode filenames receive a focused pass.
- Exercise empty/short/long Markdown, headings, lists, quotes, inline/fenced code, images/tables as source in the editable field, and exact plain text. Verify both keyboard states, toolbar sizes, cross-paragraph copy, undo/redo, activity recreation, and independent editor saves. Physical hinge sensing/haptic/audio checks are recorded separately; emulator layout checks do not prove physical gesture behavior.
- Keep before/after capture conditions matched: build snapshot, document, theme, font, density, viewport, IME. Inspect one representative batch; make one consolidated correction/confirmation round. Functional failures still require fixes and renewed verification; the visual-pass budget is not permission to waive release contracts.
- Stop a dependent packet if its prerequisite API/data contract is missing, if existing changes cannot be safely preserved, or if required verification is unavailable. Continue independent authorized work and report the specific blocker. Never mark all polish complete while dependent correctness packets are only partially wired.

## Copy-ready dispatch template

```text
Implement only packet <ID> from docs/application-polish-execution-plan.md.
Use <model-id> with reasoning effort <effort>; start from a fresh context.
Workspace: <absolute accepted checkout path>. Baseline: <revision/snapshot and preexisting-change manifest>.
Read AGENTS.md, contributor-guides/ai-agent-workflow.md, applicable local skills,
and the relevant packet decisions/tests. For editor work, read the full interaction contracts.
Prerequisites accepted: <packet outputs/API decisions>. Allowed writes: <explicit files>.
Preserve existing edits. Do not change dependencies, storage semantics outside the packet,
public copy claims, or another packet's files. No other writer owns these files now.
Implement the smallest change and meaningful regression tests; show the prior failure where applicable.
Use the shared verification queue; do not start competing Gradle/device runs.
If a prerequisite or safe ownership is missing, report it and do not guess a broader implementation.
Return: changed files, behavior restored, test names, exact commands/results,
baseline failures versus introduced failures, source/selection/data preservation evidence,
remaining limitations, and any required integration follow-up. Do not claim release completion.
```

Review limitations: source-backed review only; no current emulator/device was connected in the preceding design review, and availability must be rechecked in P00. The reviewers did not clear this chat's history; their isolated contexts provide the requested fresh assessment. Only planning documents were edited.
