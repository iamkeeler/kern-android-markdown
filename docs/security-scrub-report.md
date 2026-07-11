# Security scrub report

Date: 2026-07-11
Project: Kern / AndroidMD-App

## Summary

Security scanning was run before open-sourcing using:

- `git-secrets`
- `gitleaks`
- `trufflehog`
- `git-filter-repo`

The normal repository history was rewritten to remove `app/google-services.json` and then force-pushed to GitHub.

## Actions taken

1. Removed a verified GitHub personal access token from the local git remote URL in `.git/config`.
   - The remote now uses `https://github.com/iamkeeler/AndroidMD-App.git` with no embedded token.
   - This token was found in local git config, not in committed git history.
2. Stopped tracking `app/google-services.json`.
3. Added `app/google-services.json` to `.gitignore`.
4. Added `app/google-services.json.example` with the Firebase API key placeholder redacted.
5. Updated GitHub Actions to restore `app/google-services.json` from `GOOGLE_SERVICES_JSON_BASE64`.
6. Rewrote normal git history using `git filter-repo` to remove `app/google-services.json`.
7. Force-pushed rewritten branches to GitHub.
8. Deleted the old `test` tag because it still referenced pre-scrub history.
9. Verified the local repo still builds with the ignored local `app/google-services.json` present.

## Backups

Local history backups were created before destructive rewrites:

```text
~/Developer/AndroidMD-App-history-backups/
```

These backups may contain the removed Firebase config and should remain private.

## Scan results

### Local repo after rewrite

```text
gitleaks: no leaks found
trufflehog verified secrets: 0
```

### Current public/tracked tree

```text
gitleaks: no leaks found
trufflehog verified secrets: 0
```

### GitHub remote normal branches/tags

Normal branches were force-updated after the rewrite. The old `test` tag was deleted.

### GitHub hidden pull-request refs

A mirror verification of GitHub still reports the historical Firebase/GCP API-key finding under GitHub-managed hidden pull-request refs:

```text
refs/pull/12/head
refs/pull/13/head
...
refs/pull/54/head
refs/pull/40/merge
refs/pull/44/merge
```

These refs are controlled by GitHub and cannot be force-pushed or deleted with normal git pushes. GitHub rejected attempts to update them with:

```text
deny updating a hidden ref
```

## Required manual follow-up

### 1. Rotate/revoke the GitHub token

A verified GitHub personal access token was found in local `.git/config` and removed from the remote URL. Even though it was local-only and not committed, it should be treated as exposed.

Recommended action: revoke or rotate the token in GitHub.

### 2. Restrict or rotate the Firebase/GCP API key

`app/google-services.json` was removed from normal history, but GitHub hidden pull-request refs still retain the old object until GitHub purges them.

Recommended action:

1. Restrict the Firebase/GCP API key in Google Cloud Console to the Android app/package/signing certificate where possible.
2. Consider rotating the key.
3. Open a GitHub Support request asking them to purge cached/hidden pull-request refs containing `app/google-services.json` / commit `ba7f05458cfb1b157dc02dd8af8e71e9500f2807`.

### 3. Do not make the existing GitHub repo public until the hidden refs are addressed

The normal branch/tag history is scrubbed, but a full `git clone --mirror` can still fetch GitHub's hidden pull-request refs that contain the old Firebase config. For a fully clean public launch, either:

- get GitHub Support to purge those hidden refs, or
- create a fresh public repo from the scrubbed current history if support cannot purge them quickly.

## Verification commands used

```bash
./gradlew test lint bundleRelease

gitleaks git . --no-banner --redact --report-format json
trufflehog git file://$(pwd) --only-verified --json

git filter-repo --path app/google-services.json --invert-paths --force
git push --mirror origin
```

## Current status

- Local current repo: clean for `gitleaks` after pruning local refs.
- Normal GitHub branches/tags: rewritten and scrubbed.
- GitHub hidden PR refs: still contain historical Firebase config and require GitHub Support or fresh-public-repo fallback.
- Local build: passing.
