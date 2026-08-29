# Quickstart: Leaderboards & Honor Board

**Feature**: `specs/008-leaderboard-honor-board`

How to provision, run and validate this increment. Details live in the contracts and the data model
rather than being repeated here.

---

## 1. Prerequisites

Everything spec 007 required, plus:

- The Supabase project resumed and reachable. Spec 007's `RLS OK` passing is the baseline this
  increment must not regress.
- `supabase` CLI logged in and linked, or an equivalent way to run SQL against the project.
- At least two devices or emulators for the multi-participant criteria, and three for the regional
  ones.

Note the same wall spec 007 hit: sign-in is passwordless OTP, so every signed-in criterion needs a
code reaching an inbox. Issue #15 tracks that, and this increment inherits it — SC-002, SC-004 and
everything involving a second participant are gated the same way.

---

## 2. Provision

```powershell
# Schema — must be byte-identical to the contract, same gate as 007
supabase db execute --file specs\008-leaderboard-honor-board\contracts\remote-schema-008.sql

# Regions. Administrator-defined; exactly one must be the fallback (FR-015, FR-017)
supabase db execute --file supabase\seed\regions.sql
```

Then schedule `public.recompute_open_periods()` — cadence is an operator decision. The Honor Board
threshold is configuration, not a constant in code (FR-028). There is no settlement window: periods
freeze at their boundary (FR-025).

Verify the migration matches the contract, as the merge gate requires:

```powershell
git diff --no-index supabase\migrations\0002_leaderboard_honor_board.sql `
                    specs\008-leaderboard-honor-board\contracts\remote-schema-008.sql
```

---

## 3. Run

```powershell
.\gradlew :app:installDebug
```

Sign in, open Progress. With no opt-in, the only leaderboard-related thing on screen is the
invitation. Signed out, there is nothing at all.

---

## 4. Validate each success criterion

### SC-001 — a person who never opts in never meets a ranking

*Automated*: `NoOptInGateTest`.

*By hand*: sign in, never opt in, then walk Today, Week, Streak, History, Insights and Progress end
to end. Nothing beyond the single invitation inside Progress may mention ranking, and no other
participant's name may appear anywhere.

### SC-002 — leaving is immediate forward, and changes nothing backward

*Automated*: `ParticipationWithdrawalTest`, plus `rls-verification-008.sql` §6.

*By hand*: two participants in one region. A opts in, both confirm A's row is visible to B. Close a
period (or seed one closed). A opts out. Then assert **all** of:

- A is gone from the open period's ranking and Honor Board, within one refresh.
- A is absent from the next period to open.
- The closed period's ranking still contains A, unchanged.
- The closed period's Honor Board still contains A.

The last two are the half most likely to regress, and the half the user explicitly chose.

### SC-003 — opting out does not touch the record

*Automated*: `ParticipationWithdrawalTest`.

*By hand*: note earned/available for several days before opting out; confirm identical after,
including streak and insights.

### SC-004 — every position matches an independent recomputation

*Automated*: `RankingAggregationTest` against the fake.

*By hand*: with a known set of completions, compute each period's totals by hand in the region's
timezone and compare. Points must equal the sum of `points_awarded` over non-reversed completions —
never a recomputation from the catalogue (Principle III).

### SC-005 — the leaderboard day is the participant's own day

**The criterion this increment exists for.** Three devices in regions spanning at least 12 hours,
on a date the catalogue schedules day-specific tasks (a Friday).

For each participant: the date shown on Today must be the same weekday as the date the leaderboard
period covers. A participant on Thursday must never be ranked inside a leaderboard Friday.

*Automated*: `RegionalPeriodBoundaryTest` covers the derivation; the three-device walk covers the
integration.

### SC-006 — a modified client cannot change any figure

```powershell
supabase db execute --file specs\008-leaderboard-honor-board\contracts\rls-verification-008.sql
```

Expected: `RLS OK 008`. Verified directly against the service, not through the app — a fake only
proves the client asked politely (research R10).

### SC-007 — no cross-region read

Covered by the same script, §2.

### SC-008 — the core loop is unaffected

*Automated*: `LeaderboardDegradationTest`.

*By hand*: with the project paused, confirm recording, Today, Week, Streak, History and Insights
behave identically to a run with it available. The leaderboard panel says standings are unavailable;
nothing else changes.

### SC-009 — own row without scrolling

*Automated*: `OwnRankLookupTest` against a seeded 10 000-participant region.

### SC-010 — a closed period is stable

*Automated*: `ClosedPeriodImmutabilityTest` — **the Principle III test this increment owes.** Close a
period, then change catalogue points, reverse a completion, add a late completion for that date, and
opt a member out. Assert the closed period's standings and Honor Board membership are byte-identical
throughout.

Periods freeze at their boundary with no settlement window (FR-025), so "a late completion" here
means one arriving even seconds after the boundary — the test should use exactly that, since it is
the realistic case, not a contrived one.

### SC-015 — a mid-period joiner is scored over the whole period

*Automated*: `MidPeriodOptInTest`.

*By hand*: record several days without opting in, then opt in on the last day of the period. The
published total must cover every day of the period, not only the days after opting in (FR-021a), and
the opt-in copy must have said so beforehand (FR-002b).

### SC-016 — a late sync misses the leaderboard but not the record

*Automated*: `LateSyncAfterFreezeTest`.

*By hand*: **the tradeoff worth seeing with your own eyes.** Record a full day offline, let the
period boundary pass, then reconnect. Assert both halves:

- The closed period's standings do not change — the day scores nothing there.
- Today, Week, Streak, History and Insights count the day in full (FR-025a).

If this feels wrong in practice, that is the signal to revisit the no-grace-period decision; the
spec's Assumptions section flags it as the one place the leaderboard disadvantages offline users.

### SC-017 — duplicate display names

*Automated*: `DuplicateDisplayNameTest`.

*By hand*: two participants in one region with the same display name. Both listed, neither name
altered or suffixed, and each can find their own row (FR-007a).

### SC-011 — the Honor Board is consistency, not points

*Automated*: `HonorBoardQualificationTest`. Two participants with equal `days_engaged` and very
different points both qualify or both do not.

### SC-012 — nothing leaks about non-qualifiers

*Automated*: contract test on the Honor Board response shape, plus `rls-verification-008.sql` §7.

*By hand*: inspect **everything the client can retrieve**, not only what it renders. No threshold,
no distance, no non-qualifier count, no per-person days figure.

### SC-013 — no leaderboard string or colour blames anyone

*By hand*: read every string this increment introduces, including both opt-in and leave
confirmations, against the `CLAUDE.md` Principle IX list. Inspect every colour on the ranking and
Honor Board surfaces.

Specifically confirm: no red; a last-place row renders identically to every other row; no copy
frames another participant as ahead of the viewer; no gap, deficit or shortfall figure anywhere.

Record the audit as a comment block at the top of `LeaderboardCopyTest`, as spec 007 did for sync
copy.

### SC-014 — a first ranking within 3 seconds

*By hand*: cold-open Progress on a working connection and time the first ranking. Then pause the
project and confirm the unavailable state resolves within 10 seconds rather than hanging.

---

## 5. Before opening the PR

- All four suites green.
- `RLS OK 008` recorded in the PR description.
- Migration byte-identical to the contract.
- Schema 4 exported and committed; `1.json`–`3.json` still byte-identical.
- The SC-013 copy and colour audit recorded.
- `ClosedPeriodImmutabilityTest` present and passing — Principle III is owed by any increment
  touching persistence.
- Any criterion left unvalidated recorded as a follow-up issue, the way spec 007's five were, rather
  than silently skipped.
