# Mizan — Working Agreements

Mizan is an offline-first Android app for recording a fixed, administrator-defined set of Islamic
daily practices. Built solo, incrementally.

## Read this first

The project constitution at `.specify/memory/constitution.md` is authoritative. It defines nine
non-negotiable principles — most importantly **test-first development** (Principle I) and
**immutable historical records** (Principle III), neither of which admits exceptions. Read it before
planning or implementing anything.

This file records the concrete, changeable details the constitution deliberately leaves out.

## Branches

| Branch | Role | Accepts |
|---|---|---|
| `main` | Release branch. Tagged releases only. | PRs from `develop-v1` |
| `develop-v1` | Integration branch. | PRs from work branches |
| `<type>/<slug>` | Work branches, one per increment. | Direct commits |

- `main` and `develop-v1` are **protected**: no direct commits, no force-push. Everything lands via
  a pull request.
- Work branches are short-lived — one increment, then merged and deleted. Name them after the spec
  they implement, e.g. `feat/day-plan-materialization`, `fix/week-boundary-friday-edge`.
- Release PRs go `develop-v1` → `main`, one per shipped milestone. The first is the MVP
  (roadmap Phases 1–4); after that, each release is its own PR.

Solo PRs are not ceremony here — they are where CI runs and where the diff gets read cold. Do not
skip them by committing straight to `develop-v1`.

### Abandoned branches — treat as non-existent

As of 2026-08-08 the remote still carries branches from before this workflow was adopted:

`develop`, `plan`, `imp/phase-01`, `chore/project-setup`, `chore/setup-data-module`,
`chore/setup-koin-di`, `feat/implement-hijri-date-persistence`, `feat/sync-hijri-dates-workmanager`

**They are kept for reference only and are deliberately not deleted.** Do not branch from them,
merge them, cherry-pick from them, cite them as prior art, or propose deleting them. `main` and
`develop-v1` are the only branches that exist for planning purposes; work starts from
`develop-v1`.

Note that `develop` is *not* the integration branch — `develop-v1` is. `develop` has no protection
and is abandoned.

## Merge gates

Before merging into `develop-v1`:

- The Constitution Check in the plan passes, and names each principle the increment touches.
- Tests are green.
- Test tasks preceded their implementation tasks in **the PR's** commit history (Principle I). A PR
  whose first commit is production code has already violated this. Merges are squash-only, so
  `develop-v1` keeps one commit per PR — the ordering evidence exists only on the pull request, and
  reviewing it before merge is the only chance to check it.
- If the increment touched persistence or the task catalogue, it includes the
  historical-immutability test required by Principle III.

Additionally, before merging `develop-v1` → `main`:

- Every Room migration in the release is non-destructive, and the schema is exported and committed.
- The app still works on a fresh install in airplane mode (Principle IV).

## Stack

- Kotlin, Jetpack Compose. Clean Architecture + MVVM, one immutable UI state per screen as
  `StateFlow`. No mutable state exposed from a ViewModel.
- Modules `:domain`, `:data`, `:app`. Direction is `:app` → `:data` → `:domain`. **`:domain` depends
  on nothing** — no Android, Room, Retrofit, Compose, or Koin annotations in it.
- **Koin only** for DI. Hilt and KSP-based DI must not be introduced; two DI frameworks may never
  coexist.
- Room with exported schemas and migrations. No destructive migration in any build a user has
  installed.
- Retrofit + coroutines for the existing Hijri date sync only. A new network surface needs explicit
  justification in the plan.
- Task content is Arabic and is **data, not UI strings**. Layouts must be RTL-correct.

## Things that are out of scope by construction

- Users creating, editing, deleting, reordering, repricing, or rescheduling tasks (Principle VI).
  The catalogue is a versioned seed, loaded idempotently.
- Penalties, negative scores, failure states, or guilt-inducing copy and imagery (Principle IX).
- Reading the system clock anywhere outside the single injected time provider (Principle VII). The
  day is local midnight to local midnight; the week is Saturday to Friday; Hijri is a label only.

## Roadmap

`docs/PLAN.md` holds the phase plan (Phases 1–10). MVP is Phases 1–4.
