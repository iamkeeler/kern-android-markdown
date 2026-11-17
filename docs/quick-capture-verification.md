# Save protection and immediate capture

Implemented November 17, 2026. Verification applies to the working tree based on `790a80e`, not a distributed build. No version allocation, publication or deployment is part of this change.

## Behavior

- New note creates a uniquely named Markdown file in the current folder and requests native document focus after loading. There is no required naming dialog; the title still supports Rename. Empty notes are retained as ordinary files.
- Recently modified notes appear above Files at launch, including nested notes and notes outside an empty selected workspace. Creation still targets the selected workspace/folder. Duplicate current-folder rows and empty Files headings are avoided.
- If a capture destination is unavailable, an explicit Write locally action can create the note in Files. A partial provider/metadata creation does not trigger a blind disk retry.
- Recovery snapshots contain exact source, selection, original baseline and indexed file identity. They are written atomically to app-private storage before the destination is touched.
- Destination saves are serialized and revision-aware. Stale queued protection cannot replace newer text or recreate an already committed recovery file. Local destination files use the same atomic UTF-8 writer.
- Failed saves retain recovery and expose Retry and Save a copy. The error notice is a polite accessibility live region. Missing reads show an unavailable state instead of an editable blank document.
- Save checks protect against changed source, removed files, replaced indexed identities and removed workspaces. Failed saves do not mark metadata as successfully pending sync. Metadata failures also retain recovery until retry succeeds.
- Toolbar/system Back flush before departure; background/disposal and view-model clearing also flush pending source. Final persistence has its own scope, cancelled after the final write. Departure after a failed save requires recovery protection for the current revision.
- Navigation entries own their view models. A serialized session ID supplies a content key that remains stable when the file is renamed. Rename retains selection and Undo access; distinct notes retain separate sources.
- The native document field and its Markdown reveal, transformations, selection, IME insets, typography, viewport and formatting priorities remain intact. Formatting controls have no target while loading/unavailable. The obsolete Launch New File switch was removed because quick capture always opens its note.

## Source organization

- `AtomicTextFile.kt` is the shared, small filesystem commit primitive.
- `DraftRecoveryStore.kt` owns private recovery serialization and identity hashing.
- `DocumentSaveSession.kt` owns serialized recovery/destination/metadata commits and conflict checks.
- `StorageManager.kt` propagates read/write failures instead of treating them as success/empty content.
- `EditorViewModel.kt`, `EditorScreen.kt` and `Navigation.kt` own presentation, lifecycle and native focus around those storage operations.
- `MainScreenViewModel.kt` and `MainScreen.kt` own capture creation, current destination, recents and explicit fallback.
- Product requirements, editor contracts, the writing review decisions and both release-note files document the new behavior.

## Regressions reproduced before fixes

- `StorageManagerTest.documentReadDoesNotTurnAMissingFileIntoAnEmptyNote`: failed because a missing document returned an empty string.
- `StorageManagerTest.documentWriteReportsAnUnwritableDestination`: failed because a failed filesystem save returned without error.
- `EditorCanvasFocusTest.newNoteStartsFocusedWithRealImeWithoutAnotherTap`: failed on the unchanged editor because the native document field was not focused.

## Verification

Final commands passed on the settled source:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

- Unit tests: **331 passed**, zero failures/errors/skips.
- Debug lint: **zero Error/Fatal**, 68 warnings. The release build also passed its configured lint checks.
- Release APK assembly: passed. APK SHA-256: `11cf354cb7f7325baef5ab69af23611729774538f788c3f3776836ea25ac19e5`.
- Instrumentation: **73 passed**, zero failures/errors/skips, Android16/API36 `Medium_Phone_API_36`, final run 3m28s.
- `git diff --check`: passed.
- The 21 changed source/test/packaged-asset files are recorded in `/private/tmp/kern-capture-source-manifest.json`, SHA-256 `7c367e3dae89967dc6a3199e75de5f5cedd36201269e03e4a20f08606ff48f9e`. No source changed after this verified manifest. Test/build logs are `/private/tmp/kern-capture-build-final.log` and `/private/tmp/kern-capture-device-final.log`; these temporary files may be cleared by the OS.

The unit suite includes real filesystem recovery, provider fault injection, metadata failures, concurrent edits during a write, stale revisions, identity replacement, current-folder collision handling, repeated taps, and local fallback. Device coverage includes native focus/real IME, viewport and toolbar clearance, exact-source persistence, failed save presentation/recovery, unavailable reads/retry, background flush, view-model clearing, recents, and actual app navigation through capture/rename/Back.

The API36 phone emulator initially presented an unrelated Digital Wellbeing ANR dialog, blocking keyboard tests. Those failures were not counted as successful verification. The temporary read-only emulator was taken offline, Digital Wellbeing disabled, and the real software IME enabled before clean reruns. The final complete 73-test run includes the navigation and empty-workspace recents regressions.

## Manual final-build checks

- Launched the final debug APK through `MainActivity`, using the real root theme and app navigation. New note opened the native caret and software keyboard immediately, without a naming dialog. Inspected the placeholder, save status, palette/IME clearance and system-bar appearance.
- Typed a Markdown fixture, waited for a successful save, then changed its destination from outside the app and continued typing. The conflict notice retained writing focus and exposed Retry/Save a copy; the external revision remained unchanged.
- Confirmed exact source, baseline and selection in the private recovery file, force-stopped the app, and relaunched it with a different process ID. Launch showed recent notes. Reopening the note restored the full draft and selection while preserving the conflicting destination. This exercised a real process restart after recovery acknowledgement.
- Used Save a copy through Android's native Downloads picker. Read back the resulting Markdown file and verified its exact source bytes. The original conflict remained visible and its external revision was preserved.
- Inspected native screenshots of the new-note editor, recent-note launch, save conflict, and recovered editor after restart. Captures are retained in the Codex visualization directory under `kern-quick-capture`; no screenshot binaries were added to the repository.
- All manual data was disposable and confined to the temporary read-only emulator. No build was shared or distributed.

## Limits

- Automated recovery tests recreate the store/view model and clear its lifecycle owner; the manual check also force-stopped/relaunched the real app after a recovery acknowledgement. Neither establishes abrupt-power-loss behavior or survival of an input event killed before its asynchronous recovery write completes.
- A storage provider and another app can mutate a document outside Kern's project lock. Baseline/indexed-identity checks are best effort across external actors; arbitrary SAF providers do not offer a shared atomic compare-and-swap.
- Only the available API36 phone emulator is used here. API26, physical fold sensing/feedback and a full TalkBack walkthrough are separate device checks; existing fold/large-font tests still run in the suite.
- The build's existing Kotlin/R8 metadata warnings are retained. There is no dependency upgrade or warning suppression in this change.
- Wider toolbar changes, content search, read-only review mode and the remaining typing-preference work are outside this implementation phase.
