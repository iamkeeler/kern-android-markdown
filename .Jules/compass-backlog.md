# Compass Usability Backlog & Audit Log

## Applied Fixes (2026-07-24)

- [x] **Focus Visible Rings (WCAG 2.4.7 / 1.4.11 AA)**: Added global `:focus-visible` outline using `var(--accent)` across all interactive controls in `styles.css`.
- [x] **Expanded Touch Targets (WCAG 2.5.8 AA)**: Increased padding and set `min-height: 44px` on `.file-btn` and `.tab-btn` elements.
- [x] **Dynamic ARIA Announcements (WCAG 4.1.2)**: Configured dynamic theme `aria-label` updates (`"Switch to light theme"` / `"Switch to dark theme"`) and tablist/tab `aria-selected` controls.
- [x] **Dark Mode Surface Contrast**: Adjusted dark theme `--muted` color token to `#9E9C96` for enhanced surface readability.

## Deferred / Routed Items

- [ ] **Visual Hierarchy Pass (`→ Lens`)**: Evaluate display title font scaling and line heights across breakpoint collapses (< 640px).
