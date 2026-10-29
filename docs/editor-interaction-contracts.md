# Editor interaction contracts

This document records user-visible editor behavior that must survive refactors. Every fixed editor regression should leave behind both an updated contract here and an automated test.

## Required behavior

### Typing must not crash

- Markdown containing full-block or inline images must remain measurable while rendered, focused, and edited.
- Do not place `IntrinsicSize` measurement above `SubcomposeLayout`-based content such as `SubcomposeAsyncImage`, lazy layouts, `BoxWithConstraints`, or tabs. Compose does not support intrinsic measurement for those children.
- Size decorative elements from the measured parent or draw them with a modifier instead of forcing intrinsic measurement.
- Regression coverage: `EditorScreenTest.testInlineMarkdownImageCreatesRenderedImageNode`.

### Selection crosses paragraph boundaries

- Active editing uses one document-level state-based `BasicTextField` and `TextFieldState` so Android's standard selection handles can expand across paragraphs.
- Copying a selection must preserve document paragraph boundaries without introducing extra blank lines.
- Do not put a `LazyColumn` or other virtualized layout inside the document `SelectionContainer`; selection behavior for uncomposed text is undefined.
- Regression coverage: `EditorScreenTest.testCrossParagraphSelectionCopiesParagraphBoundary`.
- Symptom and affected release: selecting a word in rendered editing could select the full document.
- Root cause: `MarkdownDocumentOutputTransformation` replaced the complete document in one operation, collapsing Compose's source-to-presentation offset mapping into a document-wide replacement range.
- Behavioral invariant: selecting a visible word maps to only its corresponding Markdown range (including an adjacent hidden structural marker when needed); Markdown token removal must use local edits rather than a whole-document replacement.
- Automated test name: `EditorScreenTest.testRenderedEditorSelectsOnlyTheRequestedWordRange`.

### Document text is the source of truth

- `TextFieldState.text` contains the exact Markdown source and owns selection and IME composition while editing.
- Parsed paragraphs and presentation ranges are derived, disposable indexes; they must never be joined to overwrite newer field text.
- `OutputTransformation` may change styling and presentation but must not change persisted Markdown.
- Images and tables remain visible as Markdown source in the editable field because native text fields cannot host arbitrary block composables without replacing native selection behavior.
- Regression coverage: `MarkdownDocumentScannerTest`, `MarkdownDocumentPresentationPlannerTest`, and `EditorScreenTest.testTypingStressWithRichBlocksPersistsExactText`.

### Focus preserves the viewport

- Tapping a visible paragraph must focus it without aligning it to the top or replacing the current scroll position.
- Do not call `scrollToItem` merely because focus changed.
- Do not request that an entire paragraph be brought into view on every text or selection update. Let the text field keep its cursor visible and use IME/window insets to reserve obscured space.
- Symptom and affected release: when scrolling to the end of a long document, the final line was trapped beneath the floating formatting bar, and focusing the editor on OEM devices panned the header off-screen.
- Root cause: `DocumentEditorField` lacked bottom scroll clearance within its scrollable container, and `android:windowSoftInputMode="adjustResize"` was declared on `<application>` rather than `<activity>`.
- Behavioral invariant: the editor scroll range includes clearance so the bottom line can scroll completely above the floating toolbar without clipping the text field layout. The TopAppBar header stays permanently anchored under the status bar, and all screens use a unified 2.dp `theme.headerDivider`.
- Regression coverage: `EditorScreenTest.testFocusingVisibleParagraphPreservesViewport`, `ThemeAccessibilityTest.testDefaultLightThemeContrast`.

### Rendered Markdown must not fall back to source syntax

- Symptom and affected release: Markdown source was visible in the document canvas for styles that should have been rendered, making the live editor read like a raw text field.
- Root cause: the scanner/parser accepted only a narrow set of line shapes and the presentation test coverage asserted source state instead of transformed layout text.
- Behavioral invariant: rendered mode hides recognized Markdown syntax while preserving the exact source in `TextFieldState`; the visible layout retains the author’s text and its semantic styling.
- Automated test names: `MarkdownRenderingRegressionTest.supported markdown fixture has a rendered projection for every text style` and `MarkdownRenderingInstrumentedTest.renderedEditorTextStripsMarkdownSyntaxButRetainsWriting`.
- Regression update: lists, task lists, block quotes, fenced code blocks, and inline code must use the same shared document projection as the standalone renderer. The projection may change presentation only; `TextFieldState.text` remains exact Markdown source.

