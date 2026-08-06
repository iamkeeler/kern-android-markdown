# Repository governance and PR process

Kern uses a protected-main workflow. `main` should stay releasable; day-to-day work happens on short-lived branches and lands through pull requests.

## Branch policy

- `main` is the protected release/readiness branch.
- Work in topic branches, not directly on `main`.
- Keep branches focused and short-lived.

Recommended branch names:

| Type | Pattern | Example |
|---|---|---|
| Feature | `feat/<short-description>` | `feat/folder-search` |
| Fix | `fix/<short-description>` | `fix/editor-padding` |
| Docs | `docs/<short-description>` | `docs/play-store-copy` |
| CI/release | `ci/<short-description>` | `ci/website-ftp-deploy` |
| Chore | `chore/<short-description>` | `chore/dependency-cleanup` |

## Standard workflow

```bash
git switch main
git pull --ff-only origin main
git switch -c ci/example-change

# make changes
./gradlew test lint bundleRelease

git add <files>
git commit -m "ci: describe the change"
git push -u origin HEAD
```

Then open a pull request into `main`.

## Pull request requirements

Every PR should include:

- what changed
- why it changed
- how it was verified
- screenshots for UI changes
- confirmation that no secrets/local config/generated build outputs were committed

Before merge, the PR should pass:

```bash
./gradlew test lint bundleRelease
```

The GitHub Actions `Release Readiness` workflow also runs tests, lint, and packaging checks on PRs to `main`.

## Recommended main branch protection

Configure GitHub branch protection for `main` with:

- require a pull request before merging
- require at least one approval when collaborators are active
- require status checks to pass before merging
- require branches to be up to date before merging
- require conversation resolution before merging
- prevent force pushes
- prevent branch deletion
- include administrators once the repo is public/stable

Recommended required status check:

```text
Test, lint, and package release candidate
```

## Applying protection with GitHub CLI

If authenticated with `gh` and you have admin rights:

```bash
gh api \
  --method PUT \
  repos/iamkeeler/kern-android-markdown/branches/main/protection \
  --input - <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["Test, lint, and package release candidate"]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 1
  },
  "required_conversation_resolution": true,
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_linear_history": false,
  "lock_branch": false,
  "allow_fork_syncing": true
}
JSON
```

## Emergency changes

If a direct `main` change is ever unavoidable:

1. make the smallest possible change
2. immediately run release readiness locally
3. open a follow-up issue documenting why the bypass happened
4. restore the branch workflow afterward
