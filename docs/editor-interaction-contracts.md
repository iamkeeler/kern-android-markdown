# Editor interaction contracts

This document records user-visible editor behavior that must survive refactors. Every fixed editor regression should leave behind both an updated contract here and an automated test.

## Required behavior

### Find shows the matching passage without editing the note

- Files search uses current storage with cancellable background reads and partial results for unavailable storage. Content matches carry the query into the editor. Recheck it against the live document before highlighting or scrolling.
- Find in an open note matches literal Markdown text case-insensitively. The active match uses the same source-to-display offset map as Markdown rendering. Keep cursor and selection reveal. If the match itself includes hidden syntax, retain the matching tokens so the highlight remains visible.
- Find only scrolls to a requested match. It uses the native viewport and leaves the source, selection and Undo history intact. Closing Find keeps the passage in view. Opening a content result shows Find without focusing the keyboard. The editor menu can explicitly focus Find. With the keyboard dismissed, Back closes Find before leaving the note.
- Coverage is in `EditorCanvasFocusTest.findHighlightsAndWrapsWithoutMovingTheWritingSelection` and `QuickCaptureNavigationTest.contentSearchOpensNestedNoteAtTheMatchingPassageAndReturnsToResults`.

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

### Scrolling writing stays below an opaque editor header

- Symptom and affected release: in test build 0.1.46 (47), scrolling writing could remain visible through the editor header.
- Root cause: `EditorHeader` explicitly requested a transparent Material 3 app-bar container, and the elastic scroll container translated later-drawn document content outside its viewport. An opaque background alone cannot prevent a later sibling from drawing over the header.
- Invariant: the Material 3 header paints the active theme background in both resting and scrolled states. The elastic scroll viewport clips translated document content and indicators to its own bounds, including in a second editor. Keep the header anchored, with existing divider, insets, scroll range, source, native selection, composition and Undo unchanged.
- Regression coverage: `EditorChromeTest.headerPaintsAnOpaqueBackgroundInBothThemes` and `elasticScrollCannotDrawDocumentOverTheHeader` both fail on the regressed implementation. Pixel checks cover light/dark header opacity and a deterministic negative elastic displacement, with a contrasting document proving that drawing remains inside the viewport. Existing real-IME focus, viewport, elastic bounce and split tests remain required.
- Platform behavior: use the standard Material 3 container colors and Compose `clipToBounds`; no custom focus, pointer handling, scroll positioning or header overlay is introduced.

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
- Behavioral invariant: `BasicTextField` content scrolls through the full canvas behind the floating toolbar (`zIndex = 1f`). The document editor area provides generous bottom scroll clearance (`220.dp` in both expanded and minimized states) inside a `verticalScroll` container so any line of text at the bottom can be scrolled comfortably above the floating toolbar into open view.

### Readability and metrics modal bottom sheet

- Symptom and affected release: tapping the analytics/readability button displayed an empty sidebar pane with missing counts and no analysis in document editor mode.
- Root cause: `SidebarMode.METRICS` was unhandled in `SidebarPane`, and `EditorViewModel` read only `paragraphs.items` rather than the active document text in `documentTextFieldState`.
- Behavioral invariant: tapping the analytics icon opens a Material 3 `ModalBottomSheet` on single-pane devices (or sidebar pane on dual-pane devices) populated with the readability grade, word count, character count, sentence count, reading time, and full Hemingway suggestions breakdown.
- Automated test name: `EditorViewModelTest.toggleSidebar in METRICS mode calculates readability metrics from document text`.

### Undo and Redo actions in floating toolbar

- Feature and release: added dedicated Undo and Redo actions to the floating formatting toolbar.
- Behavioral invariant: Undo and Redo buttons reflect the transactional state of `TextFieldState.undoState` (`canUndo`, `canRedo`). Inactive states are disabled with dimmed opacity (`0.35f`), active states are fully interactive. Applying formatting commands or editing text immediately enables Undo.
- Automated test name: `EditorViewModelTest.undo and redo modify documentTextFieldState correctly`.
- Narrow-pane regression: the fixed eight-action row could place the collapse control outside a phone or split writer pane. The root cause was fitting eight 48dp controls plus spacing into less than their required width. Retain six controls with native touch targets: Undo, Heading, Bold, Bullets, More and Collapse. Italic and Redo remain in overflow when the wide row cannot fit. Wide panes retain both history buttons and Italic in the row.
- Regression coverage: `EditorScreenTest.testFloatingFormattingToolbarInteraction` (also reproduced on the original commit), plus `testCompanionTogglePreservesDraftSelectionUndoAndFocus` for split-pane operation. The native document editor keeps constant bottom clearance across palette states.