### Formatting palette remains available until explicitly collapsed

- Symptom and affected release: the formatting palette was a horizontally scrolling toolbar with a separately painted collapse area, leaving part of the control visibly opaque and hiding actions off-screen.
- Root cause: the palette combined a scroll fade, a trailing background panel, and action buttons in one row.
- Behavioral invariant: while the editor is active, the primary Markdown actions are visible in one compact floating row. Less-frequent actions are available from its overflow menu. Applying a primary action never closes the palette; only the explicit collapse control may do so.
- Automated test name: `EditorScreenTest.testFloatingFormattingToolbarInteraction`.
- Regression update: the palette is one opaque, centered row. The editor content owns keyboard resizing; the palette must not inherit duplicate IME padding or leave a colored band that obscures the document.

### Selecting formatted writing reveals its source syntax

- Symptom and affected release: the document-level editor kept Markdown tokens hidden after selecting formatted text, so a selected construct could not be inspected or edited as source.
- Root cause: its output transformation stripped syntax unconditionally and did not consider the native text-field selection.
- Behavioral invariant: rendered mode hides inactive Markdown syntax, but selecting or placing the caret inside an inline construct reveals that construct's source markers while other constructs remain rendered. The floating formatting palette shares the editor canvas's single IME inset and stays above the keyboard.
- Automated test name: `MarkdownRenderingInstrumentedTest.renderedEditorTextStripsMarkdownSyntaxButRetainsWriting`.

### Markdown lists continue without corrupting source

- Symptom and affected release: pressing Enter in the document-level editor inserted a plain newline because list continuation existed only in the retired paragraph editor path. Formatting commands could also stack incompatible list markers.
- Root cause: list parsing, continuation, and toolbar conversions used separate marker rules, while the document-level `TextFieldState` had no input transformation.
- Behavioral invariant: a single Enter continues supported unordered (`-`, `*`, `+`), ordered (`.` and `)`), and task lists using their existing indentation and marker; new task items are unchecked. Empty list items exit without retaining source markers. Inline formatting remains exact source text; fenced code and thematic breaks never continue as lists. Formatting converts list prefixes instead of stacking them.
- Automated test names: `MarkdownEditorEngineTest.continuation preserves every supported list marker`, `DocumentEditEngineTest.checklist conversion replaces existing bullet and ordered markers`, and `EditorScreenTest.testDocumentEditorContinuesMarkdownList`.

### Active theme controls system bars

- Symptom and affected release: after edge-to-edge was enabled, light themes could show dark system bars because system-bar appearance was updated only by Settings.
- Root cause: system chrome was not owned by the active application theme.
- Behavioral invariant: every screen applies the active Kern theme to the status and navigation bars. Light themes use a light background with dark icons; dark themes use a dark background with light icons.
- Automated test name: `ThemeAccessibilityTest`; add emulator coverage when system-bar test infrastructure is available.

### Tapping list commands starts lists on empty and blank lines

- Symptom and affected release: tapping the bullet list or task list toolbar action on an empty line had no effect.
- Root cause: `DocumentEditEngine.transformLine` dropped line commands on blank lines with an unconditional `if (line.isBlank()) return null`.
- Behavioral invariant: toggling unordered bullets, checklists, or headings on a single empty/blank line inserts the appropriate Markdown prefix (retaining any leading whitespace indentation) and places the cursor at the end of the prefix. In multi-line paragraph selections, blank separator lines between paragraphs remain un-prefixed.
- Automated test names: `DocumentEditEngineTest.toggle bullet on empty line creates bullet prefix and positions cursor`, `DocumentEditEngineTest.toggle bullet on blank line preserves indentation and positions cursor`, `DocumentEditEngineTest.toggle checklist on empty line creates task prefix and positions cursor`.

