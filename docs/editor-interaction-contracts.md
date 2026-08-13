# Editor interaction contracts

This document records user-visible editor behavior that must survive refactors. Every fixed editor regression should leave behind both an updated contract here and an automated test.

## Required behavior

### Typing must not crash

- Markdown containing full-block or inline images must remain measurable while rendered, focused, and edited.
- Do not place `IntrinsicSize` measurement above `SubcomposeLayout`-based content such as `SubcomposeAsyncImage`, lazy layouts, `BoxWithConstraints`, or tabs. Compose does not support intrinsic measurement for those children.
- Size decorative elements from the measured parent or draw them with a modifier instead of forcing intrinsic measurement.
- Regression coverage: `EditorScreenTest.testInlineMarkdownImageCreatesRenderedImageNode`.

### Selection crosses paragraph boundaries

- A long press in rendered document mode must use Android's standard selection handles and allow expansion into adjacent paragraphs.
- Copying a selection must preserve document paragraph boundaries without introducing extra blank lines.
- Do not put a `LazyColumn` or other virtualized layout inside the document `SelectionContainer`; selection behavior for uncomposed text is undefined.
- A future editable-document architecture must use one document-level text input state so selection can cross paragraph boundaries while editing. Separate `BasicTextField` instances cannot provide native cross-field selection.
- Regression coverage: `EditorScreenTest.testCrossParagraphSelectionCopiesParagraphBoundary`.

### Focus preserves the viewport

- Tapping a visible paragraph must focus it without aligning it to the top or replacing the current scroll position.
- Do not call `scrollToItem` merely because focus changed.
- Do not request that an entire paragraph be brought into view on every text or selection update. Let the text field keep its cursor visible and use IME/window insets to reserve obscured space.
- The floating formatting toolbar must be represented by bottom content space so the cursor can remain visible without forced document jumps.
- Regression coverage: `EditorScreenTest.testFocusingVisibleParagraphPreservesViewport`.

## Preferred direction

- Prefer Material 3 `TextField` and state-based `TextFieldState` APIs. Keep `BasicTextField` only where the inline-reveal presentation demonstrably requires the undecorated primitive.
- Move toward one document-level editable text state with an `OutputTransformation` or equivalent presentation layer. Paragraph parsing may remain incremental, but it must not divide native selection and IME composition into unrelated fields.
- Prefer standard Compose selection, focus, keyboard, accessibility, and gesture behavior. Document and test every unavoidable customization.

## Regression record format

Append to this document when an interaction defect is fixed:

- Symptom and affected release
- Root cause
- Behavioral invariant
- Automated test name
- Any intentional platform-default deviation and why it is necessary
