# Kern

Kern is a focused markdown reader and writing workspace for Android. It is built around local files, fast editing, and a calm document-first interface.

The app is currently being prepared for Google Play internal testing and public open-source release.

## What Kern is for

- Reading and editing markdown documents on Android
- Working with local project folders through Android's Storage Access Framework
- Keeping a file tree and editor workspace close together on larger screens
- Maintaining a quiet, typography-forward writing surface

## Status

Kern is pre-release software. The current engineering focus is:

- Google Play release automation
- open-source repository cleanup
- privacy and security review
- Play Store listing and website preparation
- release-quality lint and architecture cleanup

## Build locally

### Requirements

- JDK 17
- Android SDK / command-line tools
- Android SDK Platform 36
- Android Build Tools 36.0.0 or newer

### Firebase config

The real Firebase config is intentionally not tracked in git.

For local development, copy the template and replace the placeholder values:

```bash
cp app/google-services.json.example app/google-services.json
```

For CI, store the real file as a base64 GitHub Actions secret named:

```text
GOOGLE_SERVICES_JSON_BASE64
```

### Verify

```bash
./gradlew test lint bundleRelease
```

Expected release artifact:

```text
app/build/outputs/bundle/release/app-release.aab
```

## Release automation

Release automation lives in:

- `.github/workflows/release-readiness.yml`
- `.github/workflows/google-play-release.yml`
- `.github/workflows/deploy-website-ftp.yml`
- `docs/release-automation.md`

The Google Play workflow requires repository secrets for Firebase config, Play Console upload credentials, and release signing credentials.

Website deployment uses FTPS and runs when a `website-v*` tag is pushed. It requires these GitHub Actions secrets:

```text
WEBSITE_FTP_SERVER
WEBSITE_FTP_USERNAME
WEBSITE_FTP_PASSWORD
```

## Repository workflow

Kern uses a protected-main workflow: work in branches, open pull requests, and merge to `main` only after release-readiness checks pass.

See:

```text
docs/repository-governance.md
```

## Security and open-source readiness

See:

```text
docs/security-scrub-report.md
```

Important: normal branch/tag history has been scrubbed, but GitHub hidden pull-request refs may require GitHub Support cleanup before making the existing GitHub repository public.

## Design direction

Kern uses a restrained, document-first visual system:

- warm off-white canvas
- charcoal ink text
- muted steel metadata
- utility blue action accents
- spacious 8dp rhythm
- minimal surfaces and typography-led hierarchy

See `design.md` for detailed interface rules.

## Contributing

Kern is not yet broadly open for outside contributions. The initial public release will prioritize small, focused issues after the Play Store preparation work is complete.

See `CONTRIBUTING.md` for development expectations.

## Security

Please do not open public issues for security problems. See `SECURITY.md`.

## License

Apache License 2.0. See `LICENSE`.
