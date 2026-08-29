# Contract: the leaderboard read model

**Feature**: `specs/008-leaderboard-honor-board`

What a client may retrieve about other participants, and — more importantly — what it may never
retrieve. The negative half is the contract that matters: several Principle IX guarantees are
enforced by data being *unavailable*, not merely unrendered (research R8).

---

## The one rule

> A participant reads other participants **only** through `leaderboard_entries` and
> `honor_board_closed`, and those tables hold only what FR-002 says opting in publishes.

`completions` and `day_records` keep the `_select_own` policies spec 007 shipped. This increment
widens no existing policy. `rls-verification-008.sql` re-runs spec 007's assertions for exactly that
reason.

---

## Operations

### Fetch a ranking page

**In**: period kind, page cursor.
**Out**: up to 50 entries, position-ordered, each `(displayName, points, position)`; a `hasMore`
flag; the region's id and display name; whether the period is final.

Region is **not** an input. The service derives it from the caller's account (FR-009). A client
cannot request another region's ranking, and `rls-verification-008.sql` §2 asserts the policy
returns zero rows when it tries.

### Fetch own rank

**In**: period kind.
**Out**: the caller's own entry, its immediate neighbours, and the region's participant count.

A separate operation rather than a search through pages, so SC-009 holds at 10 000 participants
without paging — and so nobody has to scroll past 9 000 people to find themselves, which would be a
shame mechanic regardless of what the copy says (research R9).

### Fetch the Honor Board

**In**: period kind.
**Out**: qualifying members as `(displayName)`, and whether the caller is among them.

### Report a zone

**In**: an IANA zone id.
**Out**: the assigned region.

The client reports a **zone**, never a region (FR-014). The mapping table is readable by no client,
so a participant cannot look up which zone lands in a weak pool.

### Set participation

**In**: opted in or out.
**Out**: the resulting state.

Opting out clears every open period and keeps the participant out of periods opening afterwards;
closed periods are untouched (FR-004, FR-004a, FR-004b).

---

## What the read model must never expose

Each of these is absent from the schema, not filtered in a query — so no future endpoint, view or
client can surface one by accident.

| Never exposed | Why |
|---|---|
| Any completion, task slug, recorded time or reversal of another participant | The entire point of R1. This is a person's worship record |
| Another region's entries | FR-009, SC-007 |
| A count of participants who did not qualify for the Honor Board | FR-030, SC-012 |
| The Honor Board threshold, or any distance to it | FR-030 — "3 days short" is deficit framing |
| Any per-person `days_engaged` on the Honor Board | Would let a client rank a surface that must not be ranked (FR-029) |
| `isLast`, `isBottom`, or any bottom-of-list marker | FR-038 — a last-place row must render identically to every other |
| Rank history, trend, or position change | FR-039 — storing it invites the notification the constitution forbids |
| Points behind the leader, or any gap-to-next figure | Deficit framing again, Principle IX |
| Any email address | FR-006 |

**Note on `days_engaged`.** It exists on `leaderboard_entries` because the aggregation job needs it
to decide Honor Board qualification. It is *not* part of the Honor Board response. If a future
change exposes it there, SC-012's structural check in `rls-verification-008.sql` §7 will not catch
it — that check scans column names — so the Honor Board response shape is asserted separately by
contract test.

---

## Failure behaviour

| Condition | Response | Requirement |
|---|---|---|
| Service unreachable | `Unavailable`; the cached page renders, stamped with its age | FR-035, FR-036 |
| No cache and unreachable | `Unavailable`. **Never an empty list** — an empty ranking reads as "nobody is ahead of you" | spec edge case |
| Caller not opted in | No ranking data at all; the invitation is the only thing that renders | FR-001, SC-001 |
| Caller not signed in | Nothing. No invitation, no placeholder, no entry point | FR-033 |
| Caller has unsynced completions | Ranking renders with `isProvisional`, saying the standing is catching up | FR-037 |

No failure response carries a message attributing the cause to the person (FR-035). The result types
have no field for one.
