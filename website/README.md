# Kern Landing Website

A focused, local-first static landing website for Kern — a Markdown reader and writing workspace for Android.

## Features

- **Interactive Markdown Workspace**: Interactive live editor showcase with document switching (`draft.md`, `research.md`, `outline.md`) and real-time Markdown rendering directly in the hero device frame.
- **Theme Support**: Seamless dark/light theme switching with automatic `prefers-color-scheme` OS detection and local storage persistence.
- **Brand Alignment**: Custom design tokens mirroring the app design language from `design.md` (warm `#FCFBFA` canvas, dark `#121212` canvas, charcoal text, utility blue accents).
- **SEO & Social Sharing**: Complete Open Graph, Twitter Cards, canonical links, and structured JSON-LD schema.
- **Accessibility & Responsive Navigation**: Mobile drawer menu, high contrast themes, and skip-to-content keyboard accessibility.

## File Inventory

- `index.html` — Main landing page featuring interactive workspace showcase
- `privacy.html` — Privacy policy and data posture statement
- `contact.html` — Contact info, project links, and security reporting
- `styles.css` — Central visual system and theme variable declarations
- `favicon.svg` — Signature Kern "K" vector favicon

## Local Preview

Start a local HTTP server:

```bash
python3 -m http.server 8087 --directory website
```

Open in your browser:

```text
http://localhost:8087/index.html
```

## Continuous Deployment

This site is deployed automatically to `attach.design/kern/` via GitHub Actions FTP deployment (`.github/workflows/deploy-website-ftp.yml`).
