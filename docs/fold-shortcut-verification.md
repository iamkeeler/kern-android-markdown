# Fold shortcut refinement verification

Scope: Settings owns the fold shortcut and its persisted action/feedback preferences. Reader metrics uses the existing Hemingway surface without requiring second-editor width. Second editor retains readable-pane requirements and explains an unavailable layout. Recognition accepts a gentle bend and slightly imperfect flat readings.

## Regression evidence

- Added `gentle bend works when a flat hinge reports less than 175 degrees` before changing the recognizer. The previous implementation failed this test (one failure among ten recognition tests); the updated implementation passes it and the cancellation/noise suite.
- View-model tests cover metrics in an undersized window, exact draft/selection preservation, second-editor sizing, revealing an existing editor behind metrics, and missing/invalid preference fallback.
- Device coverage exercises injected angle changes through the production recognition path, both selected actions, exactly one feedback request per successful gesture, Settings routing, live sensor readings without layout changes, persistent preferences, sensor-unavailable manual controls, and 200% font scaling for the action selector.

- A final review found that primary-screen dialog flags did not cover other modal windows, including those owned by the second editor. Added `testInjectedFoldCannotCommitBehindAnotherWindow` and reproduced the unwanted commit before adding the standard Compose window-focus guard. The test also verifies a fresh gesture works after dismissal.

## Build checks

- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease -x :app:uploadCrashlyticsMappingFileRelease` passed: 299 unit tests, zero failures/errors/skips, zero lint errors, release APK built. Existing nonfatal R8/Kotlin metadata warnings remain. Crashlytics mapping upload is an external release action and was excluded from local verification.
- Final combined verification passed on October 29, 2026: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:connectedDebugAndroidTest -x :app:uploadCrashlyticsMappingFileRelease --max-workers=2` completed successfully in 8m 22s. All **58 instrumentation tests** passed on `Kern_Polish_API_36` / emulator-5554 / Android 16 API 36, with zero failures/errors/skips; all 299 unit tests passed, lint reported zero errors and the release APK built. Final log: `/private/tmp/kern-fold-verified.log`.
- The first device attempt was blocked by an emulator System UI ANR dialog, which prevented IME visibility checks. Cold-started the temporary API 36 AVD, cleared the emulator dialog before tests began, and reran the complete suite separately from release optimization. The original user AVD was preserved.

- On the recovered emulator, all editor/fold tests passed. One unrelated large-font Auto-pair assertion observed stale Compose state after its Room write had already completed. Strengthened that test to wait for the observed UI state; the final complete run passed with the UI-state wait in place.
- Visually inspected the production fold Settings dialog from the device test: both choices, sensor status and feedback rows fit without horizontal clipping; content scrolls inside the native dialog. The action selector also passes at 200% system font scale.

## Physical-device limit

Emulator angle injection verifies recognition, lifecycle cancellation and UI dispatch, but does not establish the Pixel 10 Pro Fold's actual sensor readings or physical feel. The Settings dialog now displays local hinge readings to support that device test. The shortcut remains opt-in, and no sensor values are sent to telemetry.

Shared Firebase/Play builds use the repository's version allocator, packaged-APK verification and source-commit metadata. Local test/build invocations do not reserve distribution numbers.
