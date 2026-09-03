# Verify Report — onboarding-exact-alarm-ask (third run)

**Verdict: PASS WITH WARNINGS** — 0 CRITICAL, 1 WARNING, 1 NOTE.

## Who ran this, and why it matters

Runs 1 and 2 were `sdd-verify` phase agents. The third run was attempted twice by a phase agent
and both attempts died after 600 s without progress — the maintainer's laptop was closed, so the
stall was environmental, not a defect in the phase or the runtime.

Rather than launch a third agent against the same conditions, the orchestrator completed the
remaining scope directly. That scope was narrow by design: runs 1 and 2 had already re-derived the
full 19-scenario mapping, and only two correction rounds had touched the tree since. What follows
was executed and read first-hand, not relayed.

## History

| Run | Verdict | Findings |
| --- | --- | --- |
| 1 | FAIL | 5 CRITICAL, 3 WARNING, 2 SUGGESTION |
| 2 | FAIL | 2 CRITICAL remaining; 3 closed with evidence |
| 3 | **PASS WITH WARNINGS** | 0 CRITICAL |

## A. The two survivors are closed — but the guard needed hardening first

`reminder-delivery`'s "Declining onboarding's offer costs nothing later" and "Declining onboarding's
ask does not suppress the banner" were proven only by architectural absence, which the compliance
rule does not accept. The second correction round answered with
`NoExactAlarmAskPersistenceTest` — reflection over `AlarmScheduler` and `ReminderSettingsStore`,
banning a persistence-shaped naming pattern.

Judged on merit rather than accepted. What was already right:

- A **self-reach assertion** — a scan that quietly matched nothing would otherwise pass both
  assertions vacuously, the same failure mode the two existing call-site guards protect against.
- Fields are scanned alongside methods, so a Kotlin property is caught by name.
- The pattern requires a recording verb **and** a completion noun, so `canScheduleExactAlarms` and
  `getSnoozeDuration` do not false-positive.
- The failure message says *delete the member rather than renaming it*, closing the obvious evasion.

**What was wrong, and was fixed here.** The `ReminderSettingsStore` check scoped its ban to names
containing "exact alarm", because that class legitimately holds the same shape for
`POST_NOTIFICATIONS`. Any synonym walked straight through. Inverted to an allow-list: the members
that legitimately carry the shape are two — now four, see below — known and stable, while the space
of violations is open-ended, so the closed set is the one worth enumerating.

Proved by planting `hasAskedAboutPreciseTiming()` on `ReminderSettingsStore`: it passes the
keyword-scoped check and **fails** the allow-listed one. Reverted; the production file is untouched.

Completing the allow-list surfaced something the keyword filter had been hiding: `onboardingDone`
and `setOnboardingDone` match on "done" and had never been examined, because the exact-alarm filter
discarded them before the shape test ran. Both are legitimate and are now declared as such rather
than invisible.

## B. Invariants re-checked after two correction rounds

| Invariant | Result |
| --- | --- |
| Page list construction-time, **no index clamp** | Holds — `private val pages` at `OnboardingViewModel.kt:72`, computed once; no `coerceIn`/clamp near `index` |
| `buildViewModel`'s `alarmScheduler` defaults granted | Holds — `OnboardingViewModelTest.kt:214-216` |
| No bare `OnboardingViewModel` in androidTest | Holds — no constructor call in `app/src/androidTest` |
| No new exact-alarm persistence | Holds — nothing matching in `ReminderSettingsStore`, and now guarded |
| `TodayBanners.kt` absent from the branch diff | Holds — "Fix" retired by string value, not key |

## C. The flakiness item

`today-slot-row-compose-test-timeout-flakiness` is filed **open** in `openspec/config.yaml` with its
evidence, and was not used to excuse anything in this change.

## Gates

| Command | Result |
| --- | --- |
| `:app:testDebugUnitTest --rerun-tasks` | **190 tests, 0 failures** |
| `:app:detekt :app:detektMain :app:lintDebug` | BUILD SUCCESSFUL |
| `:app:emulatorMatrixGroupDebugAndroidTest --rerun-tasks` | **api31 103/0 · api37 103/0** |

Detekt failed once on `MaxLineLength` in the hardened guard's own KDoc and was fixed by splitting
the line, not by raising the threshold. XML parsed with `xml.etree.ElementTree`.

## WARNING — the matrix is a degrading gate

Across this change's verification the matrix ran seven times; three runs failed, each on a
*different* method of the untouched `TodaySlotRowComposeTest`, never on this change's own tests.
Four distinct methods are now known across two sessions, all through the same `awaitNodeWithText`
helper.

This run was clean on the first attempt, but a gate that fails intermittently for reasons unrelated
to the change under test erodes its own authority: the next person to see it red will be tempted to
re-run rather than read. Filed and owned separately; it does not block this change, and it should
not stay open long.

## NOTE — Play policy remains unverified

Whether offering `SCHEDULE_EXACT_ALARM` during onboarding carries a Play Console constraint was
never verified; the phases that looked had no network access. It is honestly recorded as unknown
rather than quietly resolved. The change alters no manifest declaration — only *when* an existing
deep link is offered — so the exposure is a policy question, not a technical one.
