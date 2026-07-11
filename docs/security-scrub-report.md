# Security scrub report

Date: 2026-07-11
Project: Kern / AndroidMD-App

## Summary

Security scanning was run before open-sourcing using:

- `git-secrets`
- `gitleaks`
- `trufflehog`

## Actions taken

1. Removed a verified GitHub personal access token from the local git remote URL in `.git/config`.
   - The remote now uses `https://github.com/iamkeeler/AndroidMD-App.git` with no embedded token.
   - This token was found in local git config, not in committed git history.
2. Stopped tracking `app/google-services.json`.
3. Added `app/google-services.json` to `.gitignore`.
4. Added `app/google-services.json.example` with the Firebase API key placeholder redacted.
5. Updated GitHub Actions to restore `app/google-services.json` from a new `GOOGLE_SERVICES_JSON_BASE64` secret.
6. Verified the project still builds locally with the ignored local `app/google-services.json` present.

## Scan results

### Current public/tracked tree

A scan of the public/tracked tree showed:

```text
gitleaks: no leaks found
trufflehog verified secrets: 0
```

### Full git history

`gitleaks` still reports one historical finding:

```text
Rule: gcp-api-key
File: app/google-services.json
Commit: ba7f05458cfb1b157dc02dd8af8e71e9500f2807
```

`trufflehog` found no verified secrets in git history.

## Required manual follow-up

### 1. Rotate/revoke the GitHub token

A verified GitHub personal access token was found in local `.git/config` and removed from the remote URL. Even though it was local-only and not committed, it should be treated as exposed.

Recommended action: revoke or rotate the token in GitHub.

### 2. Decide whether to rewrite git history

`app/google-services.json` existed in git history and contains a Firebase/GCP API key. Firebase Android API keys are often intentionally shipped in apps, but before open-sourcing the repo the safest path is:

1. Restrict the Firebase/GCP API key in Google Cloud Console to the Android app/package/signing certificate where possible.
2. Consider rotating the key.
3. Decide whether to rewrite git history to remove `app/google-services.json` before making the repository public.

If history rewrite is approved, use a fresh clone and `git filter-repo` or BFG Repo-Cleaner, then force-push with collaborator coordination.

## Verification commands used

```bash
./gradlew test lint bundleRelease

gitleaks dir /tmp/kern-tracked-tree --no-banner --redact --report-format json --report-path /tmp/kern-security-scan/gitleaks-tracked-current.json
trufflehog filesystem /tmp/kern-tracked-tree --only-verified --json

gitleaks git . --no-banner --redact --report-format json --report-path /tmp/kern-security-scan/gitleaks-history.json
trufflehog git file://$(pwd) --only-verified --json
```

## Current status

- Current public/tracked tree: clean after removing `google-services.json` from git.
- Git history: still contains historical Firebase API key finding unless history is rewritten.
- Local build: passing.
