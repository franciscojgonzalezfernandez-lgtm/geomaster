# ADR-0018: Automated PR review and conditional auto-approval with Claude cloud agents

- Status: Accepted
- Date: 2026-10-09
- Deciders: Javi
- Related requirements: NFR-05, NFR-11, FR-EXT-03 (extends ADR-0013)

## Context

A solo owner (~10 h/week) merges agent-written PRs. We want quality and security review to run
automatically **on GitHub, after CI is green**, in a fresh (clean) context per PR, and routine
high-quality changes to merge without a human in the loop — while anything risky still stops for
a human. This formalizes the "Agent PR review workflows" line of ADR-0013.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| Claude cloud agents in GitHub Actions, triggered after CI, with a policy gate for auto-approval | Clean per-PR context; no local runner; policy as code; kill-switch; migrations always held | Needs the Claude GitHub app + `ANTHROPIC_API_KEY` secret; auto-merge power must be fenced |
| Reviewer bots only (no auto-merge) | Simplest, safest | Owner still clicks merge on every trivial PR |
| Fully autonomous merge, no exceptions | Fastest | Unsafe for schema/migration and security-relevant diffs |

## Decision

A review workflow runs **only after the CI workflows conclude successfully** (`workflow_run`
trigger gated on `conclusion == success`), so agents never review a red build. For each PR it
starts a Claude cloud agent with **clean context** (fresh checkout of the PR head, no carry-over)
that:

1. Reads the diff and repo context and reports **correctness bugs** and **vulnerabilities**
   (OWASP Top 10, secrets, injection, authz/tenant-isolation, dependency risk).
2. Verifies the PR checklist criteria: tests/evals pass, strings exist in EN/DE/FR/ES, LLM calls go
   through the provider layer and record cost, no hard-coded plan limits, Lighthouse ≥ 0.9 on
   touched public pages, smoke job green.
3. Emits a machine-readable verdict: `quality_score` (0–100), `blocking_findings[]`, per-criterion
   booleans, and `touches_schema`.

**Auto-approval policy** (evaluated by a gate job, all must hold):
- `quality_score ≥ 90` (threshold in config), and
- zero `blocking_findings`, and all required criteria true, and
- the diff does **not** touch the database schema or a migration
  (`apps/core/src/main/resources/db/migration/**`, or any DDL) — schema/migration PRs **always**
  require a human, and
- auto-approval is **enabled in configuration** and the PR carries no opt-out label.

When all hold, the agent posts an approving review and enables GitHub auto-merge; the PR still
merges only once every required check (ADR-0013) is green. When any fails, the agent posts its
findings as a review and leaves the PR for a human — it never blocks silently.

**Configuration** lives in `.github/claude/config.yml` (policy as code): `enabled`,
`min_quality_score`, `require[]` criteria, `block_on_paths[]`, `block_on_labels[]`. It is changed
by editing that file (e.g. in conversation with the coding agent) — turning auto-approval off is a
one-line, reversible PR, and `block_on_labels` (`no-auto-approve`) is a per-PR override.

**Prerequisites** (owner, one-time): install the Claude GitHub app on the repo and add the
`ANTHROPIC_API_KEY` repository secret; enable branch protection on `main` requiring the CI + smoke
+ Claude-review checks. The review workflow no-ops safely if the secret is absent.

## Consequences

- Trivial, high-quality, non-schema PRs merge unattended; risky ones never do.
- Auto-merge is fenced: post-CI only, score + criteria + no-schema + enabled, with a kill-switch.
- The review runs on Anthropic API usage (budgeted under "Agent PR review" in the PRD, ~CHF 30/mo).
- `pull_request_target` is avoided; the private repo's PRs come from owner/agent branches, not
  forks, and review runs in the base context via `workflow_run`.
