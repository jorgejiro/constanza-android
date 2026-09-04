```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:04a2d8508dcda55c8a8824fbae6d561a2f13d885ae57f297339bd204604074b3
verdict: fail
blockers: 0
critical_findings: 0
requirements: 1/3
scenarios: 15/24
test_command: JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :domain:test :app:testDebugUnitTest --rerun
test_exit_code: 0
test_output_hash: sha256:0230f5f589a275c0483898f5c33712233abcf0a2f596707e756a8260d43a485f
build_command: JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:compileDebugAndroidTestKotlin --rerun
build_exit_code: 0
build_output_hash: sha256:b064ee149b1495e6216fed874a44da62127a6f22eba3a7225bb3039676b4a467
```

## Verification Report

**Change**: today-past-day-correction
**Scope of THIS verification**: Slice A / PR 1 only — `TodayViewModel`'s viewed-date/clock-truth
split, the three navigation gestures, the UI-state reset, its JVM tests, and the streak regression.
Working tree on branch `feat/today-viewed-date-split`, uncommitted. Phases 3–6 (Slice B: screen,
strings, instrumented tests, config close-out) are deliberately unstarted and are NOT scored as
incomplete or blocking in this report — they land in PR 2.
**Version**: delta spec `openspec/changes/today-past-day-correction/specs/habit-entry-tracking/spec.md`
**Mode**: Standard (`strict_tdd` scoped to `:domain` only; this change adds no `:domain` production
code, so strict TDD has no subject — correctly not enforced)

### Completeness (Slice A scope: Phases 1, 2, and the JVM-applicable parts of Phase 7)
| Metric | Value |
|--------|-------|
| Slice A tasks total (Phase 1 + Phase 2 + applicable Phase 7) | 26 |
| Slice A tasks complete | 26 |
| Slice A tasks incomplete | 0 |
| Slice B tasks (Phases 3–6, task 7.5) | 24, intentionally `[ ]` — out of scope for this report |

### Build & Tests Execution
**Build**: ✅ Passed (fresh, forced with `--rerun`, not cached `UP-TO-DATE`)
```text
$ JAVA_HOME=".../jbr" ./gradlew :app:compileDebugAndroidTestKotlin --rerun
> Task :app:compileDebugAndroidTestKotlin
w: ...LanguageOverrideComposeTest.kt:68:27 'fun createComposeRule(...)' is deprecated (pre-existing,
   unrelated to this change)
BUILD SUCCESSFUL in 1s — exit 0
```

**Tests**: ✅ 273 passed / ❌ 0 failed / ⚠️ 0 skipped (fresh execution, JUnit XML read directly, not
inferred from `BUILD SUCCESSFUL` text)
```text
$ JAVA_HOME=".../jbr" ./gradlew :domain:test :app:testDebugUnitTest --rerun
:app:testDebugUnitTest  -> 221/221, 0 failures, 0 errors (26/26 in TodayViewModelTest, freshly
                            re-executed at 2026-09-04T20:53:14Z, not cache-replayed)
:domain:test            -> 52/52, 0 failures, 0 errors (10/10 in StreakCalculatorTest, freshly
                            re-executed at 2026-09-04T20:53:28Z)
Total: 273/273 passed, 0 failed, 0 skipped
```
Counts were read from `app/build/test-results/testDebugUnitTest/*.xml` and
`domain/build/test-results/test/*.xml` `<testsuite tests=... failures=... errors=...>` attributes
after a forced `--rerun`, per the "read the JUnit XML, a bare BUILD SUCCESSFUL is not evidence" rule.

**Static analysis**: `./gradlew :domain:detektMain :app:detektMain` (forced `--rerun`) — BUILD
SUCCESSFUL, 0 findings on both modules (`## Findings (0)` in both `detekt.md` reports, empty
`<checkstyle>` body in both `detekt.xml`), `maxIssues: 0` satisfied.

**Coverage**: ➖ Not available (no coverage tool configured for this project; not part of the
verification commands the orchestrator specified)

