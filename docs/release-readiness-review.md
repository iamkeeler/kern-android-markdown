# Release-readiness review

Date: 2026-07-10
Project: `AndroidMD-App` / app name `Kern`
Package: `com.attachdesign.kern`

## Automated verification run

Local release verification was executed after installing a local JDK 17 and Android SDK 36 toolchain:

```bash
./gradlew test lint bundleRelease
```

Result: **BUILD SUCCESSFUL**

Generated artifact:

```text
app/build/outputs/bundle/release/app-release.aab
```

Current lint summary after fixes:

```text
0 errors
66 warnings
1 hint
```

## Changes made during this review

- Added `.github/workflows/release-readiness.yml`.
- Added `.github/workflows/google-play-release.yml`.
- Added `docs/release-automation.md`.
- Fixed two release-blocking lint issues caused by API 31 splash-screen attributes in the base `values/` theme by moving them to `values-v31/`.
- Fixed the release-blocking `data_extraction_rules.xml` lint issue by removing a database exclusion that was outside the included backup domains.

## Google Play release automation

A GitHub Actions flow now exists for Play Console upload:

```text
.github/workflows/google-play-release.yml
```

It supports:

- Manual launch with selectable Play track: `internal`, `alpha`, `beta`, `production`.
- Manual release status: `draft`, `inProgress`, `halted`, `completed`.
- Tag launch for `v*` tags.
- Unit tests before upload.
- Android lint before upload.
- Signed release `.aab` build.
- Upload through Google Play Developer Publishing API via `r0adkll/upload-google-play@v1`.

Required GitHub secrets are documented in `docs/release-automation.md`.

## Release blockers found and fixed

### 1. API 31 splash-screen attributes were declared in the base theme

Files:

- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-v31/themes.xml`

Original lint errors:

```text
android:windowSplashScreenAnimatedIcon requires API level 31 (current min is 26)
android:windowSplashScreenBackground requires API level 31 (current min is 26)
```

Resolution: base theme now stays API-26 safe, and API-31 attributes live in `values-v31/themes.xml`.

### 2. Invalid data-extraction backup rule

File:

```text
app/src/main/res/xml/data_extraction_rules.xml
```

Original lint error:

```text
. is not in an included path [FullBackupContent]
```

Resolution: removed the database-domain exclusion from cloud backup because only `file` and `sharedpref` are explicitly included.

## Remaining release risks / review findings

### P1 — Release signing is not locally configured

The app can build a release bundle locally, but the checked-in config relies on `keystore.properties` being generated or present. The new Google Play workflow generates this file from GitHub secrets.

Before production release, confirm:

- App signing is configured in Google Play Console.
- The keystore used in CI matches the app-signing/upload-key strategy.
- `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` are set in GitHub Actions secrets.

### P1 — Play Console service-account access must be provisioned

The workflow is ready, but it will not upload until `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` is set and the service account has Play Console release permissions for `com.attachdesign.kern`.

### P2 — Lint warnings remain

There are no lint errors after the fixes, but lint still reports 66 warnings and 1 hint. Highest-value warnings to address before a broad release:

- `targetSdk = 35` while compile SDK is 36. Current Google Play policy accepts target API 35 for new apps/updates starting Aug 31, 2025, but upgrading target SDK should be planned.
- `EditorViewModel` holds a `Context` field (`app/src/main/java/com/attachdesign/kern/ui/editor/EditorViewModel.kt:81`). Ensure this is application context only; never pass an Activity context into this ViewModel.
- Deprecated Compose Material APIs in `MainScreen.kt` and `SettingsScreen.kt`.
- Launcher-icon shape/density warnings.
- Hardcoded dependencies outside the version catalog in `app/build.gradle.kts`.

### P2 — Architecture rule concern: `TextFieldValue` in `EditorViewModel`

Project rules warn against injecting Compose `TextFieldValue` or UI hooks into text-processing engines. The pure parser/domain layer appears separate, but `EditorViewModel` currently stores `TextFieldValue` extensively.

This is acceptable only if the ViewModel is treated as platform/UI binding, not headless domain processing. For stricter architecture, selection/cursor state should be represented as platform-neutral data in the ViewModel, and `TextFieldValue` should stay in the Composable layer.

### P2 — Existing Firebase distribution flow is debug-only

Existing workflow:

```text
.github/workflows/android-build-distribution.yml
```

It builds and distributes `app-debug.apk` to Firebase App Distribution. That is useful for testers but not suitable for Google Play release. The new Play workflow builds `.aab` instead.

### P3 — Dependency updates are available

Lint reports newer versions for Gradle, Android Gradle Plugin, Compose BOM, Material 3, Room, Firebase BOM, Google Services, Navigation3, Robolectric, etc. These are not blockers but should be updated in a controlled branch with regression testing.

## Codex review note

Attempted to run Codex CLI as an independent reviewer, but Codex returned a usage-limit error and said to retry after 5:39 PM. The review above is based on local static inspection and actual Gradle/lint/test output.
