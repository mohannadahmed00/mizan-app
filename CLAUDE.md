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

### Starting a spec

Every `/speckit-specify` run **starts by cutting a branch**, without being asked:

```bash
git fetch origin
git switch -c spec/<NNN>-<slug> origin/develop-v1
```

Branch from `origin/develop-v1`, never from whatever is checked out — otherwise a spec written
mid-task inherits an unrelated branch. Take `<NNN>-<slug>` from the Spec Kit feature name and add
the `spec/` prefix.

The spec directory stays unprefixed (`specs/001-domain-foundation/`). Spec Kit resolves the active
feature from `SPECIFY_FEATURE` and `.specify/feature.json`, not from the git branch, so the two may
differ safely.

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

## Design

The product design lives in Claude Design:
`https://claude.ai/design/p/cbccc4f5-eeaf-401a-b975-5a4484a98fdb` (file `Mizan.dc.html`).

**Design tokens** — background `#EFECE5`, primary green `#0B5D42`, hover `#14805C`, ink `#14211C`,
muted `#5C6E66`. Latin type Plus Jakarta Sans; Arabic type IBM Plex Sans Arabic; labels IBM Plex
Mono. Arabic rows use line-height 1.75, headings 1.5.

**Shell is English LTR; task content is Arabic.** Each Arabic string carries its own `dir="rtl"` in
a dedicated Arabic face, so mixed Arabic/Latin rows never reflow the surrounding layout. This is a
product decision, permitted explicitly by the constitution since v1.1.1.

**Navigation is three tabs, not four.** Leaderboard lives inside Progress. The design's own
rationale: a permanent leaderboard tab "puts comparison at the same weight as worship."

**Today is a stepped flow** — one prayer block at a time, not a single 40-row list.

### Audit any design change against these

Three principles bite hardest at the visual layer, which is where they are easiest to violate by
accident:

- **Principle IX — no shame.** No red "missed" states, no ✗ marks, no empty-ring-as-failure, no
  streak-loss imagery, no comparative or negative framing. Progress shows what was *completed*.
  "Incomplete = red" is a near-universal design reflex and is forbidden here.
- **Principle VI — no user authoring.** No add/edit/delete/reorder affordance on tasks anywhere. No
  FAB on a task list, no swipe-to-delete, no drag handles, no task customisation in settings.
- **Principle VIII — vertical slices.** The design spans the whole product, including Leaderboard
  (Phase 8) and Auth (Phase 7). A screen existing in the design is not permission to build it in
  the current increment.

The design as audited on 2026-08-08 passes IX and VI: zero red/negative colour values anywhere, no
authoring affordances, and the streak-reset copy leads protective ("Your 38-day record still
stands… One task today puts you back on").

## Things that are out of scope by construction

- Users creating, editing, deleting, reordering, repricing, or rescheduling tasks (Principle VI).
  The catalogue is a versioned seed, loaded idempotently.
- Penalties, negative scores, failure states, or guilt-inducing copy and imagery (Principle IX).
- Reading the system clock anywhere outside the single injected time provider (Principle VII). The
  day is local midnight to local midnight; the week is Saturday to Friday; Hijri is a label only.

## Roadmap

`docs/PLAN.md` holds the phase plan (Phases 1–10). MVP is Phases 1–4.
