# Google Play listing automation

The manual-only `Google Play Listing` workflow uploads the English store listing metadata and graphics through Fastlane Supply and the Google Play Developer API.

Before running it, grant the Play service account the `Manage store presence` permission for Kern. This is separate from the release permission used by `Google Play Release`.

Run it from GitHub Actions after the first internal AAB upload:

1. Open **Actions** → **Google Play Listing**.
2. Click **Run workflow**.
3. Run it from the branch containing this workflow.

The workflow uploads the listing as a draft and does not upload an AAB or publish to production.
