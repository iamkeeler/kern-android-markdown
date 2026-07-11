# Play Store listing draft — Kern

Package: `com.attachdesign.kern`
App name: `Kern`
Status: draft for internal testing / first public listing

## Short description

A calm local-first markdown reader and writing workspace for Android.

## Full description

Kern is a focused markdown workspace for Android, built for people who want local documents, readable typography, and a quiet writing surface.

Open project folders, move through markdown files, and write in a clean editor that keeps the document at the center. Kern is designed around warm surfaces, clear structure, and low-distraction editing instead of heavy note-taking systems or cloud-first workflows.

Current focus:

- local markdown reading and editing
- project folder navigation
- document-first workspace layout
- responsive editing performance
- calm typography and minimal interface chrome

Kern is in early release. The app is best for testers who are comfortable with markdown files and local Android file workflows.

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

## Screenshot plan

Recommended screenshots:

1. Home / project file tree with markdown files visible.
2. Editor showing a focused markdown document.
3. Wider-screen split layout with file tree and document canvas.
4. Formatting or markdown structure state, if visually distinct.
5. Privacy/local-first reassurance screen or website-style brand panel.

Recommended screenshot copy overlays, if used:

- Local markdown, calm workspace
- Write without cloud lock-in
- Files and editor, side by side
- Built for readable long-form writing

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