### Rendered Markdown styles span full text bounds without offset drift

- Symptom and affected release: when tapping `H` or viewing rendered Markdown, heading and inline styles were truncated by a few characters, leaving trailing characters at normal body size.
- Root cause: `MarkdownDocumentOutputTransformation` passed already-transformed block and span coordinates into `SourceOffsetMap.map()`, double-subtracting delimiter lengths.
- Behavioral invariant: output transformation applies block and inline styles using original AST source ranges mapped cleanly through the source-to-buffer offset map. All rendered characters of headings and styled spans receive full, exact styling without character clipping or offset drift.
- Automated test names: `MarkdownRenderingInstrumentedTest.renderedEditorTextStripsMarkdownSyntaxButRetainsWriting`, `DocumentEditEngineTest.set heading on empty line creates heading prefix and positions cursor`.

### Floating toolbar menus keep soft keyboard visible

- Symptom and affected release: tapping the overflow menu or heading level dropdown on the floating formatting toolbar dismissed the soft keyboard.
- Root cause: `DropdownMenu` defaulted to `PopupProperties(focusable = true)`, which transferred window focus away from `BasicTextField`.
- Behavioral invariant: formatting palette dropdown menus use non-focusable popup properties (`PopupProperties(focusable = false)`), allowing the underlying `BasicTextField` to retain input focus and keep the IME active.
- Automated test name: `EditorScreenTest.testFloatingFormattingToolbarInteraction`.

### Collapsed floating toolbar docks to the right edge

- Symptom and affected release: when minimized, the formatting FAB was centered at the bottom of the screen instead of docking at the trailing edge.
- Root cause: parent animation container enforced `Alignment.BottomCenter` across both expanded and minimized states.
- Behavioral invariant: when expanded, the floating formatting toolbar is centered along the bottom edge above the keyboard; when minimized, the floating expand FAB docks at `Alignment.BottomEnd` with margin padding.
- Automated test name: `EditorScreenTest.testFloatingFormattingToolbarInteraction`.

### Code block and inline code formatting span entire contents uniformly

- Symptom and affected release: fenced code blocks rendered with uneven ragged line highlights with missing styles on closing lines/braces.
- Root cause: `MarkdownDocumentOutputTransformation` stripped trailing newlines from `CODE_BLOCK` projection strings, causing a discrepancy with AST token replacements and triggering whole-block fallback replacement.
- Behavioral invariant: code blocks and inline code are projected cleanly via AST token replacements. Monospace font styling and background colors span the exact bounds of all code block lines (including closing braces) and inline code spans without truncation or offset mismatch.
- Automated test name: `MarkdownRendererTest.fenced code hides fence and language but leaves code literal`.

### Floating formatting toolbar operates on high Z-index with generous document scroll clearance

- Symptom and affected release: the bottom lines of text in the editor could not be scrolled clear of the floating toolbar and keyboard.
- Root cause: `bottomPadding` in `DocumentEditorField` was too small (`spacingTitan` / 70dp) or bound directly to the text layout rather than the outer scroll container, preventing the document from scrolling past the floating bar.
- Behavioral invariant: `BasicTextField` content scrolls through the full canvas behind the floating toolbar (`zIndex = 1f`). The document editor area provides generous bottom scroll clearance (`220.dp` expanded, `100.dp` minimized) inside a `verticalScroll` container so any line of text at the bottom can be scrolled comfortably above the floating toolbar into open view.

### Readability and metrics modal bottom sheet

- Symptom and affected release: tapping the analytics/readability button displayed an empty sidebar pane with missing counts and no analysis in document editor mode.
- Root cause: `SidebarMode.METRICS` was unhandled in `SidebarPane`, and `EditorViewModel` read only `paragraphs.items` rather than the active document text in `documentTextFieldState`.
- Behavioral invariant: tapping the analytics icon opens a Material 3 `ModalBottomSheet` on single-pane devices (or sidebar pane on dual-pane devices) populated with the readability grade, word count, character count, sentence count, reading time, and full Hemingway suggestions breakdown.
- Automated test name: `EditorViewModelTest.toggleSidebar in METRICS mode calculates readability metrics from document text`.

