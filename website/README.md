# Kern Landing Website

A focused static website for Kern, an open-source Markdown editor for Android.

## Features

- **Product page**: A single Markdown editor screenshot in a device frame, with links to GitHub and Google Play.
- **Theme support**: Light and dark themes with saved user preference.
- **SEO and sharing**: Open Graph, Twitter Cards, canonical links, and SoftwareApplication JSON-LD.
- **Accessibility**: Skip-to-content link and responsive navigation.

## File Inventory

- `index.html` — Main landing page
- `privacy.html` — Privacy policy and data posture statement
- `contact.html` — Contact info, project links, and security reporting
- `styles.css` — Central visual system and theme variable declarations
- `favicon.svg` — Signature Kern "K" vector favicon
- `logo.svg` — Stable public URL for Markdown image examples

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
