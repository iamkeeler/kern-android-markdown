# Editor header verification

November 17, 2026. Fix applied on top of `8e73122`, the source of Firebase test build 0.1.46 (47).

## Change

`EditorScreen.kt` gives the Material 3 app bar the active theme background in both resting and scrolled states. `ElasticOverscroll.kt` clips the translated document and indicators to their viewport. Source, selection, focus, scroll positioning, insets and Undo behavior are unchanged.

## Regression evidence

Both new `EditorChromeTest` tests failed before the fix: header pixels contained the contrasting document/backdrop color. After the fix, header opacity and negative elastic displacement checks passed in light and dark themes.

The native editor was also inspected with a long bundled Markdown note during ordinary upward scrolling and an active upward overscroll. The header remained opaque and document pixels stayed below its divider.

## Verification

- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`: passed; 341 unit tests, zero failures/errors/skips, and zero lint Error/Fatal issues.
- `./gradlew :app:connectedDebugAndroidTest --no-daemon`: passed; 87 tests, zero failures/errors/skips, on Android 16 / API36.
- `git diff --check`: passed.
- The final device run used a temporary read-only headless emulator with software rendering, a real software keyboard, networking/animations disabled and Wellbeing disabled. Earlier startup attempts encountered ADB failures and an Android System UI ANR; these were resolved before the passing run. No app assertion was weakened.
- Existing R8/Kotlin metadata warnings remain. No distribution or version reservation was performed for this fix.

Logs: `/private/tmp/kern-header-baseline-confirmed.log`, `/private/tmp/kern-header-focused-final.log`, `/private/tmp/kern-header-matrix.log`, and `/private/tmp/kern-header-full-device.log`. Visual evidence: `/private/tmp/kern-header-scrolled.png` and `/private/tmp/kern-header-during-overscroll.png`.

The durable invariant and test names are recorded in [editor interaction contracts](editor-interaction-contracts.md).
