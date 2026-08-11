# Mizan Domain Glossary

Sixteen terms. Each means exactly one thing, everywhere — in a specification, a test name, and a type
name. Where two terms are easy to confuse, the difference is stated explicitly rather than left to
inference.

No technology appears in any definition. These are statements about the domain, not about how it is
stored or displayed.

## Task Definition

A practice that can be recorded — one line on the accountability sheet. Carries its identity, the
section it belongs to, and its position within that section.

A Task Definition says *what* the practice is. It says nothing about what the practice is worth or
when it applies; those change over time, and identity must not. Contrast [Task Version](#task-version).

## Task Version

What a Task Definition was worth, and when it applied, under one version of the catalogue: its point
value, its schedule rule, and how many times a day it may be completed.

This is the thing a past day is scored against. Separating it from Task Definition is what allows an
administrator to change a task's points without rewriting what already-recorded days report.

## Section

The grouping a task is displayed and totalled under — the prayer blocks, the night prayer, Quran,
the remembrances, the weekday fast, the Friday practices.

A Section carries its own order. Task positions are counted inside a section, so two tasks in
different sections may share the same position without conflict.

## Schedule Rule

The statement of which dates a task applies to. Today two forms exist: *every day*, and *specific
days of the week*. A rule that matches no day at all is invalid — a task that can never appear is
not a task.

The vocabulary is deliberately open: date-anchored rules for occasions in the lunar calendar can be
added later without changing what the existing two forms mean.

## Day Plan

The frozen record of one date: the complete set of tasks that applied to it, and the total points
that were available on it.

A Day Plan is written once, the first time that date is opened, and never recomputed. This is what
makes a past day reproducible: if the catalogue changes afterwards, the Day Plan does not. Contrast
[Planned Task](#planned-task), which is one entry inside it.

## Plan Origin

Whether a Day Plan exists because the date was actually opened, or because it was filled in
afterwards for a date the app never saw.

The two look identical to the person using the app — a date with nothing recorded reads the same
either way. The distinction exists for consistency counting: opening a date is evidence the app was
used that day; being filled in afterwards is not, and must never be read as if it were.

## Planned Task

One task's entry within a single Day Plan — the task as it stood on that date, with the points it
was worth then and the number of times it could be completed.

A Day Plan is the whole day; a Planned Task is one line of it.

## Completion

A record that a task was carried out — which task, which date it is credited to, how many points it
earned, and when it was actually recorded.

A Completion carries its own points rather than looking them up. This is what stops a later change
to a task's value from silently re-scoring the past. Contrast [Occurrence](#occurrence).

## Occurrence

One recordable instance of a task within a single day, bounded by the number of times that task
allows.

The distinction from Completion is countable: a task permitting three occurrences a day can hold up
to three Completions against one date. An Occurrence is the *slot*; a Completion is the *record*
that fills it. Undoing removes the most recent Completion, freeing its Occurrence.

## Daily Score

For one date, the points earned against the points that were available on it, taken from that day's
Day Plan.

Both halves matter. Earned points alone cannot be read, because what was achievable that day depends
on which tasks applied to it.

## Day Summary

A read-only rendering of one date's Day Plan and its Completions — what applied, what was done, and
the Daily Score that follows from them. It shows a date; it cannot change one.

## Week Key

A stable name for one week, derived from the date its Saturday falls on. The same week is recognised
as the same week wherever it is referred to.

## Weekly Score

The same measure as a Daily Score, extended across one week running Saturday to Friday — but with a
week in progress, "available" splits in two. Points earned are read against what has *elapsed* so
far, never against the full week; the full week's total is shown alongside as a separate figure, not
as the denominator. For a week that has run its course, the two figures are the same number.

## Record Start

The earliest date for which any Day Plan exists. Dates before it are outside the record altogether —
not days that read as zero, but days that were never being tracked at all. It is the floor beneath
which nothing is filled in and no week can be visited.

## Consistency Day

A date on which at least one applicable task was completed. It is a yes or no, not a quantity — a
day with one completion counts exactly as much as a day with forty.

The measure is consistency, not perfection. Nothing is deducted for what was not done.

## Streak

A run of consecutive Consistency Days.

A streak is broken by a day with no completions, never by a day with few. When one ends, what was
recorded before it stands unchanged — the count restarts, the history does not.

---

**A note on naming.** The specification for increment `001` refers to a **validation contract**: the rules deciding
whether a catalogue is admissible. In code that contract is realised as `CatalogueValidator`. The
two names refer to the same thing.
