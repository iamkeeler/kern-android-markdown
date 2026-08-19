---
name: kern-readme-maintenance
description: Update the Kern README accurately while preserving its established structure, voice, links, commands, screenshots, and release documentation. Use when a product feature, setup step, supported capability, distribution flow, repository workflow, or public project information changes.
---

# Kern README Maintenance

Keep the README an accurate public entry point without turning it into a changelog.

## Workflow

1. Read `README.md` fully and identify the existing section that owns the changed information.
2. Verify claims against implementation, current workflows, and the supporting documents in `docs/` or `contributor-guides/`.
3. Update the smallest relevant README section. Preserve heading hierarchy, writing style, link style, command conventions, and screenshot naming.
4. Add a section only when no existing section can own the information; place it where the current narrative naturally expects it.
5. Do not announce internal refactors, transient debugging details, secrets, local paths, or unverified roadmap work.

## Content rules

- Keep setup and verification commands executable and aligned with Gradle/workflow reality.
- Update release/distribution claims together with the related GitHub Actions or documentation.
- Use stable product language and explain user-visible capability rather than implementation details.
- Verify every added relative link and asset path.

## Handoff

State which README section changed, the source that verified it, and whether related docs or screenshots also need a follow-up.
