# Kern

Kern is an open-source Markdown editor for Android. Write on your phone, tablet, or foldable with live formatting, local files, and a quiet document-first workspace.

<img src="store-assets/google-play/app-icon-512.png" alt="Kern app icon" width="96">

[https://kern.attach.design.](https://kern.attach.design.) · [GitHub](https://github.com/iamkeeler/kern-android-markdown) · [Privacy policy](https://kern.attach.design/privacy.html)

## Download builds

- **Test build:** Download the current Android APK from the [`test` prerelease](https://github.com/iamkeeler/kern-android-markdown/releases/tag/test).
- **Stable release:** Once public releases begin, download the newest build from [Latest release](https://github.com/iamkeeler/kern-android-markdown/releases/latest).
- **All builds:** Browse [all GitHub Releases](https://github.com/iamkeeler/kern-android-markdown/releases) for previous test and release builds.

## Why Kern

- **Live Markdown preview** — See headings, emphasis, lists, links, and code as you write.
- **Local files** — Open and edit files through Android’s file access system.
- **Readable editing** — Use clear typography, adjustable text size, themes, and focused editing views.
- **Writing tools** — Check word counts, reading grade level, sentence complexity, and other document metrics.
- **Large-screen layouts** — Use a file tree beside the editor on tablets and foldables.
- **Open source** — Read the code, follow development, and suggest changes on GitHub.

## See it in action

![Kern editing a Markdown document](website/screenshots/framed/phone-01-live-preview-1080x1920.png)

More screenshots and source assets are in [`website/screenshots/`](website/screenshots/) and [`store-assets/`](store-assets/).

## Build locally

### Requirements

- JDK 17
- Android SDK Platform 36
- Android Build Tools 36.0.0 or newer

The real Firebase configuration is not tracked. Create a local configuration from the public template:

```bash
cp app/google-services.json.example app/google-services.json
```

Replace the placeholder values with your Firebase project configuration, then run:

```bash
./gradlew test lint bundleRelease
```

The release bundle is written to `app/build/outputs/bundle/release/app-release.aab`.

## CI/CD pipeline

GitHub Actions provides checks, tester distribution, releases, and website deployment:

1. **Release Readiness** (`release-readiness.yml`) can be run manually. It restores a safe Firebase template when no secret is supplied, runs unit tests and lint, packages an unsigned release APK, and uploads lint reports and the APK as artifacts.
2. **Test distribution** (`android-build-distribution.yml`) runs when the `test` tag is pushed. It increments the patch version, builds a debug APK, sends it to Firebase App Distribution for the `main-testers` group, and attaches the APK to the [`test` GitHub prerelease](https://github.com/iamkeeler/kern-android-markdown/releases/tag/test).
3. **Google Play Release** (`google-play-release.yml`) runs manually or for `release`/`v*` tags. It restores signing material from GitHub secrets, verifies tests and lint, builds signed APK/AAB files, attaches them to [GitHub Releases](https://github.com/iamkeeler/kern-android-markdown/releases), and uploads the AAB to the selected Play track. Production completion is intentionally blocked in the workflow.
4. **Website deployment** (`deploy-website-ftp.yml`) runs manually or for `website-v*`, `website/*`, and `website-*` tags. It verifies the static site, then deploys `website/` over FTP.
5. **Google Play Listing** (`google-play-listing.yml`) runs manually after an internal app upload. It packages the approved English metadata and store graphics with Fastlane, then uploads them to Google Play as a draft on the internal track. It does not upload an app binary or publish to production.

Release credentials belong in GitHub Actions secrets. Never commit Firebase configuration, keystores, service-account files, tokens, or local properties. See [`docs/release-automation.md`](docs/release-automation.md) for the secret list and release procedure.

The listing workflow also requires the Play service account to have **Manage store presence** permission. See [`docs/google-play-listing-automation.md`](docs/google-play-listing-automation.md).

## Assets

- [`store-assets/google-play/`](store-assets/google-play/) — app icon, feature graphic, store screenshots, and contact sheet.
- [`website/screenshots/`](website/screenshots/) — website screenshots, including framed README images.
- [`tools/generate_store_graphics.py`](tools/generate_store_graphics.py) — generates store graphics.
- [`tools/generate_framed_screenshots.py`](tools/generate_framed_screenshots.py) — creates framed screenshots for the website and README.
- [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md) — direct runtime and build dependency license notices.

## Privacy

Kern does not host your documents or require an account. You choose where your files are stored. Kern uses Firebase Analytics and Crashlytics; its own analytics events do not include document titles, document text, or file paths. See the [privacy policy](https://kern.attach.design/privacy.html).

## Contributing

Start with a focused issue or pull request. Good first contributions include bug fixes, documentation, tests, accessibility improvements, and release hardening.

Read [`CONTRIBUTING.md`](CONTRIBUTING.md) for the workflow, and [`contributor-guides/`](contributor-guides/) for the shared design, product, coding, and web standards.

## Project status

Kern is in active development. The Android app and website are being prepared for the first public Google Play release.

## Security

Do not report security vulnerabilities in public issues. Email [gary@attach.design](mailto:gary@attach.design) with the affected version, steps to reproduce, and possible impact. See [`SECURITY.md`](SECURITY.md) for the reporting policy.

## License

Kern’s original source is released under the [Apache License 2.0](LICENSE). You may use, study, modify, distribute, and sell versions of the code, provided you follow the license terms and preserve required notices. Third-party libraries retain their own licenses; see [`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md).

## Trademark and branding

The name **Kern**, the Kern logo, the app icon, and other project marks identify the official project and are not licensed under Apache 2.0. You may use them to make accurate, nominative references to Kern, but you may not imply that a fork, modified app, service, or distribution is official or endorsed by Kern without permission.

Forks and modified distributions should use a distinct name, package identity, icon, and visual branding. The source license allows commercial forks, but it does not grant rights to Kern’s trademarks or branding.