### Core Mechanism — Traced in Code, Not Taken on Trust
| Check | Finding |
|---|---|
| Both clock-driven writers remain unconditional | ✅ Confirmed. `TodayViewModel.kt:186` (`init` timer collector) and `:229` (`refreshDate()`) both do a bare `dateState.update { it.copy(clock = date) }` / `dateState.update { it.copy(clock = currentDateSource.today()) }` with **no** `if`/guard on `navigated`. Neither writer grew a condition. |
| Forward re-attachment sets `navigated = null`, never pins to clock | ✅ Confirmed. `showNextDay()` (`:253-267`): `if (next >= current.clock) { if (current.navigated != null) { …; dateState.update { it.copy(navigated = null) } } }` — sets `navigated = null`, never `navigated = current.clock`. Backed by `TodayViewModelTest.kt:565-581`, `forward navigation onto today re-attaches, so a later tick still moves the view`: navigates back, forward (lands on `TODAY`), then advances the fake clock to `TOMORROW` and asserts `uiState.value.date == TOMORROW` — this is exactly the test that would fail if `showNextDay()` pinned `navigated = clock` instead of nulling it. |

### Spec Compliance Matrix — All 24 Delta-Spec Scenarios
The actual retrieved spec file (`specs/habit-entry-tracking/spec.md`) contains **3 requirements and
24 scenarios** (`rg -c '^#### Scenario:'` = 24), not the 22 that `tasks.md`'s "Hard constraints
honored" section and `design.md`'s Testing Strategy section both state — see WARNING below. Counts
in the YAML envelope above use the actual 24, per this skill's rule to never invent totals.