### Undo and Redo actions in floating toolbar

- Feature and release: added dedicated Undo and Redo actions to the floating formatting toolbar.
- Behavioral invariant: Undo and Redo buttons reflect the transactional state of `TextFieldState.undoState` (`canUndo`, `canRedo`). Inactive states are disabled with dimmed opacity (`0.35f`), active states are fully interactive. Applying formatting commands or editing text immediately enables Undo.
- Automated test name: `EditorViewModelTest.undo and redo modify documentTextFieldState correctly`.
- Narrow-pane regression: the fixed eight-action row could place the collapse control outside a phone or split writer pane. The root cause was fitting eight 48dp controls plus spacing into less than their required width. Retain the six primary formatting/menu/collapse controls in one compact row with native touch targets; expose dedicated Undo/Redo menu items with their enabled states in overflow when the full history row cannot fit. Wide panes retain history buttons in the row.
- Regression coverage: `EditorScreenTest.testFloatingFormattingToolbarInteraction` (also reproduced on the original commit), plus `testCompanionTogglePreservesDraftSelectionUndoAndFocus` for split-pane operation. The editor's bottom clearance remains unchanged.

### Elastic document overscroll bounce-back and end indicators

- Feature and release: elastic overscroll and spring bounce-back when scrolling past top or bottom of a document.
- Behavioral invariant: scrolling or dragging past the vertical document boundaries applies non-linear rubber-band resistance. Top and bottom boundary elastic indicators stretch dynamically in width and opacity using `theme.accent` (Utility Blue). Releasing or settling flings triggers fluid spring bounce-back (`Spring.DampingRatioMediumBouncy`, `Spring.StiffnessMediumLow`) to zero offset. Hardware-accelerated `graphicsLayer` translation is used without invalidating text measurement, selection handles, or IME composition.
- Automated test names: `ElasticOverscrollTest`, `EditorScreenTest.testElasticOverscrollBouncePreservesEditorState`.



### Foldable companion preserves the writing session

- Feature: book-style foldables and sufficiently wide windows expose a Split view command and a second native editor with a file picker. Diff is follow-on work.
- Behavioral invariant: each document has its own `TextFieldState`, selection, undo history and save path. Split open/close, sidebar substitution and width changes preserve the primary draft; the picker prevents two editors from writing the same file. Switching the second file saves its current draft before loading another.
- Repeating the command restores the previous sidebar. The second document survives hiding or compact-window suspension. Each pane owns its IME inset; the editor toolbar retains its clearance.
- The manual command requires >=720dp and >=340dp per pane, with minimum pane width increasing for large fonts. Vertical separating hinges are excluded from pane bounds. Horizontal folds do not enable this feature.
- The shortcut is initially off and requires a standard angle sensor and a resumed editor. An observed flat baseline, deliberate brief bend, return to flat, and cooldown produce one toggle. Initial unfolding, sustained postures, full closure, horizontal folds, modal UI, and lifecycle interruption must not toggle. Transient vertical geometry changes do not cancel a gesture. Never interrupt pointer selection or either editor's IME composition to commit.
- Platform deviation: a custom recognizer implements the explicitly requested physical toggle. Native editing/selection/focus, Material 3 supporting-pane layout/animation, system haptics, and disabled-animation behavior remain platform-owned.
- Symptom: on Pixel 10 Pro Fold, a partial fold moved the writer to the left with a blank right half and the reopening gesture failed to toggle the layout. Identified contributing causes: the scaffold excluded the hinge even for a single pane, and the recognizer restarted on changing fold geometry or window bounds. Physical-device confirmation is still required. Invariant: a bend alone retains the full-width writer; a completed deliberate gesture toggles the split even when vertical geometry changes during the bend. Coverage: `EditorScreenTest.testInjectedHingeGestureTogglesCompanionExactlyOnceEachWay`.
- Automated coverage also includes `FoldGestureRecognizerTest`, `EditorViewModelTest.split restores prior sidebar without changing document state`, `EditorScreenTest.testCompanion*` and `testSecondEditorSavesIndependentlyAndRetainsDraft`.

