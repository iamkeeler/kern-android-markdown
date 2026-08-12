# Web standards

The website is a small static site in `website/` and is deployed as-is. Keep it simple, fast, and usable without JavaScript.

## Structure and content

- Use semantic HTML landmarks, one clear page title, and a logical heading hierarchy.
- Keep page copy concise and aligned with the product requirements and privacy policy.
- Reuse the existing CSS tokens and layout patterns before adding new styles.
- Keep public URLs, canonical links, Open Graph metadata, sitemap entries, and `robots.txt` consistent.

## Accessibility and performance

- Every meaningful image needs useful alternative text; decorative images should use empty alt text.
- Preserve visible keyboard focus, readable contrast, and touch targets large enough for mobile use.
- Use responsive layouts that work on narrow screens first. Avoid layout shifts and unnecessary dependencies.
- Do not add tracking or third-party scripts without documenting the data and privacy impact.

## Verification

When changing the site, verify the affected pages locally, check links and image paths, and confirm that the deploy workflow still finds the required files. Website changes deploy through `.github/workflows/deploy-website-ftp.yml` on a website tag or manual dispatch.