| # | Requirement | Scenario | Status this PR | Test / evidence |
|---|---|---|---|---|
| 1 | Provisional-Missed Correction | Happy path — live snooze never becomes missed, then completes | Not covered by either — out of scope (EntryWriter/OccurrenceResolver untouched by this diff; not re-verified in this pass) | — |
| 2 | Provisional-Missed Correction | Force-resolved missed corrected by a late answer | Not covered by either — out of scope (same as above; `EntryWriteParityTest` extension for the past-date variant is Slice B task 5.5) | — |
| 3 | Provisional-Missed Correction | Manual in-app edit corrects a past missed day | Covered now (mechanism) — full UI proof deferred to Slice B | `TodayViewModelTest.kt` "answer on a past day passes the viewed date to answerInApp" (2.9), "a past slot is freely re-editable through every status, twice around the cycle" (2.15); instrumented walk is Slice B task 5.1/12 |
| 4 | Provisional-Missed Correction | Streak interaction | ✅ Covered now | `StreakCalculatorTest.kt` "streak recomputed after a late correction shows no break" — pre-existing, byte-identical to `origin/main`, independently re-verified (see below) |
| 5 | Provisional-Missed Correction | Unbounded backward reach for a manual edit | Covered now (mechanism) — UI navigation deferred to Slice B | `TodayViewModelTest.kt` "N backward steps reach clock minus N, unbounded" (2.8) |
| 6 | Provisional-Missed Correction | Any past slot is freely re-editable to any status | Covered now (mechanism) — UI-driven proof deferred to Slice B task 5.4 | `TodayViewModelTest.kt` "a past slot is freely re-editable through every status, twice around the cycle" (2.15) |
| 7 | In-App Answer Date Attribution | Answer given before midnight is recorded on that date | ✅ Covered now | `TodayViewModelTest.kt` "answering hands EntryWriter the slot's live occurrence handle and the injected today" — pre-existing, unmodified, still green |
| 8 | In-App Answer Date Attribution | Answer given after midnight is recorded on the new date, not the old one | ✅ Covered now | `TodayViewModelTest.kt` "answer writes against the currently displayed date, even for a slot captured before midnight rolled over" — pre-existing, unmodified, still green |
| 9 | In-App Answer Date Attribution | Answering a navigated-to past day credits that date, not today | ✅ Covered now | `TodayViewModelTest.kt` "answer on a past day passes the viewed date to answerInApp" (2.9) |
| 10 | Day-Level Rollup and Per-Slot Display | Day rollup reports partial completion | Not covered by either — out of scope this review (pre-existing `:domain` rollup, unmodified) | — |
| 11 | Day-Level Rollup and Per-Slot Display | A missed slot alongside a completed slot reports partial, not missed | Not covered by either — out of scope this review | — |
| 12 | Day-Level Rollup and Per-Slot Display | No completion at all still reports a missed day | Not covered by either — out of scope this review | — |
| 13 | Day-Level Rollup and Per-Slot Display | Today screen shows independent slot rows | Not covered by either — out of scope this review (pre-existing UI, unmodified) | — |
| 14 | Day-Level Rollup and Per-Slot Display | An answered slot names its specific answer and offers one route, without colour | Not covered by either — out of scope this review | — |
| 15 | Day-Level Rollup and Per-Slot Display | The change route is reachable without a gesture and names its own slot | Not covered by either — out of scope this review | — |
| 16 | Day-Level Rollup and Per-Slot Display | Today screen rolls over at local midnight while displayed | ✅ Covered now | `TodayViewModelTest.kt` "crossing midnight while displayed re-subscribes EntryDao and moves both the rollup and the occurrence filter" — pre-existing regression, extended by task 2.14's new expansion-state assertions (see below) |
| 17 | Day-Level Rollup and Per-Slot Display | A backgrounded app corrects the date on resume | ✅ Covered now | `TodayViewModelTest.kt` "refreshDate corrects a stale observedDate left over from backgrounding" — pre-existing, unmodified, still green; `refreshDate()` confirmed unconditional at `:229` |
| 18 | Day-Level Rollup and Per-Slot Display | Foregrounded timezone travel remains a known, accepted gap | ✅ Satisfied by definition | Explicitly accepted boundary in the spec itself ("screen MAY keep showing the prior date") and in `design.md`'s "Accepted boundary" note; no vehicle is required or expected |
| 19 | Day-Level Rollup and Per-Slot Display | Midnight rollover does not move a deliberately viewed past date | Covered now (mechanism) — UI-level (real navigable screen) deferred to Slice B | `TodayViewModelTest.kt` "a midnight tick while on a past day does not move uiState's date" (2.2) |
| 20 | Day-Level Rollup and Per-Slot Display | Resume does not move a deliberately viewed past date | Covered now (mechanism) — deferred to Slice B for UI | `TodayViewModelTest.kt` "refreshDate while on a past day does not move it" (2.3) |
| 21 | Day-Level Rollup and Per-Slot Display | Returning to the live edge resumes following the clock | Covered now (mechanism) — deferred to Slice B for UI | `TodayViewModelTest.kt` "showToday after a tick that fired while away lands on the new clock date" (2.4), "forward navigation onto today re-attaches, so a later tick still moves the view" (2.6) |
| 22 | Day-Level Rollup and Per-Slot Display | Forward navigation stops at today | Covered now (mechanism) — deferred to Slice B for UI | `TodayViewModelTest.kt` "showNextDay at the live edge is a no-op" (2.7) |
| 23 | Day-Level Rollup and Per-Slot Display | Add-habit affordance is absent on a past day | Deferred to Slice B — `TodayUiState.isPastDay` exists and is proven (2.12), but `TodayContent`/`TrailingAddHabitAction` are not yet wired to it (tasks 3.5, 5.2) | — |
| 24 | Day-Level Rollup and Per-Slot Display | Per-slot UI state does not leak across a navigation | Covered now (mechanism) — deferred to Slice B for a real Compose-level proof | `TodayViewModelTest.kt` "both expansion sets are empty after a navigation that changes the viewed date" (2.10), "a tick while navigated away neither clears expansion state nor re-subscribes Room" (2.11) |

