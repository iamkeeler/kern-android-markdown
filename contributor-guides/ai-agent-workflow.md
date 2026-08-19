# AI-agent workflow

This guide is mandatory for AI coding agents and is useful to human contributors making product changes. It exists to prevent regressions caused by narrow, local fixes that overlook a user-facing contract.

## Before changing code

1. Read the relevant product and engineering guides in this folder.
2. Find the affected behavior's existing tests and contracts before proposing an implementation.
3. Inspect the working tree. Preserve unrelated user changes and do not rewrite or discard them.
4. Prefer the smallest change that preserves established architecture and platform behavior.

## Project-local skills

Use the matching project-local skill when it is available:

- `$kern-editor-regression-guard` for editor, Markdown projection, selection, focus, IME, scrolling, and floating-toolbar work.
- `$kern-android-verification` for Android test/build verification and emulator-result diagnosis.
- `$kern-material3-ui-guard` for Compose UI, Material 3, insets, controls, interaction, and accessibility-semantic changes.
- `$kern-theme-accessibility` for theme tokens, contrast, light/dark behavior, and system bars.
- `$kern-readme-maintenance` for public README updates.
- `$kern-release-learning-loop` only after a successful stable `release` or `v*` tag, to propose durable improvements from an accepted release.

Skills supplement these project guides; they do not replace the contracts, tests, or explicit user authorization required for external actions.

## Editor changes are release work

The editor is a native text-input surface, not a collection of independently editable rendered blocks. Before changing editor, Markdown projection, selection, focus, scrolling, keyboard, or toolbar code, read [the editor interaction contracts](../docs/editor-interaction-contracts.md) in full.

Treat every listed invariant as a release requirement. Do not replace document-level `TextFieldState`, native selection, focus, IME behavior, or offset mapping with custom behavior unless the contract records the product requirement and why platform behavior cannot satisfy it.

For every editor regression fix:

1. Record the symptom, root cause, invariant, and regression-test name in the interaction-contract document.
2. Add or strengthen an automated test that fails on the regressed implementation.
3. Run the full editor verification set:

   ```bash
   ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
   ./gradlew :app:connectedDebugAndroidTest
   ```

4. State the exact test scope and result in the handoff. If the emulator cannot run, say so clearly; it is not a passing verification result.

## Product and UI changes

- Read [product requirements](product-requirements.md) before changing user-visible behavior.
- Read [design guide](design-guide.md) before changing layout, typography, color, motion, Material components, insets, or accessibility behavior.
- Keep theme colors and system bars at the app root. Respect the contrast and Material 3 requirements in `AGENTS.md`.
- For a user-facing interaction or layout change, add Compose/instrumented coverage and visually inspect it on an emulator when practical.

## Completion checklist

- The change is scoped to the request; unrelated files and formatting are untouched.
- Tests cover the affected behavior, including the prior failure mode where applicable.
- Required build, lint, unit, and emulator checks have passed, or any unavailable check is explicitly reported.
- Documentation is updated when the work establishes or changes a durable product, architectural, or interaction rule.
- The handoff leads with the outcome, lists files changed, and names verification performed and any remaining limitation.
