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
- The floating formatting toolbar is a compact, translucent overlay. Reserve only enough bottom content space for the cursor to remain visible; do not turn the toolbar into a full-width opaque bottom sheet.
- Regression coverage: `EditorScreenTest.testFocusingVisibleParagraphPreservesViewport`.

### Rendered Markdown must not fall back to source syntax

- Symptom and affected release: Markdown source was visible in the document canvas for styles that should have been rendered, making the live editor read like a raw text field.
- Root cause: the scanner/parser accepted only a narrow set of line shapes and the presentation test coverage asserted source state instead of transformed layout text.
- Behavioral invariant: rendered mode hides recognized Markdown syntax while preserving the exact source in `TextFieldState`; the visible layout retains the author’s text and its semantic styling.
- Automated test names: `MarkdownRenderingRegressionTest.supported markdown fixture has a rendered projection for every text style` and `MarkdownRenderingInstrumentedTest.renderedEditorTextStripsMarkdownSyntaxButRetainsWriting`.

### Formatting palette remains available until explicitly collapsed

- Symptom and affected release: the formatting palette was a horizontally scrolling toolbar with a separately painted collapse area, leaving part of the control visibly opaque and hiding actions off-screen.
- Root cause: the palette combined a scroll fade, a trailing background panel, and action buttons in one row.
- Behavioral invariant: while the editor is active, the primary Markdown actions are visible in a single, compact floating palette. Applying an action never closes it; only the explicit collapse control may do so.
- Automated test name: `EditorScreenTest.testFloatingFormattingToolbarInteraction`.

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
