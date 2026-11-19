# ADR-0019: Merge on green CI, without agent review or human approval gates

- Status: Accepted
- Date: 2026-11-19
- Deciders: Javi
- Related requirements: NFR-11 (supersedes ADR-0018; amends the agent-review part of ADR-0013)

## Context

ADR-0018 added a Claude review gate (required `review` status) and a `human-approved` label that
was mandatory for schema changes. In practice the gate blocked routine PRs (cancelled runs, branch
updates, labels that did not stick) and cost owner time that a solo project with ~10 h/week does not
have. The owner decided that passing tests are the merge criterion.

## Decision

- Required checks on `main`: `web`, `core`, `web-smoke`, `core-smoke`. They have no path filters on
  pull requests, so every PR reports all four.
- `auto-merge.yml` enables auto-merge (squash) on every non-draft PR from this repository; the PR
  merges as soon as the required checks pass. No approvals are required.
- `strict` is off: a PR does not have to be rebased on the latest `main` before merging. Two PRs
  that pass separately can still conflict semantically (e.g. two `V4__` migrations); the next PR's
  CI, which runs on the merge with the latest `main`, catches it.
- The Claude review workflow, the human-approval workflow and `.github/claude/config.yml` are
  removed. Schema migrations merge like any other change; their safety comes from Flyway
  migration tests in `core` (Testcontainers) and the Flyway rule that applied migrations are never
  edited.
- The owner can still review or block any PR by converting it to draft or disabling auto-merge.

## Consequences

- Faster flow; test quality is now the only automated gate, so specs keep requiring tests per task.
- No automated security/quality review on PRs (NFR-11's agent review is dropped by owner decision;
  the PRD should be updated to match). It can be reinstated later as a non-blocking comment-only job.
- Merges done by the workflow token do not trigger `push` workflows on `main`; every PR's CI
  already tests its merge commit with `main`, which is the check that matters.
