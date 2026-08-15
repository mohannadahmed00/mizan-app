# Phase 0 Research: History & Past-Day Review

Seven questions the plan could not answer from the specification alone. Each was resolved against the
merged code on `develop-v1`, not against `002`/`003`/`004`'s documents.

---

## R1 — How is a past date's figure produced when it has no stored plan?

**Decision.** Build a full `DayPlan` with the existing `buildDayPlan(catalogue, version, date,
origin, newId)` and **do not store it**. The version comes from `versionEffectiveOn(date)`, the origin
is `BACKFILLED`, and `newId` returns a fixed non-identifying string. This lives in one file,
`domain/history/DeriveDayPlan.kt`, and every caller that needs an unplanned date's figures goes
through it.

**Rationale.** FR-020b requires a date to read identically whether it carries a plan or not. There
are two ways to get that: write two code paths and test that they agree, or make them the same path.
`buildDayPlan` is already pure, already takes its version explicitly rather than reading the current
one, and is already what `ensurePlanFor` calls before inserting. Calling it and discarding the result
makes "derived equals stored" a property of construction rather than a promise maintained by hand —
and it means the applicability rule, the point arithmetic, the section ordering and the Hijri label
all come from one place, as they already do for stored plans.

The immutability question was checked rather than assumed. Deriving reads the catalogue at render
time, which Principle III forbids *for a recorded day*. It is permitted here because the derived
figure is stable: `versionEffectiveOn` returns the version with the greatest effective-from date on
or before the date, task versions are immutable once published (Architectural Decision 3), and the
result is therefore the same forever. The moment a date carries a completion it carries a plan too —
`002`'s write path calls `ensurePlanFor` before recording — so no *recorded* day is ever derived.

**Alternatives considered.**

- *Extend `projectAvailablePoints` and use it everywhere.* It returns an `Int`. Week rows need only
  that, but the day detail needs the task list, the per-task points, the occurrence limits and the
  Hijri label. Two projections would then exist, and FR-020b would need a test rather than a
  guarantee. Rejected — but `projectAvailablePoints` stays, unchanged, for `003`'s future dates.
- *Materialise on scroll so nothing is ever derived.* Rejected by clarification Q2.
- *Store a lightweight "week rollup" instead.* A new stored fact for a figure that is already
  derivable, forbidden by Principle VIII and by the spec's own no-new-persistence assumption.

---

## R2 — What does `buildWeekSummary` do with an elapsed date that has no plan?

**Decision.** Widen `projectedAvailable` from "every date after today" to "every date in the week with
no stored plan, that is at or after the record start". The `available` calculation becomes: a stored
plan's own `availablePoints` when one exists, the projection otherwise, and `0` only for
`OUTSIDE_RECORD`. No `DayCellState` changes.

**Rationale.** Today the function reports `available = 0` for an elapsed date with no plan, which was
correct for `003` — `GetWeekSummary` backfilled every such date before calling it, so the case could
not arise. History does not backfill (Q2), so the case is now reachable and `0/0` would be a
fabricated figure of exactly the kind FR-032 forbids. The state machine already handles it: `plan ==
null` on an in-record elapsed date already yields `NOTHING_RECORDED`, which is the right state. Only
the denominator is wrong.

Stored plans keep priority over projection wherever both could apply. That is what stops a catalogue
change from moving a recorded day (Principle III) — the projection is consulted only for dates that
have nothing recorded and no plan.

**Alternatives considered.**

- *A second aggregate for history.* Two implementations of "what a week came to", which Principle VII
  forbids in spirit and `003` forbids by construction. Rejected.
- *Leave `buildWeekSummary` alone and let `GetHistoryPage` patch the denominators afterwards.* Moves
  the rule out of the pure function into an orchestrator, where `003`'s existing tests would not
  cover it. Rejected.

**Regression risk.** `003`'s `BuildWeekSummaryTest` and `GetWeekSummaryBackfillTest` must stay green
unchanged. They exercise weeks where every elapsed date has a plan, so the widened branch is
unreachable for them.

---

## R3 — How is history paged?

**Decision.** A page is **eight whole weeks**, keyed by the Saturday of its newest week, loaded
backwards and clamped at the week containing the record start. Each page is one `plansBetween(start,
end)`, one `liveBetween(start, end)` over the same 56-day span, and one catalogue load per distinct
version needed for projection. **No DAO method is added.**

**Rationale.** Both range reads already exist, both are indexed on the date column, and both already
take arbitrary bounds — `003` happens to call them with a seven-day range. Eight weeks is roughly two
months, comfortably more than one screen, and 56 dates is a small enough read that SC-015's 500 ms
first-screen budget is not in question. Paging by whole weeks rather than by row count keeps the
week rule in `WeekBoundary` and avoids a second notion of where a page boundary falls.

Catalogue loads are the only repeated cost, so they are memoised per version within a page — a page
spanning one catalogue version loads it once, not 56 times.

**Alternatives considered.**

- *Load the whole record at once.* SC-003 forbids it explicitly, and three years is 157 weeks.
- *Room `PagingSource`.* A new dependency and a new idiom for a list that is bounded, local, and
  never larger than a few hundred rows. Principle VIII. Rejected.
