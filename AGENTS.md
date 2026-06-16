# Kern AGENTS

## Architecture Guidelines

* The architecture strictly mandates decoupling into two layers: 1) Headless Domain Processing Module (pure Kotlin, zero references to Android or Compose runtime) and 2) Platform Binding & Layout Component Module (handles Jetpack Compose and OS bindings).
* UI Modification Rules: Do not create new UI components unless absolutely necessary; reuse and extend existing ones. Avoid structural overhauls and hacks like negative margins or absolute positioning that break component logic.
* Standardization Rule: Centralize reusable UI components (e.g., generic buttons, list rows, typography resolution logic) into a shared components or theme package to prevent duplication across different screen files.
* Do not inject Compose components (e.g., TextFieldValue) or UI hooks into the text processing engines; the text processing loop must exclusively consume standard Kotlin primitives.

## Screen Guidelines

* `MainActivity.kt` uses `enableEdgeToEdge()`. When creating new screens, ensure you add `safeDrawingPadding()` or `systemBarsPadding()` to the root component (like `BoxWithConstraints` or `Column`) to prevent content from overlapping the system status and navigation bars.

## Commands

* Use the Gradle wrapper for execution commands: `./gradlew build`, `./gradlew assembleDebug`, `./gradlew lint test`, and `./gradlew testDebugUnitTest` (specifically for local Android unit tests) to build, lint, and test the project. Note: `ktlintCheck` is not an available task in this project.
