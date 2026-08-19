---
name: kern-editor-regression-guard
description: Protect Kern's editor interaction contracts during any change to the Markdown editor, document projection, selection, focus, keyboard/IME insets, scrolling, or floating formatting toolbar. Use when modifying `ui/editor`, Markdown parser/rendering behavior that affects editing, or fixing a user-visible editor regression.
---

# Kern Editor Regression Guard

Preserve native text-input behavior and the editor's documented product invariants while making a focused, tested change.

## Required reading

Before editing, read these files in order:

1. `AGENTS.md`
2. `contributor-guides/ai-agent-workflow.md`
3. `docs/editor-interaction-contracts.md`
4. The relevant tests under `app/src/test/` and `app/src/androidTest/`

Treat the interaction-contract document as release criteria. When it conflicts with an implementation shortcut, keep the contract unless the user authorizes a product change.

## Implementation guardrails

- Keep `TextFieldState` and exact Markdown source as the editing authority.
- Preserve native selection, focus, cursor, IME, scrolling, and offset-mapping behavior. Do not split selection across independent paragraph fields or replace platform behavior without a documented product requirement.
- Keep rendered Markdown as presentation only; never persist transformed text in place of source Markdown.
- Use the shared Markdown projection for rendered editor behavior. Keep styles and selection offsets valid after every local output edit.
- Apply IME/window insets once at the owning editor container. Floating editor chrome must follow the keyboard without duplicating the inset.
- Prefer Material 3 controls and stable Android/Compose APIs.

## Regression workflow

1. State the user-visible symptom and identify the violated contract.
2. Locate the smallest existing test surface that can reproduce it. Add or strengthen a test that fails before the fix.
3. Implement the narrowest fix that preserves the guardrails above.
4. Append the symptom, root cause, invariant, and test name to `docs/editor-interaction-contracts.md`.
5. Review the diff for unrelated changes and verify Markdown source remains exact.

## Required verification

Run all of the following after an editor regression fix:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

Run a focused instrumentation class first when it shortens iteration, but do not substitute it for the full editor instrumentation suite. Use one Gradle instrumentation invocation at a time; concurrent connected-test runs can corrupt shared device-result output.

If an emulator is unavailable, perform all other checks and report the missing emulator verification plainly. Do not claim it passed.

## Handoff

Report the restored invariant, files changed, regression tests added or strengthened, and exact verification results. Call out any intentionally unrun check or remaining limitation.
