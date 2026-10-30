# AGENTS.md

Instructions for coding agents working on Mad Transit. Humans start with [README.md](README.md). Each app has its own file with the details: [api/AGENTS.md](api/AGENTS.md) and [web/AGENTS.md](web/AGENTS.md).

## Repo map

- `api/`: Spring Boot API in Kotlin that wraps the EMT MobilityLabs API. `api/client/` is the TypeScript client generated from its OpenAPI contract.
- `web/`: React web app that calls the API only through `@jorgetroya80/mad-transit-client`.
- `docs/specs/` and `docs/plans/`: one SPEC and one PLAN per module. Start with [docs/specs/CAPABILITY-MAP.md](docs/specs/CAPABILITY-MAP.md).
- `CONSTRAINTS.md`: the quality bar, with the command behind each rule.
- `render.yaml`: Render Blueprint. It deploys only from `main`.

## Before you change code

1. Read [CONSTRAINTS.md](CONSTRAINTS.md). Never weaken it to make a change pass: no lowered thresholds, no new suppression comments, no skipped or deleted tests.
2. New behavior follows spec, plan and tasks, with human approval between phases. Do not write code for a module or feature until its plan is approved.
3. Fix bugs test-first: a test that fails, then the fix.

## Verify

```bash
pnpm check:fast   # types of what changed, web lint, secrets, floor guard (seconds)
pnpm check:task   # + web and api tests with coverage, changed-line coverage, layers (under 90 s)
pnpm check:full   # + Semgrep and pnpm audit (needs Docker)
```

Run `pnpm check:task` before you report a task as done, and report its real result.

## Commits and pull requests

- Conventional Commits, checked by commitlint. Types: feat, fix, perf, refactor, test, docs, build, ci, chore, revert. Scopes: api, web, ci, deps, docs, repo.
- Body lines are at most 100 characters.
- Stage files explicitly (`git add <path>`), never `git add -A`.
- One branch and one PR per module or fix, with one commit per plan task. Never commit to `main`.
- A feat, fix or perf PR touches only `api/` or only `web/` (the release-scope check). Put the other part's changes in an earlier build or chore PR.
- The repo squash-merges, so the PR body becomes the commit body and release-please parses it. A line it cannot parse (code, for example) makes it skip the commit without warning. Every PR body follows [.github/pull_request_template.md](.github/pull_request_template.md): `## What` lists the changes as bullets, and `## Why` gives the reason in prose. No code in either.
- Only if the body needs code or a special footer, end it with an override, which replaces the whole commit message for release-please. Keep it in sync with the PR title, and put any `BREAKING CHANGE:` footer inside it, or the release loses it:

  ```
  BEGIN_COMMIT_OVERRIDE
  <the PR title>

  BREAKING CHANGE: <only for a breaking change>
  END_COMMIT_OVERRIDE
  ```

- Ask before you push, open or merge a PR, or touch the Render deploy.

## Documents

- Specs, plans and the capability map are in Spanish. Code, comments, commit messages, PR bodies and READMEs are in English.
- Every SPEC and PLAN starts under its title with Created, Status (with the date of its last change) and the link to its Plan or Spec. See the capability map for the states.
- Record decisions and results in the plan's changelog as you finish each task.

## Gotchas

- pnpm installs only package versions published at least 10 days ago (`minimumReleaseAge`), except our own client.
- The web needs a GitHub token with `read:packages` to install `@jorgetroya80/mad-transit-client`; see [web/README.md](web/README.md).
- Geolocation needs HTTPS, so a phone cannot test it against `vite --host` on the LAN. Test on Render after the merge.
- A Render Blueprint sync never deletes rules or environment variables removed from `render.yaml`.
- Ask before you add any dependency.
