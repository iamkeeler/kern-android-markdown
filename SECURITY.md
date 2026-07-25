# Security Policy

## Supported versions

Kern is pre-release. Security reports should target the current `main` branch unless a public release branch is created later.

## Reporting a vulnerability

Please do not report security vulnerabilities through public GitHub issues.

Send reports to:

```text
gary@attach.design
```

Include:

- affected version or commit
- steps to reproduce
- impact assessment
- whether the issue exposes local files, user content, credentials, or release infrastructure
- any suggested remediation, if known

## Secrets policy

Do not commit:

- `app/google-services.json`
- keystores or upload keys
- service-account JSON
- Play Console credentials
- GitHub tokens
- local `keystore.properties`
- local `local.properties`

CI expects sensitive files to be supplied through GitHub Actions secrets, especially:

```text
GOOGLE_SERVICES_JSON_BASE64
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
GOOGLE_PLAY_SERVICE_ACCOUNT_JSON
```

## Current known security/open-source note

The normal repository branch and tag history has been scrubbed of `app/google-services.json`. GitHub hidden pull-request refs may still require GitHub Support cleanup before the existing repository is made public.

See:

```text
docs/security-scrub-report.md
```
