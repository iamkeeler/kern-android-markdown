# Inline fold settings and release-note preparation

October 30, 2026 scope: fold preferences appear directly under Settings → Behavior, using its existing scroll container, gutters, theme and native controls. The navigation button, dialog and nested scrolling were removed. Existing enabled/action/feedback values are preserved, and the local hinge readout is active only while Behavior is composed and resumed. Settings in either visible editor pane pauses gesture commits; closing it resumes from a fresh baseline.

The existing What's New draft still described the initial internal build. Current user-facing notes now live in `release-notes/whatsnew-en-US`; the listing draft links to this source. Both distribution workflows prepare notes with the actual allocated version/build, source commit and workflow attempt. Firebase and tagged GitHub releases use the prepared notes, and Play receives the localized source. Optional Firebase tester context is retained. The release-note helper rejects empty or oversized notes, and AGENTS.md records the maintenance rule.

## Verification

- Before the UI change, `SettingsScreenTest.foldControlsAreInlineAndPersistAcrossTabsAtLargeFontScale` failed because Second editor was absent from Behavior without opening the separate dialog.
- Final combined matrix passed in 6m 30s: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:connectedDebugAndroidTest -x :app:uploadCrashlyticsMappingFileRelease --max-workers=2`.
- 304 Android unit tests and 60 instrumentation tests passed, with zero failures/errors/skips. Lint reported zero errors and the release APK built. Existing nonfatal R8/Kotlin metadata warnings remain. Local Crashlytics mapping upload was excluded.
- Device: temporary `Kern_Polish_API_36`, emulator-5554, Android 16 API 36, real software keyboard enabled. A startup System UI wait dialog was cleared before the final matrix. The original user AVD was preserved.
- Inline coverage checks direct access without a dialog/button, persistence across tabs, minimum native touch targets and 200% font scaling. Updated editor coverage checks saved feedback preferences, preview without changing the draft, sensor-unavailable manual controls, and suppressing a fold while the second editor's inline Settings is open.
- Visually inspected compact 320dp/200%-font and wide embedded Settings screenshots. Labels wrap without horizontal clipping or overlapping controls; the Settings page owns scrolling. Captures: `/private/tmp/kern-inline-fold-large-font.png` and `/private/tmp/kern-inline-fold-wide.png`.
- All 13 Python versioning/release-note checks passed; `actionlint` passed for both publishing workflows, and `git diff --check` passed. The maintained notes contain 370 characters.
- Native log: `/private/tmp/kern-inline-fold-verification.log`; initial regression log: `/private/tmp/kern-inline-fold-before.log`.

## Release boundary

Shared builds reserve fresh version and build numbers through the existing shared counter, verify the packaged APK, and record source metadata. The checked-in Gradle properties remain the documented baseline/lower bound. This preparation does not publish a Play production release. Physical hinge behavior and feel remain a device-check requirement; emulator injection is not physical confirmation.
