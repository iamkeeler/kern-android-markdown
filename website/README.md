# Kern website

A simple static landing site for Kern.

## Pages

- `index.html` — landing page
- `privacy.html` — privacy note for app/website launch
- `contact.html` — contact and project links
- `styles.css` — shared visual system

## Visual direction

The site mirrors the app design language from `design.md`:

- warm off-white canvas (`#FCFBFA`)
- muted surface color (`#F4F3F0`)
- charcoal text (`#1A1A18`)
- muted steel metadata (`#7C7A75`)
- utility blue CTAs (`#2E5BFF`)
- typography-first hierarchy
- restrained lines and broad spacing

## Local preview

```bash
python3 -m http.server 8087 --directory website
```

Then open:

```text
http://localhost:8087/index.html
```

## Launch notes

Before public launch:

1. Replace the Play Store placeholder link if the final package/listing URL changes.
2. Replace the privacy note with the final policy after Play Data Safety decisions are complete.
3. Add final screenshots or app imagery if desired.
4. Decide hosting target: GitHub Pages, Netlify, Cloudflare Pages, or another static host.
