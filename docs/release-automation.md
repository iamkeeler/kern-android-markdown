# Android release automation

This project now has two release-oriented GitHub Actions workflows:

1. `.github/workflows/release-readiness.yml`
   - Runs on PRs to `main`, pushes to `main`, and manual dispatch.
   - Runs unit tests, Android lint, debug APK packaging, and unsigned release APK packaging.
   - Uploads lint reports and the unsigned release APK as artifacts.

2. `.github/workflows/google-play-release.yml`
   - Runs manually or when a `v*` tag is pushed.
   - Builds a signed release Android App Bundle (`.aab`).
   - Uploads the bundle to Google Play using the selected track/status.

3. `.github/workflows/deploy-website-ftp.yml`
   - Runs manually or when a `website-v*` tag is pushed.
   - Uploads the static `website/` directory to the configured FTP server.

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
| `WEBSITE_FTP_SERVER` | FTP server hostname/address for static website deployment. |
| `WEBSITE_FTP_USERNAME` | FTP username for website deployment. |
| `WEBSITE_FTP_PASSWORD` | FTP password for website deployment. |

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
2. Create a version tag, for example:

```bash
git tag v0.1.11
git push origin v0.1.11
```

3. The Google Play release workflow builds and uploads the signed `.aab`.
4. Start with internal testing before alpha/beta/production.

## Website deployment

The website deploy workflow publishes the contents of `website/` over FTP when a website tag is pushed:

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

- Android lint currently reports `0 errors`, `66 warnings`, and `1 hint`; the new release workflows include an explicit guard to fail if future lint errors appear.
- The existing Firebase distribution workflow builds a debug APK. Google Play needs a signed `.aab`, which is what the new Play workflow builds.
- `app/google-services.json` is intentionally ignored by git for open-source safety. Keep the real file local or store it as `GOOGLE_SERVICES_JSON_BASE64` in GitHub Actions secrets; `app/google-services.json.example` is the public template.