**Compliance summary**: 15/24 scenarios fully satisfied for the current shipped surface (14 "Covered
now" + 1 "Satisfied by definition"); 1 scenario explicitly deferred to Slice B (23); 8 scenarios out
of this review's file scope (1, 2, 10–15) — pre-existing and unmodified by this diff, not
independently re-verified here. **None of the 9 not-yet-complete scenarios are failures**: 1
(add-habit affordance) is expected and tracked for PR 2; the other 8 are inherited, unmodified
behavior this change does not touch.

### Gap Scenarios the Design Flagged — Independently Checked
| Gap | Claim in apply-progress | Independent check | Verdict |
|---|---|---|---|
| "Any past slot is freely re-editable to any status" | New JVM test added (2.15) | Read `TodayViewModelTest.kt:713-739`: walks `COMPLETED → MISSED → SKIPPED` twice via `viewModel.answer(...)`, then `coVerify(exactly = 2)` for each status against `YESTERDAY`. Genuinely exercises the unrestricted-transition claim. | ✅ Confirmed |
| "Streak interaction" | Claimed satisfied by a **pre-existing, unmodified** test in `StreakCalculatorTest.kt` | `git diff origin/main -- domain/src/test/kotlin/com/jjrapps/constanza/domain/StreakCalculatorTest.kt` → **empty diff**, confirming the file is byte-identical to `origin/main`. Read the test (`streak recomputed after a late correction shows no break`, lines 105-116): asserts `StreakCalculator.current(daily, beforeCorrection, today) == 0` (broken, last day `MISSED`) and `StreakCalculator.current(daily, afterCorrection, today) == 6` (unbroken, last day `COMPLETED`) — the same before/after pattern the spec scenario describes ("a streak calculation that currently treats [a date] as broken... a streak calculation run after the correction MUST show an unbroken streak"). | ✅ The claim holds — this genuinely covers the scenario, not a false claim |

### Task 2.14 Rollover Assertion — Independently Checked
Claim: the missing assertion was added to the existing rollover test at
`TodayViewModelTest.kt:270`, proving `expandedHabitIds`/`reopenedSlots` survive a midnight rollover
at the live edge. Confirmed at lines 302-327: `expandedHabitIds`/`reopenedSlots` are populated
*before* `currentDateSource.advanceTo(TOMORROW)`, then asserted unchanged *after* it —
`assertEquals(setOf(HABIT_ID), onTomorrow.expandedHabitIds, "a live-edge midnight rollover must
leave expandedHabitIds alone")` and the equivalent for `reopenedSlots`. This is exactly the
assertion design.md's Decision 3 said was missing. ✅ Confirmed present and correctly asserting.

### Design Deviations — Judged
| Deviation | Claim | Independent check | Verdict |
|---|---|---|---|
| `clearPresentedSlotState()` not extracted as a shared helper | Inlined into all 3 gestures to stay under detekt's `TooManyFunctions` ceiling | `TodayViewModel.kt` has exactly 10 `fun` declarations (`rg -n "^\s*fun "` → 10 matches); no `clearPresentedSlotState` function exists anywhere in the file — only mentioned in a KDoc comment. `showPreviousDay`, `showNextDay`, `showToday` each duplicate the identical two-line `expandedHabitIds.value = emptySet(); reopenedSlots.value = emptySet()`. Behaviour-neutral: three call sites doing the same two assignments is identical at runtime to one shared private function doing it. | ✅ Behaviour-neutral, as claimed |
| `readNotificationPermission` inlined | Former private helper inlined into `refreshNotificationPermission()` and `recordNotificationPermissionRequested()` | Confirmed: no `readNotificationPermission` function exists; both call sites independently call `notificationPermission.decide(reminderSettingsStore.hasRequestedNotificationPermission())`. Both existing tests for these two functions (`refreshNotificationPermission picks up a permission granted...`, `recordNotificationPermissionRequested writes the flag and moves...`) still pass unmodified. | ✅ Behaviour-neutral, as claimed |
| `TooManyFunctions` ceiling itself | 10 functions stays under the default threshold | `config/detekt/detekt.yml` has no `TooManyFunctions` override (`rg -n "TooManyFunctions"` → no match), so detekt's built-in default applies. `:app:detektMain` (forced fresh) reports 0 findings for the whole module, including `TodayViewModel.kt`. | ✅ Confirmed — detekt is silent on this class |

### Correctness (Static Evidence, Slice A scope)
| Requirement / Decision | Status | Notes |
|------------|--------|-------|
| Decision 1 — one `TodayDate` state object, projected `viewed`, unconditional writers | ✅ Implemented | Traced in code (`TodayDate`/`DateView`, `:94-115`); both writers unconditional (`:186`, `:229`) |
| Decision 1, invariant 4 — `navigated != null` ⇔ past day | ✅ Implemented | `isPastDay` derived exactly as `navigated != null` (`:114`); test 2.12 pins both directions |
| Decision 2 — in-place navigation, no route change | ✅ Implemented | `ConstanzaRoute.Today` untouched; navigation state lives only in `TodayViewModel` |
| Decision 3 — clear presented-state on navigate, not on live-edge rollover | ✅ Implemented | Three gestures clear both sets before writing; `init`/`refreshDate` never touch them; test 2.14 pins the live-edge non-clearing case explicitly |
| `answer()`/`refreshDate()`/`requestChange()`/`toggleExpanded()` signatures unchanged | ✅ Confirmed | All four keep their exact prior signatures |
| `EntryWriter`, `TodayModel`, `OccurrenceResolver`, `CurrentDateSource` not modified | ✅ Confirmed | `git status` shows only `TodayViewModel.kt` and `TodayViewModelTest.kt` under `app/`; none of those four files appear in the diff |

### Coherence (Design)
| Decision | Followed? | Notes |
|----------|-----------|-------|
| Decision 1 (state shape) | ✅ Yes | Exact `TodayDate`/`DateView` shape from design.md, same field names |
| Decision 2 (no route change) | ✅ Yes | — |
| Decision 3 (clear-on-navigate) | ✅ Yes | Including the "deliberately narrow" live-edge-rollover exception |
| Decisions 4–5 (date bar, past-day empty state) | N/A — Slice B | Correctly not attempted in this PR; `isPastDay` field (the Decision 5 prerequisite) is already in `TodayUiState`, ready for Slice B to consume |

### Issues Found

**CRITICAL**: None.

**WARNING**:
1. `tasks.md`'s "Hard constraints honored" section and `design.md`'s Testing Strategy section both
   state the delta spec has "22 scenarios"; the actual file
   (`openspec/changes/today-past-day-correction/specs/habit-entry-tracking/spec.md`) has **24**
   (`rg -c '^#### Scenario:'`). This is a pre-existing documentation count from an earlier phase
   (design/tasks), not something Slice A's apply run introduced or can fix by editing code — flagging
   for correction before the change is archived, per this skill's rule to count actual headings
   rather than trust a stated total.

**SUGGESTION**:
1. Scenarios 1, 2, and 10–15 were not independently re-verified in this Slice-A-scoped pass because
   their backing files (`EntryWriter`, `OccurrenceResolver`, day-rollup/per-slot UI tests) are outside
   this PR's file set. They are presumed intact (untouched by the diff, and the full JVM suite —
   including their tests — still runs 273/273 green), but a full-spec `sdd-verify` pass covering
   those files' own dedicated tests should still run once before the final archive of this change.

