# ADR-0018: Automated PR review and conditional auto-approval with Claude cloud agents

- Status: Superseded by ADR-0019
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

**Enforcement is a required status check, not a GitHub approval.** A solo author cannot approve
their own PR, so `main` requires **0 approving reviews** and instead requires a `review` status
check. Because this workflow is `workflow_run`-triggered, its own check-run attaches to the default
branch rather than the PR head, so the gate explicitly **posts a `review` commit status onto the PR
head SHA** — that status is what branch protection matches. The gate encodes the verdict as it:
- policy holds → enable auto-merge and exit success → `review` **green** → PR merges once all
  required checks (ADR-0013) are green;
- held or schema/migration diff → comment the reason and exit failure → `review` **red** → PR
  blocked until a human approves it (below);
- kill-switch off (`enabled: false`) → advisory comment, no auto-merge, exit success → `review`
  green and non-blocking, so a human merges manually.

It never blocks silently: every outcome posts a comment with the reason.

**Human approval without bypass.** A held PR is approved by a repo admin adding the
`human_approval_label` (`human-approved`). `human-approval.yml` (`pull_request_target`, runs from
the default branch, never checks out PR code) verifies the sender is an admin, posts a green
`review` status on the current head SHA and enables auto-merge. Any new push removes the label, so
approval is per commit (the status is bound to the SHA anyway). The gate never overwrites a
`review` status that human-approval.yml posted on the same head, and it never reads the label
itself, so only an admin-verified labelling can approve. Branch
protection bypass is therefore reserved for emergencies, never the normal path.

**Required checks must always report.** `web-smoke` and `core-smoke` are required, so `CI smoke`
has no `paths` filter on `pull_request`: a filtered-out workflow never reports, leaves the checks
"Expected" forever and also never triggers this review (it is `workflow_run` on CI). Workflows
that are not required checks (`CI core`, `CI web`) may keep path filters.

**Trust boundaries.** The agent job checks out untrusted PR code and holds no `statuses` or
`contents: write` token. The gate runs in a separate job on a fresh runner, reads the verdict only
from the uploaded artifact and reads the policy (`config.yml`) from the default branch, so a PR
cannot relax the rules that judge it; protected paths are matched as globs against the PR's file
list from the API.

**Merging runs in its own job.** Enabling auto-merge (or merging an already-green PR) needs
`contents: write`; that permission is granted only to a separate `automerge` job that runs after a
passing gate, pinned to the reviewed head SHA (`--match-head-commit`). The job that runs the review
agent keeps `contents: read`.

The gate holds unless: `quality_score ≥ min_quality_score`, `blocking_findings` is empty, the diff
touches no `block_on_paths`, and no `block_on_labels` label is present. Criteria such as i18n,
provider-layer and no-hard-coded-limits are enforced through the agent's blocking findings (and the
required CI/Lighthouse/smoke checks), not a separate boolean list.

**Configuration** lives in `.github/claude/config.yml` (policy as code): `enabled`,
`min_quality_score`, `block_on_paths[]`, `block_on_labels[]`, `human_approval_label`. It is changed
by editing that file (e.g. in conversation with the coding agent) — turning auto-approval off is a
one-line, reversible PR, and `block_on_labels` (`no-auto-approve`) is a per-PR override.

**Prerequisites** (owner, one-time): install the Claude GitHub app on the repo; add the
`ANTHROPIC_API_KEY` repository secret; enable repo "Allow auto-merge"; set branch protection on
`main` to **0 required approvals** and require the `web-smoke`, `core-smoke` and `review` checks.
The review workflow no-ops safely if the secret is absent (then every PR is admin-merged until it
is set).

## Consequences

- Trivial, high-quality, non-schema PRs merge unattended; risky ones never do.
- Auto-merge is fenced: post-CI only, score + criteria + no-schema + enabled, with a kill-switch.
- The review runs on Anthropic API usage (budgeted under "Agent PR review" in the PRD, ~CHF 30/mo).
- `pull_request_target` is avoided; the private repo's PRs come from owner/agent branches, not
  forks, and review runs in the base context via `workflow_run`.