### Elastic document overscroll bounce-back and end indicators

- Feature and release: elastic overscroll and spring bounce-back when scrolling past top or bottom of a document.
- Behavioral invariant: scrolling or dragging past the vertical document boundaries applies non-linear rubber-band resistance. Top and bottom boundary elastic indicators stretch dynamically in width and opacity using `theme.accent` (Utility Blue). Releasing or settling flings triggers fluid spring bounce-back (`Spring.DampingRatioMediumBouncy`, `Spring.StiffnessMediumLow`) to zero offset. Hardware-accelerated `graphicsLayer` translation is used without invalidating text measurement, selection handles, or IME composition.
- Automated test names: `ElasticOverscrollTest`, `EditorScreenTest.testElasticOverscrollBouncePreservesEditorState`.



### Foldable companion preserves the writing session

- Feature: book-style foldables and sufficiently wide windows expose a Split view command and a second native editor with a file picker. Diff is follow-on work.
- Behavioral invariant: each document has its own `TextFieldState`, selection, undo history and save path. Split open/close, sidebar substitution and width changes preserve the primary draft; the picker prevents two editors from writing the same file. Switching the second file saves its current draft before loading another.
- Manual close restores the previous sidebar. A physical fold-close does not restore Settings, which would disable the next gesture. The second document survives hiding or compact-window suspension. Each pane owns its IME inset; the editor toolbar retains its clearance.
- The manual command requires >=720dp and >=340dp per pane, with minimum pane width increasing for large fonts. Vertical separating hinges are excluded from pane bounds. Horizontal folds do not enable this feature.
- The shortcut is enabled by default when no preference is saved; an explicitly saved off value remains off. It requires a standard angle sensor and a resumed editor. An observed flat baseline, deliberate brief bend, return to flat, and cooldown produce one toggle. Initial unfolding, sustained postures, full closure, horizontal folds, modal UI, and lifecycle interruption must not toggle. Transient vertical geometry changes do not cancel a gesture. Never interrupt pointer selection or either editor's IME composition to commit.
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
- Invariant: a native `TextFieldDecorator` contains top, side and bottom whitespace and calls its inner field once. The outer document scroll remains the effective scroll owner; a finite viewport supplies minimum height, while centered text can shrink below its 680dp cap. Native nearest-offset caret placement and selection behavior are retained. The toolbar remains above this area with constant 220dp clearance and one IME inset owner.
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
- Invariant: recognition follows the local Study Companion implementation: observe >=162°, enter the 95°–158° crease range, then return to >=165° after 150–4500ms; abort below 80° and debounce successful triggers for 1000ms. Process actual sensor events without mandatory stationary dwell or conflating away intermediate angles. Initial unfolding, sustained book posture, closure, jitter, lifecycle interruption and cooldown remain guarded. Changing the selected action cancels a pending gesture. Settings and any separate modal window (including one opened from the second editor) disable gesture commits. The root editor must own window focus, rather than relying only on its own dialog flags. Settings offers live sensor readings while the Behavior tab is composed and resumed; leaving the tab or screen unregisters the sensor. No readings or sensor availability are uploaded.
- Regression coverage: `FoldGestureRecognizerTest.gentle bend works when a flat hinge reports less than 175 degrees` fails on the prior recognizer; the full recognizer suite retains cancellation/noise checks. `EditorViewModelTest.fold metrics ignores split width and preserves the live draft` and `fold second editor respects width and reveals an existing editor behind metrics` cover dispatch. `EditorScreenTest.testInjectedFoldCannotCommitBehindAnotherWindow` fails before the window-focus guard and covers cancellation while a modal is open plus recognition after dismissal. `EditorScreenTest.testInjectedStudyCompanionGestureOpensMetricsWithoutRoomForSecondEditor`, `testInjectedHingeGestureTogglesCompanionExactlyOnceEachWay`, `testFoldFeedbackPreferencesPersistAndPreviewDoesNotChangeLayout`, `testFoldShortcutUnavailableKeepsManualSplitControl`, and `SettingsScreenTest.foldControlsAreInlineAndPersistAcrossTabsAtLargeFontScale` cover production UI routing, live readings, preferences, feedback, both choices and large-font Settings.

### Hinge recognition matches the working Study Companion interaction

