# Writing continuity verification

Date: November 17, 2026. Local implementation on top of `790a80e` and the existing uncommitted save-protection/capture phase. No shared build was distributed.

## Scope

- Constant native canvas clearance prevents scroll-range clamping when the palette collapses. Explicit collapse is saveable within the writing session.
- The saveable native viewport follows stable document identity. Loading another document clears history; same-document reentry and rename preserve it.
- Compact panes show Undo directly, with Italic and Redo in More. Native touch targets, disabled states and non-focusable formatting menus remain intact.
- A pure Kotlin typing engine shares pair definitions with the legacy engine. The active native editor observes heading spacing, master/per-pair switches and keyboard capitalization preferences. Helper edits remain local and share the native Undo transaction.
- A delegating public input observer and pass-through clipboard receiver preserve composition and paste. SDK-specific overloads are guarded. No reflection or replacement text-input implementation is used. The experimental API exception is recorded in the interaction contracts.
- Settings copy and bundled/Play release notes describe the resulting behavior. Exact Markdown and cursor-local reveal remain unchanged.

## Code and coverage

- `ui/editor/EditorScreen.kt`: hoisted document viewport, saveable palette choice, native typing preferences and compact history controls.
- `ui/editor/EditorViewModel.kt`: stable viewport identity and load-time history boundary.
- `ui/editor/DocumentInputSession.kt` and `MarkdownDocumentInputTransformation.kt`: native edit-origin protection and transactional input helpers.
- `parser/DocumentTypingEngine.kt` and `MarkdownEditorEngine.kt`: pure typing rules and shared pair configuration.
- `ui/settings/SettingsScreen.kt`: result-oriented preference labels.
- `EditorCanvasFocusTest`, `EditorTypingPreferencesTest`, `EditorScreenTest`, `QuickCaptureNavigationTest`, `SettingsScreenTest`, `DocumentTypingEngineTest`, `MarkdownDocumentInputTransformationTest` and `EditorViewModelTest`: interaction, preference, parser and session regression coverage.

## Regression evidence

Before the phase-two fixes, the new default-pairing and heading-spacing unit tests failed. Device tests reproduced menu-only compact Undo, forgotten palette collapse after state restoration, and a 120dp final-line jump on collapse. Logs: `/private/tmp/kern-writing-phase2-baseline-unit.log` and `/private/tmp/kern-writing-phase2-baseline-device.log`.

Native Enter after a composing word initially inserted a newline without the next list prefix. The retained device test covers that failure and composing-newline preservation. Enter is dispatched asynchronously by native input; the transformation handles its committed line break without interpreting it as ongoing composition.

One intermediate composition assertion was corrected: a changed committed heading may receive spacing after composition ends; unchanged commits invoke no transformation and defer spacing to the next edit. This is distinct from rewriting active composing text, which stays exact.

## Required verification

- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` — passed. Unit XML: 341 tests, zero failures/errors/skips. Debug lint: zero Error/Fatal issues, 68 warnings. Local release APK assembled successfully. Final build log: `/private/tmp/kern-writing-phase2-verified-matrix.log`.
- Focused native typing suite — 8/8 passed, including composing-word Enter. Both synchronized Undo/browser regressions also passed together (2/2).
- `./gradlew :app:connectedDebugAndroidTest` — passed: 85 tests on Medium_Phone_API_36 (Android 16 / API36), zero failures/errors/skips. Full final log: `/private/tmp/kern-writing-phase2-settled-device.log`. The final run includes the explicit IME teardown and preference-fixture focus described below.
- Manual API36 phone inspection used the final debug APK and real Gboard: immediate empty-note focus, disabled/enabled direct Undo, overflow Italic/Redo and retained keyboard, trailing collapsed palette, and unchanged short-note text position. Screenshots: `compact-empty.png`, `overflow.png`, and `collapsed.png` under `/Users/iamkeeler/.codex/visualizations/2026/11/17/01a1e7be-2a14-7730-a9ee-f9bb56297e2c/kern-writing-continuity/`.
- App/test/assets source manifest: `/private/tmp/kern-writing-source-manifest.json`, SHA-256 `8151f90e5584296f1e1c44246a05c05414f414af0f5b687c5e8e9a914e878e50`. Documentation is outside this source manifest.
- `git diff --check` — passed. The owned temporary emulator was shut down after verification.
- Play release notes are 445 characters. No version allocation, commit, upload or shared distribution was performed.

## Emulator profile

An intermediate full run passed 84/85 tests and timed out in the existing top-padding real-IME check. Logs show native focus and Gboard's input view starting, followed by Android's IME visibility-animation timeout. The final run restarts the owned read-only API36 emulator and uses window/transition/animator scales of zero; real Gboard remains enabled. The manual visual pass used normal animations. Repeated rapid activity replacement also exposed Android IME-control leash timeouts. The focus fixture now explicitly hides the real keyboard and awaits hidden insets before closing its old window, so the next activity cannot inherit an unfinished handoff. No app behavior or test assertion was weakened for this environment issue.

## Test synchronization

The final matrix exposed an existing unit fixture using Unconfined Main while real IO settings collectors resumed concurrently. The affected fold-metrics test now recreates its model with serial Main scheduling, matching Android's UI-thread behavior. The direct-Undo device test waits for the real IME before entering text and checks native Undo immediately, avoiding interaction during keyboard startup. A file-browser test now targets its file row explicitly while Rename closes, and checks all matching ellipsized labels instead of assuming a unique filename text node.

The preference fixture explicitly focuses the native field after document readiness, then captures its input connection. It no longer depends on automatic startup focus, which is covered separately by the real-IME quick-capture tests. This separates preference assertions from activity/input-session startup timing.

## Practical limits

- Emulator tests establish Android API36 behavior, including real software-keyboard focus/clearance and native input-connection composition. They do not establish every third-party keyboard, dictation engine, physical foldable, TalkBack traversal or minimum-SDK runtime.
- Capitalization is a keyboard request; it does not force source uppercase. Arbitrary keyboards can send pasted/dictated content as ordinary character commits without exposing its origin.
- Palette restoration uses normal saveable state. This phase does not add durable reading-position restoration across process death.
- Existing R8/Kotlin metadata warnings remain; dependencies were not upgraded in this change.

See [editor interaction contracts](editor-interaction-contracts.md) for invariants and named regression coverage, and [phase-one verification](quick-capture-verification.md) for save protection and immediate capture.
