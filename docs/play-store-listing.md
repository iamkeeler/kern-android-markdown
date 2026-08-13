# Play Store listing draft — Kern

Package: `com.attachdesign.kern`
App name: `Kern`
Status: draft for internal testing / first public listing

## Short description

Write and preview Markdown locally with a calm, focused Android editor.

## Full description

Kern is a focused Markdown editor and reader for Android, designed for writing, reviewing, and organizing local documents.

Write with live preview as Markdown formatting appears while you type. Tap into any passage to work directly with the underlying syntax, without switching between separate editing and preview screens.

With Kern you can:

- Read and edit Markdown files on your Android device
- Open and manage local folders through Android’s file picker
- Navigate documents with a project file tree and breadcrumbs
- Use live preview for headings, emphasis, lists, links, code, and other Markdown formatting
- Apply formatting with the selection toolbar
- Check readability metrics, reading grade level, word count, and sentence complexity
- Choose rendered, syntax-focused, or raw editing views
- Adjust themes, editor typography, and text size
- Share, rename, duplicate, move, and delete documents
- Use a spacious split-pane workspace on tablets and larger Android screens

Kern is local-first. It does not require a Kern account or a Kern-hosted document cloud. Files remain in the storage location you choose through Android’s file access system.

The interface is built around readable typography, clear document structure, and minimal distractions—whether you are writing notes, drafting articles, reviewing documentation, or working with a Markdown-based project.

Kern is open source and built for Android. See the repository license for details.

## Feature bullets

- Read and edit markdown files on Android
- Work with local project folders
- Navigate a simple file tree
- Use a quiet, typography-first editor
- Designed for phones, tablets, and wider Android surfaces
- Local-first posture with no account requirement

## What's new — internal test build

Initial internal testing build for Kern, a local-first markdown workspace for Android. This build validates release signing, Play upload automation, markdown editing basics, and Android file workflows.

## Category recommendation

Primary category: Productivity

Alternative category: Tools

## Tags / positioning language

- markdown editor
- markdown reader
- local-first writing
- Android writing app
- document editor
- plain text workflow

## Store graphics

Draft store graphics are available in:

```text
store-assets/google-play/
```

Generated assets:

1. `feature-graphic-1024x500.png` — Google Play feature graphic.
2. `app-icon-512.png` — 512×512 Play icon candidate.
3. `phone-01-local-markdown-1080x1920.png` — local markdown screenshot candidate.
4. `phone-02-focused-editor-1080x1920.png` — focused editor screenshot candidate.
5. `phone-03-file-tree-1080x1920.png` — file tree/workspace screenshot candidate.
6. `phone-04-privacy-1080x1920.png` — local-first/privacy screenshot candidate.
7. `social-preview-1200x630.png` — website/social preview image.

The draft panels use designed UI compositions. Before final public submission, supplement them with real device screenshots from a release-equivalent build if Play review or launch marketing needs exact in-app capture.

Recommended screenshot order:

1. Local markdown / files live locally.
2. Focused editor / clear writing structure.
3. Files and editor / workspace navigation.
4. Privacy posture / writing stays yours.

## Graphic asset notes

- Use the existing Kern mark: charcoal circle, warm background, serif K.
- Match website/app palette:
  - Canvas: `#FCFBFA`
  - Surface: `#F4F3F0`
  - Ink: `#1A1A18`
  - Muted: `#7C7A75`
  - Accent: `#2E5BFF`
- Avoid neon gradients, busy device mockups, and generic AI-style illustrations.

## Website / privacy URLs

Temporary local website source:

```text
website/index.html
website/privacy.html
website/contact.html
```

Before Play submission, host these pages publicly and use the hosted privacy URL in Play Console.

## Pre-submit blockers

1. Host the website and privacy page publicly.
2. Confirm whether Firebase Analytics remains enabled in the production build.
3. Complete Data Safety answers using the final production dependency set.
4. Generate Play screenshots from a release-equivalent build.
5. Ensure hidden GitHub PR refs are purged or avoid making the current GitHub repo public until resolved.
