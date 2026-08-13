# Kern agent guidance

Before changing the editor, read [docs/editor-interaction-contracts.md](docs/editor-interaction-contracts.md). Its interaction contracts are release requirements, not implementation suggestions.

For every editor regression fix:

1. Record the symptom, root cause, invariant, and regression test in the interaction-contract document.
2. Add or strengthen an automated test that fails on the regressed implementation.
3. Run the editor instrumentation tests on an emulator in addition to unit tests, lint, and the release build.

Prefer stable Android, Jetpack Compose, Kotlin, and Material 3 APIs and platform interaction behavior. Use custom gesture, focus, selection, layout, and text-input behavior only when the platform component cannot satisfy a documented product requirement.
