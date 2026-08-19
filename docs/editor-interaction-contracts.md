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

### Elastic document overscroll bounce-back and end indicators

- Feature and release: elastic overscroll and spring bounce-back when scrolling past top or bottom of a document.
- Behavioral invariant: scrolling or dragging past the vertical document boundaries applies non-linear rubber-band resistance. Top and bottom boundary elastic indicators stretch dynamically in width and opacity using `theme.accent` (Utility Blue). Releasing or settling flings triggers fluid spring bounce-back (`Spring.DampingRatioMediumBouncy`, `Spring.StiffnessMediumLow`) to zero offset. Hardware-accelerated `graphicsLayer` translation is used without invalidating text measurement, selection handles, or IME composition.
- Automated test names: `ElasticOverscrollTest`, `EditorScreenTest.testElasticOverscrollBouncePreservesEditorState`.



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