- Symptom: Study Companion reliably recognizes the user's physical fold/reopen while Kern does not.
- Root cause: Kern used higher flat thresholds, mandatory flat/bend/return holds and a shorter timeout. It also conflated sensor values and sampled the latest angle every 50ms; a brief crease could disappear between samples. These differences reject valid Study Companion event traces; physical confirmation remains required.
- Invariant: use Study Companion's 162° start, 95°–158° crease, 165° return, 150–4500ms flex window, <80° closing abort and 1000ms debounce. Use `SENSOR_DELAY_NORMAL` and process each delivered event; no continuous resampling or steady-state sensor polling. Any brief deferral for native input safety remains limited to 500ms after recognition. Lifecycle, modal/window-focus, action selection and exact-document-state protections remain in force.
- Regression tests: `FoldGestureRecognizerTest.Study Companion trace accepts 162 degree start and 165 degree return`, `sparse on change events recognize without holds or synthetic samples`, and `Study Companion permits a comfortable four second fold` all fail on the preceding recognizer. Additional tests retain fast-flex rejection, cooldown, held posture, invalid samples, clock reversal, deep closure and initial-unfold safeguards. The injected hinge tests exercise both configured actions with a 162° start and 165° return through the production collector. `EditorScreenTest.testInjectedFoldDefersForSafeInputWithoutAnotherSensorEvent` covers safe-input deferral without another sensor event and discarding an expired deferred action.
- Reference and comparison: [Study Companion hinge comparison](hinge-study-companion-comparison.md).

### Fold preferences are part of Settings

- Symptom: Fold shortcut appeared as a button opening a separate dialog rather than normal settings content.
- Root cause: `FoldShortcutSettings` hid its controls behind local dialog state and a second scroll container. Removing that modal also removes window-focus blocking for Settings displayed inside the second editor.
- Invariant: Settings → Behavior contains the enabled switch, action radio group, haptics, sound, feedback preview and local hinge status inline. The existing Settings page owns scrolling and gutters. Each compound row keeps one native action and a minimum 48dp touch target; text wraps at large system font sizes. Saved values survive tab changes without changing the document. No fold action can commit while inline Settings is open in either visible editor pane; closing Settings resumes recognition from a fresh baseline.
- Regression coverage: `SettingsScreenTest.foldControlsAreInlineAndPersistAcrossTabsAtLargeFontScale` fails on the previous button/dialog implementation because the action choices are absent from Behavior. It checks direct access, absence of a dialog/navigation button, large-font touch bounds and persistence. `EditorScreenTest.testInjectedFoldPausesWhileInlineSettingsAreOpenInSecondEditor` checks the secondary-pane guard and fresh recognition after closing it. The existing preference/preview and sensor-unavailable tests now operate directly on inline controls.

### Repeated fold toggles stay available after closing a loaded second editor

- Symptom: after closing a second editor with the fold shortcut in 0.1.44 (45), the next fold could refuse to reopen it. Reproduced when split view was opened from Settings, a second document was loaded and edited, then the gesture closed the split.
- Root cause: gesture dispatch reused the manual split toggle, which restored the previously open Settings sidebar. Settings correctly disables physical recognition, so this automatic restoration prevented the next fold. The former two-toggle test started with a closed sidebar and an empty second pane, missing this path.
- Invariant: a physical fold-close returns to the writer instead of restoring Settings. Manual close retains its previous-sidebar restoration, and gestures remain blocked while Settings is intentionally open. Repeated close/reopen cycles retain both documents, selections, undo availability and the second file identity, with one feedback request per committed gesture.
- Regression coverage: `EditorViewModelTest.fold closing a split opened from settings keeps repeated reopening available` and `EditorScreenTest.testInjectedFoldReopensLoadedSecondEditorRepeatedly`. The emulator test fails on the prior dispatch at its first reopen, and exercises two cycles with real document edits. `SettingsScreenTest.foldShortcutDefaultsOnAndExplicitOffSurvivesReentry` fails on the former off default; `testInjectedHingeGestureTogglesCompanionExactlyOnceEachWay` now runs without a saved enabled preference. Explicit off and feedback preferences remain persisted.
- No new platform-input deviation: the fix changes pane dispatch only; native focus, IME composition, selection, document text and sensor thresholds remain unchanged. Emulator injection does not establish the physical device's sensing behavior.

### New note opens directly into native writing

