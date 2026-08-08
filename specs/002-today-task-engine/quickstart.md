# Quickstart: Validating the Today Screen

**Feature**: 002-today-task-engine | **Date**: 2026-08-09

How to prove this feature works. Most of it runs on the JVM; the Room layer needs a device.

## Prerequisites

- JDK 11+
- **A device or emulator** for the `:data` tests — this is the first part of the project that cannot
  be verified by `./gradlew test` alone (research.md R7)
- No network at any point. If any step needs it, something is wrong.

## Run everything that does not need a device

```bash
./gradlew :domain:test :app:testDebugUnitTest
```

## Run the persistence tests

```bash
./gradlew :data:connectedDebugAndroidTest
```

---

## Scenario 1 — The catalogue still validates after the move

```bash
./gradlew :domain:test --tests "*CatalogueValidatorTest*" --tests "*CatalogueArithmeticTest*"
```

**Expected**: passes unchanged. The `001` suite moved modules; it did not change. Totals still
69 / 74 / 76 / 500.

**This is the load-bearing check for the Adhkar correction.** The catalogue now has 32 tasks rather
than 40, with Adhkar as one task at limit 9. If the contract needed editing to accept that, stop —
see spec.md, *Required catalogue correction*.

## Scenario 2 — `:domain` cannot see Android

```bash
./gradlew :domain:test --tests "*ModuleBoundaryTest*"
```

**Expected**: passes — `:domain`'s build file applies `kotlin("jvm")`, not `com.android.library`.

Prove it is real:

```bash
# temporarily add `import android.os.Build` to any file in domain/src/main
./gradlew :domain:compileKotlin
# EXPECTED: compile error, unresolved reference. Then remove it.
```

That compile error *is* Principle II. If it compiles, the module type is wrong.

## Scenario 3 — Applicability and scoring

```bash
./gradlew :domain:test --tests "*ResolveApplicableTasksTest*" --tests "*ScoreDayTest*"
```

**Expected**: each weekday yields its correct task set and available total; scoring covers zero,
partial, complete, and multi-occurrence.

## Scenario 4 — Occurrence limits and undo (the Q4 case)

```bash
./gradlew :domain:test --tests "*OccurrenceTest*"
```

**Expected**: Adhkar accepts nine and refuses a tenth; one undo makes exactly one more possible.

Critical assertion, from SC-012: complete to the limit, undo, complete again — repeated ten times —
and the earned total must equal never having undone at all. **If a task can be permanently locked
below its limit by undoing, the tombstone filter is missing from a read path.**

## Scenario 5 — Day rollover with a fake clock

```bash
./gradlew :domain:test --tests "*DayBoundaryTest*" --tests "*RolloverTest*"
```

**Expected**: advancing the fake clock past local midnight yields a new date; the previous date's
plan is unchanged. No test anywhere reads the real clock.

Prove the clock is injected:

```bash
grep -rn "LocalDate.now()\|Instant.now()\|System.currentTimeMillis()" \
  domain/src/main data/src/main app/src/main
# EXPECTED: hits only inside SystemTimeProvider. Anything else violates Principle VII.
```

## Scenario 6 — History stays honest (Principle III)

```bash
./gradlew :data:connectedDebugAndroidTest --tests "*DayPlanImmutabilityTest*"
```

**Expected**: passes. Seed catalogue v1, create a day plan, introduce v2 with changed points and a
changed schedule, then re-read the v1 day. Its tasks, points and available total are identical;
today reflects v2.

**This is the test the whole storage design exists for.** It is worth reading the assertions by hand
before trusting a green result.

## Scenario 7 — Idempotent seeding

```bash
./gradlew :data:connectedDebugAndroidTest --tests "*CatalogueSeederTest*"
```

**Expected**: seeding twice leaves row counts, plans and completions identical (FR-001). A defective
catalogue writes nothing at all and returns `Failed`.

## Scenario 8 — Offline on a fresh install

Manual, and the one that matters most.

```bash
adb uninstall com.giraffe.mizanapp
# put the device in airplane mode
./gradlew :app:installDebug
# open the app
```

**Expected**: tasks appear immediately, both dates show, completing and undoing works, and the score
updates. No spinner beyond first load, no error, no empty state.

**No step above may require the network.** If any does, Principle IV is broken.

## Scenario 9 — The Principle IX pass

Manual. Open the screen with nothing completed and read every visible element.

**Expected**: no red, no ✗, no "missed", no "0 remaining" framing, no negative number anywhere. A
zero day reads as a day not yet begun, never as a failure.

Cross-check against the audit list in `CLAUDE.md`'s Design section.

---

## Definition of done

| Check | Signal |
|---|---|
| SC-001 | Fresh install, airplane mode, complete a task, score changes |
| SC-002 | 69 / 74 / 76 by weekday, 500 across the week |
| SC-003 | 20 mixed complete/undo operations leave earned equal to the live rows' sum |
| SC-004 | Catalogue change leaves recorded days untouched (Scenario 6) |
| SC-005 | Kill and reopen: every record and total preserved |
| SC-006 | Fake clock past midnight: new plan, previous byte-for-byte unchanged |
| SC-007 / SC-012 | Adhkar at 9, undo, 9 again — no permanent loss (Scenario 4) |
| SC-008 | No waiting state on completion |
| SC-009 | Scenario 9 |
| SC-010 | Network permanently off changes nothing |
| SC-011 | Adhkar nine times contributes exactly 18 |

Finally, confirm the module boundary held:

```bash
grep -rn "^import android\.\|^import androidx\.\|^import androidx.room\." domain/src/main
# EXPECTED: empty. The compiler should already have made this impossible.
```