- *Page by day count.* Would let a page boundary fall mid-week, requiring partial week rows or a
  second boundary rule. Rejected.

---

## R4 — What happens when opening a past day cannot produce a plan?

**Decision.** `GetDayDetail(date)` returns a sealed outcome and proceeds in this order:

1. Later than today, or earlier than the record start → `NoRecord`. Nothing is read or written.
2. A stored plan exists → summarise from it. No write.
3. Otherwise attempt `ensurePlanFor(date)`. **Failure is swallowed.**
4. Re-read. A plan now exists → summarise from it.
5. No plan → derive (R1) and summarise from that.
6. Derivation impossible because `versionEffectiveOn` returns null → `CatalogueUnavailable`.

**Rationale.** Clarification Q4. The two failure causes are not alike. A failed *write* leaves the
figures perfectly knowable — the same derivation that rendered the week row still works — so hiding
the day would cost the user a view because of a write they never asked for. An unavailable *catalogue
version* leaves nothing knowable, and showing an empty day scoring zero would be the fabricated
figure FR-032 exists to forbid. One surfaced failure state, not two.

Step 4's re-read rather than trusting `ensurePlanFor`'s return value is deliberate: `EnsureOutcome`
already distinguishes `Created`, `AlreadyExists` and `NoCatalogue`, but a swallowed exception leaves
no outcome at all, and re-reading is the one path that is correct in every case.

**Alternatives considered.**

- *Surface both failures identically.* Rejected by Q4 — a storage hiccup would read as history being
  lost.
- *Never write; always derive.* Re-opens Q2, which settled that opening a day is the deliberate act
  that earns a stored plan. Not reconsidered here.

---

## R5 — How does navigation carry "return to whichever list I came from"?

**Decision.** Replace `MainActivity`'s single `Destination` field with a `List<Destination>` back
stack held in `rememberSaveable`, with the existing `DestinationSaver` extended to encode a list.
One `BackHandler` at the host pops it. **No navigation library.**

**Rationale.** `002` chose a single field and wrote down the condition for revisiting it: a nav
library was not worth it "until a third destination with its own back stack (the tab shell) makes it
worthwhile". FR-015 and FR-015a are that moment — a past day must return to history or to the weekly
sheet depending on where it was opened, and a single field cannot represent which. The current code
encodes the answer in hard-coded `BackHandler`s (`DaySummary` always goes back to `Week`), which
FR-015 makes wrong.

A list is the smallest change that is correct. Navigation-Compose would add a dependency, route
strings, and a second serialization scheme for the same `LocalDate` parameter, for four destinations
with no deep links, no arguments beyond one date, and no nested graphs. Principle VIII.

**Alternatives considered.**

- *Pass an "opened from" enum into `DaySummaryRoute`.* Encodes the back stack in a parameter rather
  than in a stack — works for one level, breaks at two. Rejected.
- *Navigation-Compose.* Deferred until the three-tab shell in the design is actually built, which is
  Phase 6 or later.

---

## R6 — What breaks when today stops opening the read-only summary?

**Decision.** Route the current date to `TodayScreen` from both the weekly sheet and history
(FR-015a). The change is confined to `MainActivity`'s routing.

**Rationale and risk check.** `DayCellUi.isOpenable` already returns true for today — today is not
`NOT_YET_ELAPSED`, since that state requires `date.isAfter(today)` — so today's cell is already
tappable and currently lands on a read-only copy of a day the user can still act on. `WeekEvent.OpenDay`
is already handled by the host rather than the ViewModel, so the routing decision is already in the
right place.

`WeekNavigationTest` was checked directly: it exercises week navigation, the record-start floor and
midnight rollover, and asserts nothing about which destination a day opens. `MainActivity` has no
test at all. **Nothing pins the old behaviour**, so this is a behaviour change with no test to update
— which makes writing one part of this increment rather than a migration cost.

---

## R7 — Which glossary terms does this phase owe?

**Decision.** Add **Locked Day** and **Record Start** to `docs/GLOSSARY.md`. Do **not** add
Retro-Completion Window.

**Rationale.** `docs/PLAN.md` names Retro-Completion Window, Locked Day and "`completedAt` distinct
from the day the completion is credited to" as this phase's concepts, and `004` set the precedent of
adding a phase's terms to the glossary so a canonical name has exactly one home.

Two of the three are earned. *Locked Day* is a real domain state with a rule behind it. *Record Start*
is used across `003`, `004` and this spec as a load-bearing boundary and is defined nowhere. The third
is not: the glossary opens by stating that each term "means exactly one thing", and a
Retro-Completion Window whose width is permanently zero in shipped behaviour is a term for something
that does not exist. It stays in the specification, where the decision to keep it empty is recorded
with its reasoning, and it earns a glossary entry on the day it becomes non-empty.

The third `docs/PLAN.md` item needs nothing: `Completion.recordedAt` and `Completion.creditedDate`
have been distinct since `002`, and the glossary already covers Completion.
