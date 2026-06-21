# GitHub Integration Assessment

Based on the analysis of the current `com.attachdesign.kern` codebase, adding GitHub integration for file synchronization is a **moderate-to-hard** effort.

## Current Architecture
- The app has a local SQLite cache engine via Room DB (`Database.kt`).
- The `SyncEngine.kt` contains a stubbed out synchronization system with an enum `SyncProvider` (`NONE`, `GOOGLE_DRIVE`, `DROPBOX`, `ONEDRIVE`).
- The `FileEntity` tracks synchronization states on local files (`"SYNCED"`, `"PENDING"`, `"FAILED"`).

## Implementation Requirements

### 1. Authentication (OAuth 2.0)
Currently, the app lacks an authentication system. A secure OAuth 2.0 flow must be implemented:
- Redirect the user to the GitHub authorization page.
- Handle the callback to retrieve an access token.
- Securely store the token locally (e.g., using Android's `EncryptedSharedPreferences`).

### 2. Synchronization Layer
`GITHUB` needs to be added to the `SyncProvider` enum. Since Android doesn't natively support Git commands, there are two primary approaches:
- **Using a Git Library (e.g., Eclipse JGit):** Provides pure Git capabilities (clone, commit, push, pull). It's robust but can be heavy and complex to configure on Android.
- **Using GitHub REST API:** Utilizing a network client (like Retrofit) to push and pull file updates directly to/from a repository. This is more lightweight for mobile but requires building manual API calls instead of true Git operations.

### 3. State Management & Conflicts
The current `SyncEngine` assumes a simple one-way upload sweep. With GitHub, the system must:
- Pull remote changes.
- Handle merge conflicts if a file was edited both locally and on GitHub.
- Provide a user interface to resolve these conflicts.

### 4. UI Adjustments
New UI flows and settings updates are required:
- "Sign in with GitHub" authentication button.
- Repository selection interface to choose which remote repo syncs with a local project.
