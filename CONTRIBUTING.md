# Contributing to Kern

Thanks for your interest in Kern. The project is currently in pre-release cleanup for Google Play and public open-source launch, so contribution scope is intentionally narrow.

Kern is licensed under the Apache License 2.0. Contributions are accepted under the project license unless a separate written agreement says otherwise. Please read [LICENSE](LICENSE) before contributing. Kern’s name, logo, and official branding remain protected; see the [trademark and branding policy](README.md#trademark-and-branding).

## Current contribution stance

Until the first public release is complete, the preferred contribution types are:

- bug reports with clear reproduction steps
- documentation fixes
- small, isolated code fixes
- release-readiness and security hardening improvements

Large feature PRs should wait until the architecture and roadmap are published.

## Development setup

1. Install JDK 17.
2. Install Android SDK Platform 36 and Build Tools 36.0.0 or newer.
3. Copy Firebase config template:

```bash
cp app/google-services.json.example app/google-services.json
```

4. Replace placeholder Firebase values if you need Firebase-backed workflows.
5. Run verification:

```bash
./gradlew test lint bundleRelease
```

## Architecture rules

Kern has a strict boundary between domain processing and Android/Compose UI binding.

Pure markdown/text processing code must not depend on:

- `android.content.Context`
- Jetpack Compose UI types such as `TextFieldValue`
- `MutableState`
- Android runtime APIs

The UI/platform layer owns Compose state, Android storage bindings, and operating-system integration.

See the [coding standards](contributor-guides/coding-standards.md), [design guide](contributor-guides/design-guide.md), and [product requirements](contributor-guides/product-requirements.md).

## Branch and pull request workflow

Do not work directly on `main`. Use short-lived topic branches and open a pull request back to `main`.

Recommended branch prefixes:

- `feat/` for features
- `fix/` for bug fixes
- `docs/` for documentation
- `ci/` for release automation and GitHub Actions
- `chore/` for maintenance

Example:

```bash
git switch main
git pull --ff-only origin main
git switch -c ci/example-change
```

See `docs/repository-governance.md` for the full PR process and recommended branch protection settings.

## Before opening a pull request

Run:

```bash
./gradlew test lint bundleRelease
```

A PR should include:

- a focused description of the change
- screenshots for UI changes
- test notes
- confirmation that no secrets or generated build outputs are included

## Git hygiene

- Keep PRs small and focused.
- Do not commit directly to `main` unless it is an emergency maintenance change.
- Do not commit `app/google-services.json`, keystores, local properties, or generated build outputs.
- Avoid unrelated formatting churn.
- Do not include credentials, tokens, or local machine paths in committed files.
