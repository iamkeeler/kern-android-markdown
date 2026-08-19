---
name: kern-release-learning-loop
description: Review a successful stable Kern release tag and convert durable lessons into proposed contracts, skills, guides, tests, or README updates. Use only after a `release` or `v*` tag has been pushed and its release workflow has completed successfully, or when the user explicitly asks for a retrospective on an accepted release. Do not use for `test` tags or in-progress development builds.
---

# Kern Release Learning Loop

Turn an accepted release into a concise, evidence-based proposal for durable project improvement. This skill proposes shared-guidance changes; it never silently changes them.

## Establish the release boundary

1. Confirm the tag is `release` or matches `v*` and has been pushed.
2. Confirm the relevant release workflow completed successfully.
3. Identify the preceding stable release tag and limit review to changes between those two releases.
4. Do not use test-tag builds as evidence of accepted behavior.

## Gather evidence

Review only evidence that belongs to the completed release:

- Release workflow outcome, artifacts, version, lint, and test results.
- Merged changes and resolved regressions since the prior stable tag.
- User feedback, rejected changes, reversions, or recurring failures associated with that release interval.
- Existing contracts, contributor guides, project-local skills, and README claims affected by the shipped work.

Distinguish evidence from inference. Mark an inference as such.

## Classify each lesson

| Durable lesson | Proposed destination |
| --- | --- |
| User-visible invariant that must not regress | Interaction contract plus regression test |
| Repeated agent workflow or fragile verification sequence | Project-local skill, optionally supported by a deterministic script or CI workflow |
| Project-wide implementation or design rule | `AGENTS.md` or `contributor-guides/` |
| Public, shipped capability or workflow | README and supporting release documentation |
| One-off defect with no reusable rule | Test/issue/PR record only; do not add policy |

Avoid adding a rule merely because a single change was difficult. Promote it only when it protects a durable product contract, recurring risk, or repeatable workflow.

## Produce the learning report

For each proposal, include:

1. Evidence and release/tag reference.
2. The lesson and why it is durable.
3. Proposed destination and exact change summary.
4. Test, CI, or ownership implication.
5. Whether approval is needed before making the shared-guidance change.

End with an explicit decision set: adopt, defer, or reject each proposal. Ask for approval before editing `AGENTS.md`, contributor guides, contracts, README, skills, CI workflows, or release documentation.

## Guardrails

- Do not push tags, distribute builds, change release settings, or publish documentation as part of the review.
- Do not treat a successful build alone as evidence that a behavior is correct.
- Keep the report short and actionable; link to source evidence instead of copying logs.
- Use `$kern-readme-maintenance`, `$kern-android-verification`, or another matching project-local skill only after an approved proposal requires that work.
