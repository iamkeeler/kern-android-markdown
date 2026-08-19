# Kern agent guidance

Read [the AI-agent workflow](contributor-guides/ai-agent-workflow.md) before making a change. It defines the required discovery, scope, testing, and handoff process. The project guides in `contributor-guides/` are the durable source for detailed engineering guidance.

Before changing the editor, read [docs/editor-interaction-contracts.md](docs/editor-interaction-contracts.md). Its interaction contracts are release requirements, not implementation suggestions.

For every editor regression fix:

1. Record the symptom, root cause, invariant, and regression test in the interaction-contract document.
2. Add or strengthen an automated test that fails on the regressed implementation.
3. Run the editor instrumentation tests on an emulator in addition to unit tests, lint, and the release build.

Do not mark an editor change complete until every applicable contract and verification step in the AI-agent workflow is satisfied. If an emulator is unavailable, report that explicitly rather than claiming the instrumentation requirement passed.

Prefer stable Android, Jetpack Compose, Kotlin, and Material 3 APIs and platform interaction behavior. Use custom gesture, focus, selection, layout, and text-input behavior only when the platform component cannot satisfy a documented product requirement.

## Theme, system UI, and accessibility requirements

- Apply the active app theme to system status and navigation bars at the app root. Light themes require a light system-bar background with dark icons; dark themes require a dark background with light icons. Do not rely on a screen-local side effect for this.
- Treat the built-in theme colors as accessibility contracts: text-to-background and text-to-surface must meet WCAG AA (4.5:1), while interactive indicators must meet at least 3:1. Add regression coverage whenever theme color declarations change.
- Use Material 3 controls such as `TopAppBar`, `DropdownMenu`, `IconButton`, and window-inset handling by default. Custom chrome or input behavior must document the product requirement that prevents the platform control from being used.
