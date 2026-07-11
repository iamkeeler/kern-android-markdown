# Android "Open with Kern" integration

Status: implemented MVP import-copy behavior on the PR branch.

Goal: show Kern as an option when a user taps a Markdown or plain-text document in Android file providers, Drive-style apps, or file managers.

## Implemented files

- `app/src/main/AndroidManifest.xml`
  - registers Kern for `ACTION_VIEW` on `content://` and `file://` text/markdown MIME types
  - sets `MainActivity` to `singleTop` so already-open Kern sessions receive new files through `onNewIntent()`
- `app/src/main/java/com/attachdesign/kern/MainActivity.kt`
  - converts incoming `ACTION_VIEW` intents into one-shot `ExternalOpenRequest` events
- `app/src/main/java/com/attachdesign/kern/ExternalOpenRequest.kt`
  - small event model for external file opens
- `app/src/main/java/com/attachdesign/kern/Navigation.kt`
  - imports the incoming file and navigates to the editor
- `app/src/main/java/com/attachdesign/kern/data/storage/IncomingFileImporter.kt`
  - reads the external URI, validates supported type, creates/uses `Opened Files`, writes a local copy, and inserts file metadata
- `app/src/main/java/com/attachdesign/kern/data/storage/StorageManager.kt`
  - adds `fileExists()` for duplicate-name handling

## Summary

This is feasible and fits Kern's product direction. The implementation is moderate-sized because Android's file-open flow is a URI/intent handoff, not a normal filesystem path.

Recommended MVP: **import a copy into a Kern-managed "Opened Files" workspace and open it in the editor.**

This avoids accidentally pretending Kern can write back to every external provider. A later iteration can add "edit in place" for providers that grant write permission.

## Required Android registration

Add `ACTION_VIEW` intent filters to `MainActivity` in `app/src/main/AndroidManifest.xml`.

Recommended first-pass filters:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:scheme="content" />
    <data android:mimeType="text/plain" />
    <data android:mimeType="text/markdown" />
    <data android:mimeType="text/x-markdown" />
</intent-filter>
```

Consider a separate legacy `file://` filter if testing shows common file managers still emit file URIs:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:scheme="file" />
    <data android:mimeType="text/plain" />
    <data android:mimeType="text/markdown" />
    <data android:mimeType="text/x-markdown" />
</intent-filter>
```

Notes:

- `content://` is the modern path and should be the main target.
- Many Android providers label `.md` files as `text/plain`, not `text/markdown`.
- Extension-only matching is unreliable for `content://` URIs because the file extension may not be part of the URI path.
- Avoid claiming broad binary types like `application/octet-stream` in the MVP; that could make Kern appear for too many unrelated files.

## Runtime handling

`MainActivity` needs to inspect incoming intents:

- `Intent.ACTION_VIEW`
- `intent.data` as a `content://` or `file://` URI
- `intent.type` for MIME hints
- `Intent.FLAG_GRANT_READ_URI_PERMISSION`
- optionally `Intent.FLAG_GRANT_WRITE_URI_PERMISSION` for future edit-in-place support

Recommended lifecycle handling:

1. Set `android:launchMode="singleTop"` on `MainActivity`.
2. Handle the initial intent in `onCreate`.
3. Override `onNewIntent()` so Kern can receive another file while already open.
4. Pass the pending URI into Compose navigation state.
5. Import/open the file once, then clear the pending URI so configuration changes do not re-import it.

## Recommended MVP behavior

1. User taps `README.md` or `notes.txt` in a file manager.
2. Android chooser shows Kern.
3. Kern reads the URI stream using `contentResolver.openInputStream(uri)`.
4. Kern resolves a display filename using `OpenableColumns.DISPLAY_NAME`, falling back to the URI last segment.
5. Kern validates extension/MIME:
   - `.md`
   - `.markdown`
   - `.mdown`
   - `.txt`
   - `text/plain`
   - `text/markdown`
   - `text/x-markdown`
6. Kern creates/uses a local project such as `Opened Files`.
7. Kern writes the imported content into local storage through `StorageManager.writeFile()`.
8. Kern inserts/updates the `FileEntity` row.
9. Kern navigates to `EditorKey(projectId, importedFilePath)`.

## Why import-copy first

Pros:

- Lower risk for Play Store launch.
- Works consistently across providers.
- No new database schema needed.
- Uses the existing `StorageManager.readFile/writeFile` and editor pipeline.
- Avoids data loss if a provider grants read access only.

Tradeoff:

- Edits apply to Kern's imported copy, not necessarily the original file.

UX copy should make this clear, e.g.:

```text
Imported to Kern
README.md was copied into Opened Files.
```

## Future edit-in-place version

A more advanced version can store the original document URI and write back if the provider grants write permission.

That likely requires one of these approaches:

1. Add a new imported-document table that tracks:
   - original URI
   - display name
   - MIME type
   - read/write grant flags
   - last opened timestamp
2. Or extend the existing project/file model to represent single-document `content://` URIs.

The current `StorageManager` assumes content URIs are tree URIs for external projects (`DocumentFile.fromTreeUri`). A single-file open flow uses `DocumentFile.fromSingleUri`, so edit-in-place is a separate storage mode rather than a tiny manifest-only change.

## Files likely to change

- `app/src/main/AndroidManifest.xml`
  - add `ACTION_VIEW` filters
  - consider `android:launchMode="singleTop"`
- `app/src/main/java/com/attachdesign/kern/MainActivity.kt`
  - parse `ACTION_VIEW` intent
  - handle `onNewIntent()`
- `app/src/main/java/com/attachdesign/kern/Navigation.kt`
  - accept a pending external file URI and navigate after import
- New helper, recommended:
  - `app/src/main/java/com/attachdesign/kern/data/storage/IncomingFileImporter.kt`
- Optional tests:
  - filename sanitization
  - MIME/extension allowlist
  - duplicate-name handling

## Test plan

Manual test matrix:

| Source | File | Expected |
|---|---|---|
| Android Files app | `.md` | Kern appears in chooser and imports/opens |
| Android Files app | `.txt` | Kern appears in chooser and imports/opens |
| Google Drive / cloud provider | `.md` | Kern appears if provider exposes a text MIME type |
| Gmail/attachment preview | `.txt` | Kern appears if Android emits `ACTION_VIEW` |
| Unsupported file | `.pdf` / image | Kern should not appear |
| Kern already open | `.md` | Existing activity receives `onNewIntent()` and opens/imports |

Automated/local verification:

```bash
./gradlew test lint assembleDebug
```

ADB smoke tests after installing a debug build:

```bash
adb shell am start \
  -a android.intent.action.VIEW \
  -t text/plain \
  -d 'content://...'
```

Real provider testing is still required because chooser behavior depends on the provider's MIME type metadata.

## Estimated implementation size

MVP import-copy path:

- Manifest: small
- Activity/Compose URI plumbing: small-to-medium
- Import helper: medium
- Manual QA: medium

Expected effort: about one focused implementation pass plus device testing.

Edit-in-place path:

- Storage model changes: medium-to-large
- Permission/write-back handling: medium
- QA across providers: larger

Recommended launch path: ship the MVP import-copy behavior first, then add edit-in-place after Play internal testing feedback.