- Symptom: capture required a filename dialog, another tap to focus its field, and another tap in the created editor before writing.
- Root cause: the primary creation action used a required-name request; `focusOnStart` affected the older paragraph path but did not request native document-field focus.
- Invariant: New note creates a collision-safe file in the current folder and opens its document field with the real IME ready. Provider-returned identity is authoritative. Repeated taps share one creation; a failed creation never opens an invented file. An explicit Write locally recovery action may use Files when the selected workspace is unavailable. Naming follows writing. Empty notes remain user-owned files.
- Regression coverage: `EditorCanvasFocusTest.newNoteStartsFocusedWithRealImeWithoutAnotherTap` fails on the previous implementation's missing focus; `MainScreenTest.quickNoteUsesCurrentFolderAndPreservesAnExistingUntitledNote`, `MainScreenViewModelTest.quick capture keeps its folder retries collisions and uses actual provider identity`, `repeated capture taps share one creation and failed creation never opens a note`, and `unavailable capture folder offers an explicit local fallback` protect creation outcomes.
- Platform behavior: use native `FocusRequester` once after loading. Preserve the document `TextFieldState`, viewport and cursor-local Markdown reveal; do not add custom touch interception or keyboard resizing.

### Saving is truthful and survives editor departure

- Symptom: a failed external-folder write could be swallowed while metadata still claimed success. An unavailable read could become an editable empty document. Debounced saves could be cancelled during route removal.
- Root cause: storage returned successful `Unit`/empty text on failure, and final writes lived in the view-model scope without a durable recovery snapshot or a shared departure path.
- Invariant: write complete exact-source recovery snapshots atomically in app-private storage before destination writes. Serialize recovery and destination writes by revision; older queued work must not replace newer recovery or recreate an already committed journal. Failed destination/metadata writes retain recovery and a visible error with Retry and Save a copy. Only acknowledged writes may show Saved. Missing/unavailable reads disable editing and offer Retry. A readable recovery draft may reopen despite unavailable destination storage.
- Invariant: compare the original source and indexed file identity before saving. Externally changed, removed or replaced files must not be silently overwritten/recreated. Preserve the recoverable draft and offer a copy. File/provider actors outside Kern are not covered by its project mutation lock; cross-provider atomicity is not promised.
- Invariant: toolbar Back and system Back flush the current native source before leaving. Background/disposal also flush; view-model clearing finishes the last captured write in an independent persistence scope cancelled after its final save. A failed save permits departure only after the current revision has recovery protection. Never cancel an in-flight destination write just to debounce another edit.
- Regression coverage: `StorageManagerTest.documentReadDoesNotTurnAMissingFileIntoAnEmptyNote`, `documentWriteReportsAnUnwritableDestination` and `safWriteDoesNotSwallowProviderFailure`; `DocumentSaveSessionTest` covers exact recovery, retry, external conflicts, identity replacement, edits during save, stale queued work and recovery failure. The first two storage regressions fail on the prior implementation.
- Device coverage: `EditorSaveProtectionTest` exercises exact save-before-Back, visible conflict recovery and fresh-model restoration, unavailable reads/retry, recovered-save retry, background flush and view-model clearing. A fresh view-model/store test establishes disk-backed recovery; it does not simulate an OS process kill or power failure.

### Navigation owns a stable writing session

