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

## Data handling

This section documents the current data-handling decisions (not a future
roadmap). Changing any of them is a product decision; update this section when
they change.

### Telemetry (opt-out, default-on)

Usage analytics are **enabled by default** and can be disabled by the user in
Settings (opt-out):

- `TelemetryCollection.DEFAULT_ENABLED = true`; `MainActivity` seeds the
  `telemetry_collection_enabled` setting to `"true"` for new installs.
- Events are sent to **Firebase Analytics** (`words_written`,
  `chars_written`, `words_read`, `document_opened`, `document_shared`) plus
  Firebase Crashlytics. Event payloads contain aggregate counts only — no
  document titles, paths, or text content.
- Firebase's SDK performs its own automatic device-identifier collection
  independent of Kern's event payloads.

### Backups

User documents are included in Android Auto Backup / cloud backup **in
plaintext**:

- `app/src/main/res/xml/data_extraction_rules.xml` and
  `app/src/main/res/xml/backup_rules.xml` both
  `<include domain="file" path="."/>`, so every markdown document under the
  app's files directory is uploaded to the user's Google backup.
  (`backup_rules.xml` excludes the `database` domain; `data_extraction_rules.xml`
  includes `sharedpref`.)
- There is **no at-rest encryption** applied to documents or preferences
  (`EncryptedFile`/Tink/`EncryptedSharedPreferences` are not used).

Rationale for the current state: these are the platform-default backup rules,
kept so a user who reinstalls Kern recovers their documents. The tradeoff is
that document contents leave the device unencrypted to a third-party cloud.