### Fold feedback follows the committed action and device preferences

- Feature: the fold shortcut may pair its native confirmation haptic with StudyCompanion's short paper-crease sound. Haptics default on, optional sound defaults off; each preference is persisted independently. A Try feedback control previews the selected combination without changing layout or document state.
- Behavioral invariant: a successful fold/reopen produces exactly one feedback request with current preferences. Initial unfolding, cancellation, background/resume, raw angle changes, and manual Split view must not produce gesture feedback.
- Haptics use `View.performHapticFeedback(CONFIRM)` on API 30+ (native clock-tick fallback below), without ignore-setting flags or direct vibrator access. Device touch-feedback settings and intensity remain platform-owned; no vibration permission is added.
- Paper audio is preloaded only while opted in, never loops, never queues pending gestures, and never changes system volume or requests audio focus. Silent/vibrate mode, any DND filter other than ALL, disabled system touch sounds, zero/muted system volume, unknown policy, unready/failed loads, and disposed helpers suppress audio. Pausing stops active playback; disposal releases resources and stale callbacks cannot revive them.
- Automated coverage: `TactileFeedbackHelperTest`, `EditorScreenTest.testFoldFeedbackPreferencesPersistAndPreviewDoesNotChangeLayout`, and `testInjectedHingeGestureTogglesCompanionExactlyOnceEachWay` (two commits, exactly two feedback requests; lifecycle cancellation adds none).

### Re-entering an open document does not reload over live state

- Symptom: activity/composition recreation could reload from disk and overwrite unsaved source, selection, and undo during foldable rotation or window adaptation.
- Root cause: the screen's load effect executes again on recreation; `EditorViewModel.loadFile` previously replaced the buffer even when that same project/file was already loaded.
- Behavioral invariant: loading the already-active document is idempotent; repeated requests during its initial load share the pending job. Layout/configuration changes preserve the live document session.
- Automated test: `EditorViewModelTest.reentering same file preserves live text selection and undo history`.
- Regression update: returning to the active document while a different document is loading cancels that pending load. Otherwise its late completion could replace the live draft. Coverage: `EditorViewModelTest.returning to active file cancels a different pending load`.

### Companion image resolution follows the displayed document

- Symptom: switching between a reference and preview with identical inline image markup could retain the previous document's image.
- Root cause: remembered inline content captured its resolver and document key without using them as remember dependencies.
- Invariant: inline images resolve in the context of the displayed document on every document switch. Companion projection reuses each parsed block and its image spans on a background dispatcher; displaying a large companion document must not reparse all blocks on the UI thread.
- Regression coverage: `EditorScreenTest.testInlineImageResolutionFollowsDocumentSwitch` and `MarkdownVisualTransformationTest.testExistingProjectionPreservesRenderedTextAndStyles`.

### Editor typography remains readable without changing document state

- Symptom: Medium used the 13sp interface body size and felt too small; enlarging the document exposed clipped heading ascent within body-height line boxes.
- Root cause: interface and editor shared one size token, while heading spans grew inside paragraph metrics sized only for body text.
- Invariant: the editor owns a 16sp Medium token and 1.6 body line ratio, preserving saved multipliers, heading/code ratios and font families. Heading paragraphs reserve sufficient proportional line height. Presentation changes never rewrite source, reset selection or undo, or reload a live session. Shared interface body remains 13sp.
- Regression tests: `EditorTypographyTest`, `EditorTypographyInstrumentedTest.documentFieldUsesEditorMetricsForEveryPresetAndViewMode`, `renderedRunsKeepCompleteStylesAndHeadingsHaveSafeMeasuredLineBounds`, and `typographyChangesPreserveSelectionHistoryAndExactPlainTextSaveReload`.

### The entire document canvas participates in native focus

