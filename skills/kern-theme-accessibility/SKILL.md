---
name: kern-theme-accessibility
description: Preserve Kern theme tokens, WCAG contrast, and app-root system-bar behavior. Use when changing theme models, colors, typography, dark/light appearance, system status/navigation bars, or any UI surface whose readability depends on theme values.
---

# Kern Theme Accessibility

Treat built-in theme colors and system UI as user-facing accessibility contracts.

## Required reading

Read `AGENTS.md`, `contributor-guides/design-guide.md`, and the theme model/tests before changing declarations or theme application.

## Guardrails

- Apply active theme system-bar colors and icon appearance at the app root, never as a screen-local side effect.
- Require 4.5:1 contrast for text-to-background and text-to-surface; require 3:1 for interactive indicators.
- Preserve light surfaces with dark system-bar icons and dark surfaces with light icons.
- Use semantic theme tokens rather than raw hex values in screens.
- Add or update contrast and system-bar regression coverage whenever declarations change.

## Verification

Run the theme accessibility tests plus `$kern-android-verification`. Visually verify both light and dark themes on an emulator when system bars, surfaces, typography, or interactive indicators change.
