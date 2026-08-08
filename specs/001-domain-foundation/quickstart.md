# Quickstart: Validating the Domain Foundation

**Feature**: 001-domain-foundation | **Date**: 2026-08-08

How to prove this feature works. Every step is runnable; none needs a device or emulator.

## Prerequisites

- JDK 11+ (project targets Java 11)
- No emulator, no device, no network
- Branch `spec/001-domain-foundation`

One build-file change is required before anything runs: `kotlinx.serialization` as
`testImplementation` on `:app`, plus the `kotlin-serialization` plugin. Nothing is added to
`implementation` — the APK is untouched.

## Run everything

```bash
./gradlew :app:testDebugUnitTest
```

JVM only. Should complete in seconds — the catalogue is ~40 static records.

## Scenario 1 — Positive control

```bash
./gradlew :app:testDebugUnitTest --tests "*CatalogueValidatorTest.valid catalogue has no defects*"
```

**Expected**: passes. `validate()` returns `emptyList()` for `good/valid-catalogue.json`.

## Scenario 2 — Arithmetic

```bash
./gradlew :app:testDebugUnitTest --tests "*CatalogueArithmeticTest*"
```

**Expected**: passes. Asserts 69 (Sat/Sun/Tue/Wed), 74 (Mon/Thu), 76 (Fri), 500 (week), and the
section composition from [data-model.md](./data-model.md).

## Scenario 3 — Every rule can fail (SC-003)

```bash
./gradlew :app:testDebugUnitTest --tests "*CatalogueValidatorTest*"
```

**Expected**: passes — meaning each of the 16 bad fixtures produced its own expected defect. See the
rule → defect → fixture table in
[contracts/validator-contract.md](./contracts/validator-contract.md).

Each bad fixture differs from the good one in exactly one way, so a caught defect names one rule.
The single exception is `bad/two-defects.json`, which is not a rule fixture — it proves that
`validate` reports every defect rather than stopping at the first.

## Scenario 4 — Mutation check (SC-002)

```bash
./gradlew :app:testDebugUnitTest --tests "*CatalogueMutationTest*"
```

**Expected**: passes. Takes the good fixture, alters one point value in memory, asserts the run now
reports `WeekdayTotalMismatch` and `WeekTotalMismatch`.

**This is the test that proves the suite is real.** Break it deliberately to confirm:

```bash
# temporarily change one point value in good/valid-catalogue.json from 2 to 3
./gradlew :app:testDebugUnitTest
# EXPECTED: failures in CatalogueArithmeticTest. If it passes, the suite is decorative.
git checkout -- app/src/test/resources/catalogue/good/valid-catalogue.json
```

## Scenario 5 — Domain purity (Principle II)

```bash
./gradlew :app:testDebugUnitTest --tests "*DomainPurityTest*"
```

**Expected**: passes. Asserts no `android.*` import appears in the validator or model sources, so
the Phase 2 move into `:domain` stays a file move.

## Scenario 6 — Documents

Not automated. Check by hand:

```bash
# 12 glossary terms present
grep -c "^## " docs/GLOSSARY.md          # expect 12

# spelling corrected, nothing left behind
grep -rn "catalog\([^u]\|$\)" docs/ CLAUDE.md .specify/memory/   # expect no output

# decisions recorded in place, section renamed
grep -n "Architectural Decisions (Recorded)" docs/PLAN.md        # expect 1 hit
grep -c "^[0-9]\+\. \*\*" docs/PLAN.md                           # expect 12 decisions
```

## Definition of done

| Check | Signal |
|---|---|
| SC-001 | Good fixture clean; all 16 bad fixtures caught |
| SC-002 | Mutation test passes; manual break produces a failure |
| SC-003 | Every rule row has a fixture and a test |
| SC-004 | 12 terms in `docs/GLOSSARY.md` |
| SC-005 | 12 decisions recorded; zero stray `catalog` spellings |
| SC-006 | Rule table rows = defect variants = fixtures + 1. Count them: 17 / 17 / 16 |
| SC-007 | `git diff --stat` touches no `src/main`; `settings.gradle.kts` has one `include` |

Last one is worth running literally:

```bash
git diff develop-v1... --stat -- app/src/main
# EXPECTED: empty. Any output means production code leaked in.
```
