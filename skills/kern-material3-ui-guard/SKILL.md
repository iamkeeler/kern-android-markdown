---
name: kern-material3-ui-guard
description: Preserve Kern's Material 3, Compose, inset, and interaction conventions for user-interface changes. Use when modifying composables, navigation chrome, dialogs, menus, buttons, layouts, focus behavior, keyboard behavior, or accessibility semantics.
---

# Kern Material 3 UI Guard

Keep the UI native, accessible, and consistent with the project design system.

## Before implementation

Read `contributor-guides/design-guide.md`, `contributor-guides/coding-standards.md`, and `AGENTS.md`. Read an applicable interaction contract before changing editor behavior.

## Guardrails

- Prefer Material 3 controls (`TopAppBar`, `DropdownMenu`, `IconButton`, dialogs) and stable Compose APIs.
- Use theme tokens for color, type, shape, spacing, and elevation; do not introduce screen-local visual constants.
- Give each window inset one owner. In particular, do not apply duplicate IME padding to a child overlay already positioned by its parent.
- Preserve platform focus, selection, gestures, keyboard, and accessibility behavior unless a documented product requirement requires customization.
- Supply meaningful semantics/content descriptions for icon-only controls and test important visible states.

## Verification

Add or update Compose/instrumentation coverage for changed interaction or layout. Visually inspect on an emulator when geometry, overlays, insets, animation, or accessibility behavior changes. Use `$kern-android-verification` for the required build and test matrix.