- Symptom: the display did not scope view models to navigation entries. Activity-owned editor state could outlive a popped route or be reused by another document; keying state by the filename alone would lose it after rename.
- Root cause: the Navigation 3 view-model entry decorator was absent, and editor route identity had no stable session key independent of its mutable path.
- Invariant: the existing Navigation 3 saveable-state and view-model decorators own entry lifetime. Editor entries use their serialized session ID as content key. Updating a path after rename preserves the same native source, selection, focus and Undo state. Popping an entry clears that session without cancelling its final protected save.
- Regression coverage: `QuickCaptureNavigationTest.captureRenameUndoAndSystemBackPreserveIndependentFilesThroughRealNavigation` exercises immediate creation, editing, rename, selection/Undo availability and independent files through the real app navigation; existing typography/rename and split-pane tests continue to protect native state.
- API reference: [Navigation 3 state and view-model ownership](https://developer.android.com/guide/navigation/navigation-3/save-state). The implementation uses the installed library's stable entry-decorator API without a dependency upgrade.


### Writing continuity survives palette changes and document switches

- Symptom: collapsing the palette at the bottom of a long note moved the final line by 120dp. Explicit collapse was forgotten during state restoration, and a second document could inherit the preceding document's scroll position and Undo history.
- Root cause: changing bottom clearance from 220dp to 100dp clamped the native scroll range. Palette state used plain remember, the scroll state lacked a document identity, and programmatic document loads shared edit history.
- Invariant: keep 220dp native canvas clearance in both palette states. Save explicit collapse with the writing session. Key the saveable viewport by project and stable file identity; rename and same-file reentry preserve it, while another document starts at its own viewport. Clear native history only after an actual document load. Loading a file is never an Undo action that can import another file's source.
- Regression coverage: `EditorCanvasFocusTest.collapsingToolbarKeepsTheFinalLineAtItsViewportAnchor` reproduced the 120dp displacement before the fix. `collapsedToolbarSurvivesScreenStateRestoration`, `switchingDocumentsStartsANewViewportWithoutChangingTheSavedPaletteChoice`, and `EditorViewModelTest.opening another file never exposes the previous documents undo history` protect session boundaries. Existing real-IME focus, last-line clearance, split and rename coverage remains required.
- Platform behavior: keep the same native field, source, selection, composition, scroll owner and inset owner. No automatic centering, pointer interception or focus-triggered scrolling is introduced. This does not promise durable reading-position recovery across process death.

### Undo is directly available in compact writing panes

- Symptom: Undo required opening More on a phone, making recovery harder to discover during quick capture.
- Root cause: compact-width prioritization reserved the row for formatting and placed both history actions in overflow.
- Invariant: the compact row contains Undo, Heading, Bold, Bullets, More and Collapse, each with a native 48dp target. More provides Italic and Redo with truthful enabled states. Wide panes retain both history buttons and Italic. Formatting menus retain native input focus and the real keyboard.
- Regression coverage: `EditorCanvasFocusTest.compactToolbarOffersUndoWithoutOpeningAMenu` fails on the previous menu-only implementation. `EditorScreenTest.testFloatingFormattingToolbarInteraction`, `QuickCaptureNavigationTest.captureRenameUndoAndSystemBackPreserveIndependentFilesThroughRealNavigation`, and typing preference coverage exercise direct Undo and overflow Redo.

### Typing preferences apply to the active native editor

- Symptom: pairing and heading-spacing settings affected only the legacy editor; the active document field always requested sentence capitalization.
- Root cause: its input transformation received no preferences and keyboard options hard-coded capitalization.
- Invariant: observe the existing master/per-pair and heading switches without reloading source, selection, history or viewport. Local typing edits pair enabled quotes/brackets, wrap native selections in either direction, skip existing matched closers and delete empty pairs together. Respect escapes, contractions, word boundaries and code. Heading spacing affects the current line only. All helper edits share the user's native Undo transaction. Existing list continuation remains shared with the legacy engine.
- Invariant: preserve pasted text, bulk replacements and composing input exactly. A changed committed composing word may apply heading spacing after composition ends; a no-change composition commit invokes no transformation, so spacing waits for the next user edit. Capitalization is a native keyboard hint, not forced source rewriting; changing it waits until active composition ends. Settings calls this “Capitalize sentences” and describes its keyboard-dependent behavior.
- Regression coverage: `MarkdownDocumentInputTransformationTest` reproduces missing default pairing and heading spacing; `DocumentTypingEngineTest` covers per-pair controls, prose, code, escapes, exact paste and directional selection. `EditorTypingPreferencesTest` exercises every active switch, native pair Undo/Redo, direct/batched/API33 composition, API34 replacement, heading commit, clipboard paste and capitalization hints. `EditorScreenTest.testDocumentEditorContinuesMarkdownList` and `EditorTypingPreferencesTest.nativeEnterContinuesAListAfterAComposingWord` protect list continuation. The latter reproduced an uncontinued newline because the composition guard treated native Enter as an unfinished composing edit; the transformation now permits native line breaks after composition while the origin observer still preserves composing and pasted newlines. Native Enter is dispatched asynchronously, so a synchronous input-connection flag cannot identify its later edit.
- Platform exception: installed Compose 1.11.2 exposes neither pending composition nor edit origin on public `TextFieldBuffer`; public `TextFieldState.composition` still describes the original state during the first composing edit. A single-character paste is also indistinguishable from typing in an input transformation. To satisfy exact paste/composition requirements, a narrowly scoped public `InterceptPlatformTextInput` observer delegates every native command and a pass-through `contentReceiver` marks clipboard transfers. These public APIs require an experimental opt-in in the installed library. API33/34 overloads live in SDK-gated wrappers. No reflection, custom text-input implementation, gesture or selection handling is used. Arbitrary keyboards that present clipboard/dictation content as ordinary character commits do not expose a reliable origin distinction.