- Symptom: short/empty documents exposed a small, invisible active area; tapping surrounding whitespace cleared focus. Wide text also exceeded the intended 680dp reading measure.
- Root cause: gutters belonged to an outer focus-clearing container, and an unbounded scrolling field used ineffective width constraints without a finite viewport minimum.
- Invariant: a native `TextFieldDecorator` contains top, side and bottom whitespace and calls its inner field once. The outer document scroll remains the effective scroll owner; a finite viewport supplies minimum height, while centered text can shrink below its 680dp cap. Native nearest-offset caret placement and selection behavior are retained. The toolbar remains above this area with 220dp/100dp clearance and one IME inset owner.
- Regression tests: `EditorCanvasFocusTest.emptyCanvasCenterTapOpensRealImeAndAcceptsTyping`, `shortDocumentTapBelowTextOpensRealImeAndAcceptsTyping`, `shortDocumentSideGutterTapOpensRealImeAndAcceptsTyping`, `shortDocumentTopPaddingTapOpensRealImeAndAcceptsTyping`, `wideCanvasLimitsMeasuredTextLayoutTo680Dp`, `focusingAScrolledVisibleLinePreservesItsViewportAnchor`, and `finalLineScrollsAboveExpandedAndMinimizedToolbarWithRealIme`.
- Legacy characterization: `legacyParagraphEditingAndBackgroundFocusBehaviorRemainAvailable` protects the retained fallback without applying new document decoration behavior to it. No overlay, custom tap interception, or forced caret-to-end behavior is introduced.

## Preferred direction

- Prefer state-based `TextFieldState`. The editor uses `BasicTextField` because the writing canvas intentionally does not use Material filled or outlined field decorations.
- Keep one document-level editable state with a parser-backed `OutputTransformation`. Paragraph parsing may remain incremental, but it must not divide native selection and IME composition into unrelated fields.
- Prefer standard Compose selection, focus, keyboard, accessibility, and gesture behavior. Document and test every unavoidable customization.

## Regression record format

Append to this document when an interaction defect is fixed:

- Symptom and affected release
- Root cause
- Behavioral invariant
- Automated test name
- Any intentional platform-default deviation and why it is necessary

### Renaming keeps the live editor session and truthful identity

- Symptom: Rename opened with an empty name, closed before success, and updated only the active path, leaving the title/navigation identity stale.
- Root cause: presentation owned an optimistic callback without retained request state; route keys still carried the old path.
- Invariant: Rename starts with the full leaf name and selected stem. A retained request saves the draft before the operation and consumes the provider's actual identity. Failure preserves input; partial outcomes block autosave to an uncertain path and offer metadata-only recovery. Success updates the path/title and matching navigation keys without loading over source, selection, or undo. Secondary editor ownership uses the retained primary session identity, independent of renames.
- Regression test: `EditorTypographyInstrumentedTest.failedRenameRetainsDraftAndSuccessfulRenameKeepsSessionAcrossRestoration` plus `RetainedNamingOperationsTest` and provider naming tests.

### Deletion Undo belongs to the activity session

- Symptom: an editor navigated away before presenting Undo, and independent five-second purge jobs could delete data while the offer was queued or extended for accessibility.
- Root cause: route-owned presentation and timers independently decided the same deletion's terminal action.
- Invariant: one activity-owned session receives confirmed trash identities from both editors and the browser. A matching presentation lease chooses either restore or finalization once; canceled/backgrounded presentation requeues its offer. Unsupported/partial moves never enter the ordinary Undo queue. Failed restore/finalization preserves recoverable data and reports actual-location inspection. Provider cancellation becomes an interrupted receipt, never a permanent spinner or automatic disk replay.
- Regression tests: `DeletionSessionViewModelTest`, `TrashDeletionOutcomeTest`, and `EditorTypographyInstrumentedTest.deleteUndoSurvivesLeavingEditorAndRestoringPresentation`.
- Session limitation: process death loses Undo UI and original-path receipts. Unfinalized trash stays on disk; no lost timer purges it. Persistent receipts, restart cleanup, and guaranteed cross-process Undo remain outside this pass.

### Fold shortcut settings and chosen action

