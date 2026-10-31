# Fold default and repeated reopen verification

Verified October 31, 2026, on `codex/application-polish`.

## Change and regression evidence

- An absent fold-enabled preference now defaults on in Settings and the editor. An explicitly saved off value remains off. Haptics, sound, selected action and sensor thresholds retain their existing behavior.
- Reproduced a second-editor reopening failure on the preceding dispatch: open split view while Settings is visible, load and edit a second document, fold to close, then fold again. Closing restored Settings, whose modal-safety gate correctly disabled subsequent recognition. The new instrumented test failed at its first reopen before the fix.
- Physical fold-close now avoids restoring Settings. Manual close still restores its previous sidebar, and intentionally open Settings continues to block gestures. Two repeated close/reopen cycles preserve both source buffers, selections, undo availability and second-file identity, with four feedback requests for four commits.
- The default-on Settings test failed against the former default. It also checks that explicitly turning off persists across tab reentry. Existing production hinge-injection coverage now runs without a saved enabled preference.

Contracts and product guidance record the behavior. Both the short What's New summary and bundled Markdown describe the changes.

## Verification

Using JDK 17 and the temporary `Kern_Polish_API_36` emulator (Android 16/API 36):

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease \
  :app:connectedDebugAndroidTest \
  -x :app:uploadCrashlyticsMappingFileRelease --max-workers=2
```

Final result: **BUILD SUCCESSFUL**. All **315 unit tests** and **63 instrumentation tests** passed, with zero failures, errors or skips. Lint reported zero errors/fatal issues and 69 warnings. The release APK built; R8's existing Kotlin-metadata compatibility warnings remain. Local Crashlytics mapping upload was excluded.

All **16 Python version/release-note tests** passed. The local release APK's versioned Markdown was checked byte for byte against the maintained document. Local asset generation used temporary metadata and did not reserve shared distribution numbers. Visually inspected the restored split view with both edited documents.

The first full native run passed 62/63 tests; the remaining failure was a typography-fixture database shutdown race, not a layout assertion. Activity destruction canceled its retained secondary editor without awaiting its Room observer cleanup. The fixture now joins that editor's scope before closing Room; the final full rerun passed. An earlier emulator startup System UI ANR was cleared before valid reproduction evidence was collected.

Instrumentation injects hinge events through the production collector. The original Pixel 10 Pro Fold sensor behavior still requires confirmation on physical hardware; this record establishes the reproduced Settings-restoration path and unchanged native-input protections.
