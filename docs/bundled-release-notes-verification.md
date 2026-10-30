# Bundled release notes verification

Verified on 2026-10-30, on `codex/application-polish`.

## Behavior and release process

- Maintain the short store summary and detailed `app/src/main/assets/release-notes/current.md` together.
- Publishing reserves a fresh version/build, generates matching Markdown for the APK and release downloads before Gradle, and verifies the packaged Markdown byte for byte before upload.
- The first Files view installs a normal, editable Markdown document under the app-owned Files → Release Notes folder. Installation records completion only after a confirmed write and indexing transaction.
- Later builds retain history. Restarts preserve user edits, moves and intentional deletions. Folder/filename collisions use numbered alternatives, and failed writes remain retryable. External workspaces and the selected workspace are preserved.
- Creating the Files root must keep other app-owned and external workspaces reachable. Root navigation now links both kinds and excludes default-root self links.

## Automated verification

Command (JDK 17):

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease \
  :app:connectedDebugAndroidTest \
  -x :app:uploadCrashlyticsMappingFileRelease --max-workers=2
```

Result: **BUILD SUCCESSFUL**. All 314 unit tests and 61 instrumentation tests passed, with no failures, errors or skips. Android lint reported zero errors/fatal issues and 69 warnings. The build also emitted R8 Kotlin-metadata compatibility warnings; release packaging completed successfully. Crashlytics mapping upload was excluded from local verification.

The eight installer unit tests cover first install, upgrades/history/edits, deletion, collisions, disk-to-index recovery, write-failure cleanup/retry, workspace selection and concurrent calls. View-model tests cover optional-install failure and root workspace navigation. Emulator coverage opens the actual recorded note path and checks its contents against the bundled Markdown before opening it through the existing editor navigation.

All 16 Python versioning/release-note tests passed. `actionlint` passed for both publishing workflows. APK Markdown verification passed for the local debug and release packages using temporary local version metadata; local verification did not reserve distribution numbers.

## Emulator inspection

Used the temporary `Kern_Polish_API_36` AVD (Android 16/API 36). Inspected the Release Notes folder screenshot and confirmed the document appears in the existing file browser. Repeated test app reinstalls retained public files, so the final test correctly followed a numbered collision-safe filename rather than assuming the canonical name. Existing files were not overwritten or deleted.

No Room schema change or new reader UI was required. This verification does not replace physical-device fold gesture testing. Distribution remains through the working-branch Firebase workflow; Play publication and stable tagging are separate actions.