- Symptom: the physical shortcut was hidden in the editor overflow, could only open a second editor, and silently did nothing when split sizing failed. A gentle bend or a sensor reporting flat below 175° also never armed the old recognizer. The reported Pixel 10 Pro Fold symptom still needs physical-device confirmation.
- Root cause: configuration lived in an editor-only dialog; the gesture's shared input-safety gate required `canSplit` for every action. Recognition required a <=150° bend, >=175° return and a 1.5-second timeout, excluding gentler gestures and imperfect flat readings. Modal blocking also checked only primary-screen flags, so dialogs belonging to the second editor could leave the parent recognizer active.
- Invariant: Settings → Behavior owns the persisted enabled state, action, haptics and sound. A recognized gesture toggles the chosen Reader metrics (Hemingway) or Second editor pane, without changing source, selection or undo. Existing enabled/feedback values survive the move; missing or malformed action preferences retain Second editor. Metrics does not depend on split-editor sizing. An unavailable second editor produces an explanation without success feedback. An existing second editor hidden by metrics is revealed rather than closed by its selected action.
- Invariant: recognition follows the local Study Companion implementation: observe >=162°, enter the 95°–158° crease range, then return to >=165° after 150–4500ms; abort below 80° and debounce successful triggers for 1000ms. Process actual sensor events without mandatory stationary dwell or conflating away intermediate angles. Initial unfolding, sustained book posture, closure, jitter, lifecycle interruption and cooldown remain guarded. Changing the selected action cancels a pending gesture. Settings and any separate modal window (including one opened from the second editor) disable gesture commits. The root editor must own window focus, rather than relying only on its own dialog flags. Settings offers live sensor readings only while the dialog is explicitly open and resumed. No readings or sensor availability are uploaded.
- Regression coverage: `FoldGestureRecognizerTest.gentle bend works when a flat hinge reports less than 175 degrees` fails on the prior recognizer; the full recognizer suite retains cancellation/noise checks. `EditorViewModelTest.fold metrics ignores split width and preserves the live draft` and `fold second editor respects width and reveals an existing editor behind metrics` cover dispatch. `EditorScreenTest.testInjectedFoldCannotCommitBehindAnotherWindow` fails before the window-focus guard and covers cancellation while a modal is open plus recognition after dismissal. `EditorScreenTest.testInjectedStudyCompanionGestureOpensMetricsWithoutRoomForSecondEditor`, `testInjectedHingeGestureTogglesCompanionExactlyOnceEachWay`, `testFoldFeedbackPreferencesPersistAndPreviewDoesNotChangeLayout`, `testFoldShortcutUnavailableKeepsManualSplitControl`, and `SettingsScreenTest.foldActionLivesInBehaviorSettingsAndPersistsAcrossReopening` cover production UI routing, live readings, preferences, feedback, both choices and large-font Settings.

### Hinge recognition matches the working Study Companion interaction

- Symptom: Study Companion reliably recognizes the user's physical fold/reopen while Kern does not.
- Root cause: Kern used higher flat thresholds, mandatory flat/bend/return holds and a shorter timeout. It also conflated sensor values and sampled the latest angle every 50ms; a brief crease could disappear between samples. These differences reject valid Study Companion event traces; physical confirmation remains required.
- Invariant: use Study Companion's 162° start, 95°–158° crease, 165° return, 150–4500ms flex window, <80° closing abort and 1000ms debounce. Use `SENSOR_DELAY_NORMAL` and process each delivered event; no continuous resampling or steady-state sensor polling. Any brief deferral for native input safety remains limited to 500ms after recognition. Lifecycle, modal/window-focus, action selection and exact-document-state protections remain in force.
- Regression tests: `FoldGestureRecognizerTest.Study Companion trace accepts 162 degree start and 165 degree return`, `sparse on change events recognize without holds or synthetic samples`, and `Study Companion permits a comfortable four second fold` all fail on the preceding recognizer. Additional tests retain fast-flex rejection, cooldown, held posture, invalid samples, clock reversal, deep closure and initial-unfold safeguards. The injected hinge tests exercise both configured actions with a 162° start and 165° return through the production collector. `EditorScreenTest.testInjectedFoldDefersForSafeInputWithoutAnotherSensorEvent` covers safe-input deferral without another sensor event and discarding an expired deferred action.
- Reference and comparison: [Study Companion hinge comparison](hinge-study-companion-comparison.md).
