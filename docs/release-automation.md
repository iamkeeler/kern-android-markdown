# Android release automation

This project now has four release-oriented GitHub Actions workflows:

1. `.github/workflows/release-readiness.yml`
   - Runs manually from GitHub Actions.
   - Runs unit tests, Android lint, debug APK packaging, and unsigned release APK packaging.
   - Uploads lint reports and the unsigned release APK as workflow artifacts.

2. `.github/workflows/google-play-release.yml`
   - Runs manually or when a `release` or `v*` tag is pushed.
   - Builds a signed release APK and Android App Bundle (`.aab`).
   - Uploads the bundle to Google Play using the selected track/status.
   - Attaches the signed APK and AAB to the tagged GitHub Release.

3. `.github/workflows/android-build-distribution.yml`
   - Runs manually on a working branch or when the `test` tag is pushed.
   - Builds a debug APK and sends it to Firebase App Distribution.
   - Uploads the APK as a workflow artifact.
   - Attaches the APK to the `test` GitHub prerelease for direct downloads.

4. `.github/workflows/deploy-website-ftp.yml`
   - Runs manually or when a `website-v*` tag is pushed.
   - Uploads the static `website/` directory to the configured FTPS server.

## Version and build number allocation

Every Firebase and Play distribution reserves its version before Gradle starts. `scripts/build_version.py` uses the shared `codex/build-version-counter` branch, whose `version.json` and commit history record each reserved version, Android version code, source commit, ref, workflow run and attempt. Both publishing workflows share a concurrency group; fast-forward-only counter updates also reject and retry competing reservations safely.

- Branch/manual builds and the generic `test`/`release` tags increase the latest patch and version code by one. For example, the existing `0.1.39 (40)` becomes `0.1.40 (41)`, then `0.1.41 (42)`, including reruns of the same source commit.
- The counter is shared across branches and Firebase/Play workflows. An older checkout cannot reuse or decrease its values. The checked-in `gradle.properties` provides the initial baseline and a lower bound for intentional major/minor changes; it is not the latest distributed-build record.
- A semantic `vX.Y.Z` tag reserves that exact version only if it is newer than the counter and at least the source version. Reusing an already reserved version tag fails before distribution; use a newer tag or manual dispatch. This prevents a tag from claiming a different APK version.
- Reservations are durable before building. Failed/canceled builds consume their numbers, so gaps are expected and numbers are never recycled. Do not force-push, delete or reset the counter branch.
- Gradle runs in a **separate process after allocation** so it reads the updated properties. The existing `incrementVersionPatch` task remains a local development helper; calling it alongside a packaging task in one invocation does not update that invocation's already configured version.
- Before upload, `aapt` reads the APK's actual `versionName` and `versionCode`; either mismatch fails the workflow. Artifacts include `build/build-version.json` with the reserved values, source commit/run/attempt and verified APK SHA-256. Firebase notes and artifact names include both version and build number.
- Ordinary local compiles, unit tests and release-readiness checks do not reserve distribution numbers. Shared builds must go through the Firebase or Play workflow rather than uploading an unversioned local artifact.

Versioning regression checks (no Android build required):

```sh
python3 -m unittest discover -s scripts/tests -v
actionlint .github/workflows/android-build-distribution.yml .github/workflows/google-play-release.yml .github/workflows/release-readiness.yml
```

The tests use temporary bare Git repositories to exercise fresh checkouts, competing reservations, failed pushes and retries without modifying the real counter.

## Required GitHub repository secrets

Add these in GitHub → repository → Settings → Secrets and variables → Actions:

| Secret | Purpose |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded release keystore file. |
| `ANDROID_KEYSTORE_PASSWORD` | Password for the release keystore. |
| `ANDROID_KEY_ALIAS` | Alias of the signing key inside the keystore. |
| `ANDROID_KEY_PASSWORD` | Password for the signing key. |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | Full JSON for a Google Play Console service account with release permissions. |
| `GOOGLE_SERVICES_JSON_BASE64` | Base64-encoded Firebase `google-services.json`; kept out of git for open-source safety. |
| `WEBSITE_FTP_SERVER` | FTPS server hostname/address for static website deployment. |
| `WEBSITE_FTP_USERNAME` | FTPS username for website deployment. |
| `WEBSITE_FTP_PASSWORD` | FTPS password for website deployment. |

## Encoding secrets

From the machine that has the release keystore:

```bash
base64 -i path/to/release.keystore | pbcopy
```

Paste that value into `ANDROID_KEYSTORE_BASE64`.

For Firebase config, encode the local `app/google-services.json`:

```bash
base64 -i app/google-services.json | pbcopy
```

Paste that value into `GOOGLE_SERVICES_JSON_BASE64`.

`app/google-services.json` is intentionally ignored by git. Use `app/google-services.json.example` as the public template.

## Google Play Console setup

1. In Google Play Console, create or select the app with package name `com.attachdesign.kern`.
2. Enable API access for Play Console if it is not already enabled.
3. Create a service account in Google Cloud.
4. Grant it Play Console access for this app. Start with release-management access only; avoid broad owner/admin access.
5. Download the service-account JSON and store it in `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.
6. For first upload, run the workflow manually with:
   - `track`: `internal`
   - `status`: `draft`

## Recommended release flow

1. Merge code to `main` only after `Release Readiness` is green.
2. Choose a version strictly newer than the shared counter, then create its version tag, for example:

```bash
git tag v0.2.0
git push origin v0.2.0
```

3. The Google Play release workflow builds and uploads the signed `.aab`, then attaches the signed APK and AAB to the GitHub Release for the tag.
   - The workflow refuses a manual `production` + `completed` dispatch so an accidental click cannot publish directly to production. Use draft/in-progress production upload or promote manually in Play Console when ready.
4. Start with internal testing before alpha/beta/production.

GitHub download locations:

- Test builds: the `test` prerelease assets.
- Public release builds: the APK asset on the matching `v*` or `release` GitHub Release.
- Manual workflow runs: the Actions run’s `signed-release-apk-<version>-<build>` and `signed-release-aab-<version>-<build>` artifacts, each including build metadata.

## Website deployment

The website deploy workflow publishes the contents of `website/` over FTPS when a website tag is pushed:

```bash
git tag website-v0.1.0
git push origin website-v0.1.0
```

Required secrets:

```text
WEBSITE_FTP_SERVER
WEBSITE_FTP_USERNAME
WEBSITE_FTP_PASSWORD
```

The workflow can also be run manually from GitHub Actions.

## Current release-readiness caveats

- Android lint currently reports `0 errors`, `67 warnings`, and `1 hint`; the release workflows include an explicit guard to fail if future lint errors appear.
- The existing Firebase distribution workflow builds a debug APK. Google Play needs a signed `.aab`, which is what the new Play workflow builds.
- `app/google-services.json` is intentionally ignored by git for open-source safety. Keep the real file local or store it as `GOOGLE_SERVICES_JSON_BASE64` in GitHub Actions secrets; `app/google-services.json.example` is the public template.
