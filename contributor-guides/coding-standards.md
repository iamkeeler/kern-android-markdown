# Coding standards

## Architecture

- Keep Markdown parsing, document transformation, metrics, and other domain logic in pure Kotlin where possible.
- Keep Android, Compose, storage-provider, lifecycle, and platform integrations at the UI/data boundary.
- Pass state down and events up. View models own screen state; composables should remain small and focused on rendering and user interaction.
- Prefer existing repositories, models, and utilities over introducing parallel abstractions.

## Kotlin and Compose

- Use clear names and small functions. Avoid clever or overly generic abstractions.
- Use immutable models and `kotlinx.collections.immutable` for collections crossing UI boundaries.
- Do not perform file I/O, parsing of large documents, or analysis on the main thread.
- Bind UI colors and typography through the theme tokens; do not add one-off visual constants in a screen.
- Preserve cursor and selection state explicitly when formatting Markdown changes the underlying text.

## Tests and verification

- Add or update unit tests for parser, storage, sync, metrics, and view-model behavior.
- Add Compose/instrumented coverage when a user-facing interaction or layout contract changes.
- For editor changes, preserve and update the contracts in `docs/editor-interaction-contracts.md` and run `EditorScreenTest` on an emulator.
- Before opening a pull request, run:

  ```bash
  ./gradlew test lint bundleRelease
  ```

- Include test notes and screenshots for UI changes in the pull request.

## Safety

- Never commit Firebase configuration, keystores, signing properties, tokens, local properties, or generated build output.
- Treat document text and paths as private user data. Analytics must use the established allowlist and must not include document content, titles, or paths.
- Keep changes focused and avoid unrelated formatting churn.
