# Contract: identity

One sign-in method, no password anywhere in the product, no social provider (FR-002). Supabase's
email one-time-code flow (`auth-kt`, `signInWith(OTP)` + `verifyEmailOtp`), reached only through
`AccountRepository`.

**Sign-up and sign-in are the same action.** `signInWith(OTP) { createUser = true }` — an unknown
email creates the account on first successful code entry, which is how FR-001's "create an account …
from within the app" is satisfied without a second screen or a second flow. Leaving this at the
provider's default would silently make sign-up impossible while sign-in still worked, so it is
stated here rather than left to the implementer.

**No password field exists anywhere in the app.** No `signInWith(Email)` call, no password parameter,
no password input, no password stored or transmitted. `SignInScreenTest` asserts the absence of a
password field, and the import/usage audit checks for the call (FR-002).

## The sign-in round trip

```text
EnteringEmail(email)
   │ submit
   ▼
RequestingCode(email) ──requestCode()──┬── Sent(resendAvailableAt) ──> AwaitingCode
                                       ├── NeedsConnection ─────────> NeedsConnection(email)
                                       └── AddressNotAccepted ──────> EnteringEmail(email, invalid)
AwaitingCode(email, resendAvailableAt)
   │ enter code                          │ resend (only at/after resendAvailableAt)
   ▼                                     └──> RequestingCode(email)
Confirming(email, code) ──confirmCode()──┬── SignedIn ──────────────> profile / back to Today
                                         ├── NotAccepted(EXPIRED)  ─> CodeNotAccepted ──> AwaitingCode
                                         ├── NotAccepted(INCORRECT)─> CodeNotAccepted ──> AwaitingCode
                                         ├── NeedsConnection ───────> NeedsConnection(email)
                                         └── WouldReplaceLocalRecords ──> confirmation (see below)
```

Four properties FR-002a requires, and each is a test:

1. **The entered email survives every transition.** A mistyped address is corrected by editing, not
   by retyping from scratch, and no failure path returns to an empty field.
2. **No local record is touched by any outcome above.** Sign-in that never completes leaves the
   device exactly as it was (US1 AS3).
3. **Resend has a stated wait.** `resendAvailableAt` is carried in the state; the button states when
   it becomes available rather than failing silently.
4. **A code that never arrives is recoverable from this screen.** Resend and "use a different
   address" are both reachable without leaving.

`NeedsConnection` is a statement of fact — "signing in needs a connection" — and the app behind it
remains fully usable offline. It is not an error state and carries no red (Principle IX).

## Session lifecycle

| Event | Behaviour |
|---|---|
| App restart, process death | Session restored without re-authentication (FR-005). supabase-kt persists it; `observeSession()` emits the restored session before the first frame that depends on it. |
| Token near expiry | Renewed silently by the client. No user-visible state change (FR-005, US2 AS6). |
| Renewal fails | `observeSession()` emits `SignedOut`. **Every local record stays, the app stays fully usable, and sign-in is offered — never a lockout, never hidden history** (FR-006). |
| Signed out, offline | Everything in Phases 2–6 works (FR-003, SC-007). |

**Session storage**: supabase-kt's default session store on Android (SharedPreferences via
multiplatform-settings). This holds a refresh token on a device whose owner is the account holder;
the product stores no password, no payment data, and no third-party credential. Hardening it to
`EncryptedSharedPreferences` is recorded as a follow-up rather than done here, because the current
`androidx.security-crypto` situation would add a deprecated dependency to solve a problem the threat
model does not yet have. Stated explicitly so it is a decision, not an oversight.

## Sign-out

Two paths, deliberately different in weight (FR-007a–d):

| | Plain sign-out | Sign out and remove data from this device |
|---|---|---|
| Label | "Sign out" | "Sign out and remove data from this device" |
| Local records | **All kept**, fully usable signed-out | Removed |
| Account records | Untouched | Untouched |
| Pending changes | Warned about plainly | Warned about, and named, before a second confirmation |
| Confirmation | One | Two — the second names what is about to be removed |

The pending-change warning states a fact — "N changes have not been backed up yet" — with no blame
and no red. The removing path does not proceed past it without the further confirmation.

Signing back into the same account afterwards restores the full record with no duplication: every
row is keyed by a client UUID, and every write is an upsert (US5 AS5, FR-017).

## Signing a different account into the device

A third path with the same weight as "sign out and remove data", because it has the same effect on
local records (FR-013a):

1. The user enters a different email and a valid code.
2. `confirmCode(..., replaceLocalRecords = false)` returns `WouldReplaceLocalRecords` and **opens no
   session** — nothing has changed at this point.
3. The app shows a confirmation naming the account being replaced, the number of recorded days, the
   number of completions, and any changes of that account not yet backed up.
4. Declining returns to the sign-in screen with the entered email intact and the device untouched.
5. Accepting calls `confirmCode(..., replaceLocalRecords = true)`, which wipes the previous
   account's local records and then opens the new session.

Nothing is removed from either account at any step (FR-007d). Records belonging to the previous
account are never migrated to, attributed to, or shown to the new one (FR-013).

## Profile

Minimal by design (FR-007, FR-007e):

- The email the user signed in with — always shown, never editable here.
- An optional display name — empty by default, editable at any time, never requested during sign-in,
  never required to use anything. Where a name would be shown and none is set, the email is shown.
- Sync status, read from `SyncRepository`.
- **A plain-language statement of the conflict policy**, beside the status (FR-019a): *"If you record
  on two devices at once, both records are kept. If you undo something on one device, it stays undone
  on the other."* This is the user-reachable statement FR-019a requires; `docs/PLAN.md` is developer
  documentation and does not satisfy it on its own.
- Both sign-out actions.

No avatar, no bio, no visibility control, no public surface. Collecting a name for a public surface
is Phase 8's problem, at the point of opting in (spec Clarification Q5).

## Configuration

`SUPABASE_URL` and `SUPABASE_ANON_KEY` reach the app through `BuildConfig` from
`local.properties`/CI secrets, and are read only by `SupabaseClientFactory`. The anon key is a public
client key — it is safe in a client precisely because RLS, not the key, is what protects the data
(FR-023). **A service-role key must never appear in the app**: it bypasses RLS entirely.

Missing configuration degrades cleanly: `AccountRepository` reports sign-in as unavailable and the
app runs as the offline MVP. It never crashes at start-up over an absent key.
