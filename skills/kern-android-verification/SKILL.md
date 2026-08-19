---
name: kern-android-verification
description: Verify Kern Android changes with the appropriate unit, lint, release-build, and emulator instrumentation checks. Use after modifying Android/Kotlin/Compose code, Gradle configuration, or tests, and when diagnosing or reporting a failed Android build or emulator test.
---

# Kern Android Verification

Run a focused check during iteration, then use the project release matrix before handoff.

## Select verification

- Run a targeted unit test for pure Kotlin, parser, view-model, and data changes.
- Run the relevant Compose instrumentation test for user-facing interaction or layout changes.
- Run full editor instrumentation after editor regression work; `$kern-editor-regression-guard` defines its additional requirements.
- Run the complete matrix for a handoff, release candidate, or cross-cutting change.

## Standard matrix

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

Check that an emulator is connected before instrumentation. Start only one connected-test Gradle invocation at a time: concurrent runs share result paths and can invalidate device reports.

## Results

- Treat a focused test as iteration evidence, not a substitute for the required full suite.
- Inspect Android test XML/report output when a run ends ambiguously or the tool times out.
- Report command, scope, result, and any unavailable emulator check. Do not infer success from a generated APK alone.
- Do not delete user files or build output merely to make a validation run pass.