### Verdict
**FAIL (whole-change spec envelope) — but Slice A itself is PASS WITH WARNINGS and merge-ready.**

The strict `requirements: 1/3` / `scenarios: 15/24` envelope above is scored against the FULL delta
spec's totals, and the validator correctly reports `fail` for that envelope: 9 of 24 scenarios are
not yet fully complete against the whole change. This is the accurate, honest state of the OVERALL
change, and it is exactly what a mid-flight, chained-PR verification is supposed to show — it is
**not** a defect in Slice A, and it does not block PR 1.

Read separately, for the scope this report was actually asked to judge (Slice A / PR 1 only):

- All 26 Slice A tasks (Phase 1, Phase 2, applicable Phase 7) are complete and correct.
- Both clock-driven writers are genuinely unconditional (`:186`, `:229`), traced in code, not taken
  on trust.
- The forward re-attachment edge genuinely sets `navigated = null` (never pins it to `clock`), and is
  pinned by a dedicated test (`forward navigation onto today re-attaches, so a later tick still moves
  the view`).
- Both design-flagged coverage gaps (unrestricted editing, streak interaction) are genuinely closed —
  the streak claim was independently re-verified against `origin/main` (empty diff) rather than taken
  on trust.
- The task 2.14 rollover assertion genuinely exists and asserts exactly what it claims.
- Both reported design deviations (`clearPresentedSlotState` not extracted, `readNotificationPermission`
  inlined) are genuinely behaviour-neutral.
- 273/273 JVM tests pass (fresh, forced re-run, read from JUnit XML), 0 detekt findings on
  `:domain`/`:app` (fresh, forced re-run), `:app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL
  (fresh, forced re-run).
- 0 CRITICAL issues. 1 WARNING (a pre-existing scenario-count documentation mismatch in
  `tasks.md`/`design.md`, "22" vs. the actual 24 — does not block this PR). 1 SUGGESTION (re-verify
  the 8 out-of-scope pre-existing scenarios' own dedicated tests before archiving the full change).

**Recommendation**: proceed with PR 1 (Slice A). The `fail` verdict at the whole-spec level is
expected to persist until a follow-up `sdd-verify` run after Slice B (PR 2) lands; it must not be
read as a Slice A defect.
