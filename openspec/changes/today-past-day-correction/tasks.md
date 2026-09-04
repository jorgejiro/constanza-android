# Tasks: Today Gains Date Navigation, So a Past Day Can Finally Be Corrected

## Review Workload Forecast

> `sdd-tasks` own forecast, not copied from `design.md`'s 462–651. It adds four items the
> design named as validation gaps with no line estimate: the missing rollover assertion, the
> unrestricted-editing test vehicle, the streak-interaction domain regression test, and the
> `TodayAdaptiveComposeTest`/`CoreFlowE2ETest` audits. Session budget is **800 lines**
> (`review_budget_lines: 800`), not the skill's stock 400.

| File | Action | Est. lines | Slice |
|---|---|---|---|
| `tracking/TodayViewModel.kt` | Modify | 70–90 | A |
| `test/.../tracking/TodayViewModelTest.kt` | Modify | 145–195 | A |
| `domain/src/test/.../StreakCalculatorTest.kt` | Modify | 20–35 | A |
| `tracking/TodayDateBar.kt` | Create | 70–95 | B |
| `tracking/TodayScreen.kt` | Modify | 35–50 | B |
| `res/values/strings.xml` | Modify | 6–8 | B |
| `res/values-es/strings.xml` | Modify | 6–8 | B |
| `androidTest/.../tracking/TodayPastDayComposeTest.kt` | Create | 110–160 | B |
| `androidTest/.../tracking/EntryWriteParityTest.kt` | Modify | 40–60 | B |
| `androidTest/.../tracking/TodayAddHabitComposeTest.kt` | Modify (contingent) | 0–15 | B |
| `androidTest/.../tracking/TodayAdaptiveComposeTest.kt` | Modify (contingent) | 0–15 | B |
| `androidTest/.../e2e/CoreFlowE2ETest.kt` | Modify (contingent, audit) | 0–20 | B |
| `openspec/config.yaml` | Modify | 5–15 | B |
| **Total** | | **507–766** | |
| **Slice A subtotal** | | **235–320** | |
| **Slice B subtotal** | | **272–446** | |

**This forecast's top end (766) crosses the ~700 trigger** `sdd-tasks` is asked to flag
plainly. It stays under the 800-line session budget, but with only 34 lines of headroom at
the extreme high end — not the comfortable margin the design's own 462–651 figure had.
**Recommendation: use the pre-declared, currently-unused A/B seam rather than one PR.**
Slice A (`TodayViewModel` + its unit tests + the domain streak regression, no UI, no
`@Suppress`, no strings) is autonomous and JVM-verifiable alone. Slice B depends on Slice A's
public gesture surface (`showPreviousDay`/`showNextDay`/`showToday`, `isPastDay`) landing
first. Do not split Slice A along the ViewModel/test boundary — the coexistence bug design.md
names is only provable with both together.

| Field | Value |
|-------|-------|
| Estimated changed lines | 507–766 |
| 800-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 = Slice A, PR 2 = Slice B |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending — orchestrator must ask the user |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | `TodayViewModel` viewed-date/clock split, three navigation gestures, expansion-set reset, JVM tests, streak regression | PR 1 | `./gradlew :domain:test --tests "*StreakCalculatorTest*" :app:testDebugUnitTest --tests "*TodayViewModelTest*"` | N/A — JVM-only; no screen wiring exists yet to demo on a device | Revert `tracking/TodayViewModel.kt`, `test/.../TodayViewModelTest.kt`, `domain/src/test/.../StreakCalculatorTest.kt`; app behaves identically at the live edge (new methods are unused) |
| 2 | `TodayDateBar`, `TodayScreen` wiring, strings, instrumented tests, `CoreFlowE2ETest`/adaptive audits, config close-out | PR 2 | `./gradlew :app:compileDebugAndroidTestKotlin` | `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` (api31 + api37, device-free) | Revert `tracking/TodayDateBar.kt`, `tracking/TodayScreen.kt`, `res/values*/strings.xml`, the new/extended `androidTest` files, and the `openspec/config.yaml` close-out entry; Slice A stays intact and unused |

## Phase 1: `TodayViewModel` Foundation (Slice A)

- [x] 1.1 In `tracking/TodayViewModel.kt`, replace `observedDate: MutableStateFlow<LocalDate>` (`:89`) with a private `data class TodayDate(val clock: LocalDate, val navigated: LocalDate? = null)` (`viewed = navigated ?: clock`) held in `dateState: MutableStateFlow<TodayDate>`, plus `data class DateView(val date: LocalDate, val isPastDay: Boolean)` derived via `.map { }.distinctUntilChanged()`.
- [x] 1.2 Update the clock-driven writers at `:155` (init timer) and `:192` (`refreshDate()`) to `dateState.update { it.copy(clock = date) }`, unconditional, no guard.
- [x] 1.3 Add `showPreviousDay()` (`navigated = viewed.minusDays(1)`), `showNextDay()` (`navigated = null` when `next >= clock`, else `navigated = next`), `showToday()` (`navigated = null`) to `tracking/TodayViewModel.kt`.
- [x] 1.4 Key `uiState`'s `flatMapLatest` (`:123-151`) on the `dateView` projection; add `isPastDay: Boolean = false` to `TodayUiState`.
- [x] 1.5 In each of the three gestures from 1.3, clear `expandedHabitIds` (`:90`) and `reopenedSlots` (`:95`) before writing the new date, only when the gesture actually changes `viewed`. Do not clear them on a live-edge midnight rollover.
- [x] 1.6 Confirm `answer()`, `refreshDate()`, `requestChange()`, `toggleExpanded()` keep their exact existing signatures; `answer()` still reads `uiState.value.date`.

## Phase 2: `TodayViewModel` + Domain Tests (Slice A)

- [x] 2.1 Extend `test/.../tracking/TodayViewModelTest.kt`'s `FakeCurrentDateSource` (`:82-104`) and `buildViewModel()`'s `entriesByDate` map (do not redesign either) to cover each past date a new test navigates to.
- [x] 2.2 Test: a midnight tick while on a past day does not move `uiState.value.date`.
- [x] 2.3 Test: `refreshDate()` while on a past day does not move it.
- [x] 2.4 Test: `showToday()` after a tick that fired while away lands on the new clock date.
- [x] 2.5 Test: at the live edge, both writers still move the view (`today-midnight-rollover` regression) — already proven by the two pre-existing rollover/resume tests; no new test added.
- [x] 2.6 Test: forward-onto-today re-attaches — a later tick then moves the view, proving `navigated = null`, not pinning.
- [x] 2.7 Test: `showNextDay()` at the live edge is a no-op; no future date is reachable.
- [x] 2.8 Test: N backward steps reach `clock - N` (unbounded).
- [x] 2.9 Test: `answer()` on a past day passes the viewed date to `answerInApp` (`coVerify`).
- [x] 2.10 Test: both expansion sets are empty after a navigation that changes `viewed`.
- [x] 2.11 Test: a tick while navigated away neither clears the expansion sets nor re-subscribes Room (`observeByDate` called once for that date).
- [x] 2.12 Test: `isPastDay` is true off the live edge, false at it.
- [x] 2.13 Test: a past occurrence absent from `observeUnresolved()` builds a slot with `occurrenceId == null` (proves `TodayModel.toTodaySlot` unchanged).
- [x] 2.14 Add the missing assertion to the existing rollover test at `test/.../tracking/TodayViewModelTest.kt:270`: after a midnight rollover **at the live edge**, `expandedHabitIds` and `reopenedSlots` are unchanged — Decision 3's justification currently rests on this being untested.
- [x] 2.15 Add a JVM test: from a past date, walk a slot `COMPLETED → MISSED → SKIPPED` and back through the cycle a second time via `answer()`; each transition is accepted with no restriction (the uncovered "any past slot freely re-editable" scenario).
- [x] 2.16 Add a test to `domain/src/test/kotlin/com/jjrapps/constanza/domain/StreakCalculatorTest.kt`: a day recorded `MISSED`, then corrected to `COMPLETED` via an ordinary `Entry` upsert, produces an unbroken streak through that date when `StreakCalculator` runs after the correction. Pure regression — no `:domain` production change. **Already present and unmodified** (`streak recomputed after a late correction shows no break`, verified byte-identical against `origin/main`) — no new test needed.

## Phase 3: `TodayScreen` UI (Slice B)

- [ ] 3.1 Create `tracking/TodayDateBar.kt`: `TodayDateBar` composable — previous/next `IconButton`s (`Icons.AutoMirrored.Filled.KeyboardArrowLeft`/`KeyboardArrowRight`, absent at the live edge for "next"), a centred `Text` with `Modifier.weight(1f)`, and a `TextButton` labelled `today_back_to_today` present only off the live edge. Fall back to text-label buttons if either icon is unavailable at implementation time (per `DataPortabilityScreen.kt:31-32`'s established rule).
- [ ] 3.2 In the same file, add `TodayPastDayEmptyState()` — text-only, `today_empty_past`, no button.
- [ ] 3.3 Add a `DateNavActions` holder in `tracking/TodayScreen.kt`, in the exact shape of `SlotActions` (`:129-143`), bundling `onPreviousDay`/`onNextDay`/`onToday`. No new `@Suppress`.
- [ ] 3.4 Hoist `TodayDateBar` above the `LazyColumn` in `TodayContent`'s non-empty branch (never as the list's first item) and above `TodayPermissionBanners` in both branches; `TopAppBar` stays untouched.
- [ ] 3.5 Update `TodayContent` (`:144-151`) to accept `DateNavActions`, render `TodayPastDayEmptyState()` instead of `TodayEmptyState(onAddHabit)` when `state.isPastDay && state.rows.isEmpty()`, and omit `TrailingAddHabitAction(onAddHabit)` from the non-empty branch when `state.isPastDay`.
- [ ] 3.6 Wire `showPreviousDay()`/`showNextDay()`/`showToday()` from `TodayScreen` into `DateNavActions`; format the date label with a locally-`remember`ed `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(...)` keyed on `LocalConfiguration`.

## Phase 4: Strings (Slice B)

- [ ] 4.1 Add `today_previous_day`, `today_next_day`, `today_back_to_today`, `today_empty_past` to `res/values/strings.xml`.
- [ ] 4.2 Add the same four keys, translated into neutral Spanish, to `res/values-es/strings.xml`.

## Phase 5: Instrumented Tests + Audits (Slice B)

- [ ] 5.1 Create `androidTest/.../tracking/TodayPastDayComposeTest.kt` via `TodayViewModelTestFactory.kt` (real `SelfReschedulingCurrentDateSource`) and `TodayScreenWaits.kt` (no per-class `waitUntil`): navigate back, a force-resolved `MISSED` slot shows Missed + Change; Change → Yes → Done.
- [ ] 5.2 Same file: add-habit affordance absent on a past day, present again after tapping `Today`.
- [ ] 5.3 Same file: an empty past day shows `today_empty_past` and no button.
- [ ] 5.4 Same file: unrestricted re-editing — from a past day, drive a slot through `COMPLETED → MISSED → SKIPPED` twice via the Change control; each edit lands.
- [ ] 5.5 Extend `androidTest/.../tracking/EntryWriteParityTest.kt`: `answerInApp(habitId, pastDate, slotId, COMPLETED, occurrenceId = null)` upserts on the past date and does not resurrect the `RESOLVED` occurrence.
- [ ] 5.6 Audit `app/src/androidTest/kotlin/com/jjrapps/constanza/e2e/CoreFlowE2ETest.kt` for date-label and add-habit-position assumptions the new date bar could break; update any broken assertion in the same file.
- [ ] 5.7 Re-run `androidTest/.../tracking/TodayAddHabitComposeTest.kt` and `androidTest/.../tracking/TodayAdaptiveComposeTest.kt` unmodified against the new date bar; if a geometry or clipping assertion fails because of the bar's added vertical extent, fix that assertion in the same file (contingent — this is why both carry a non-zero estimate above).

## Phase 6: Config Close-out (Slice B)

- [ ] 6.1 In `openspec/config.yaml`, close carried-forward item `no-in-app-route-to-edit-a-past-day`: set `status: resolved` and add a `resolution:` field recording that Today gained in-place backward-unbounded date navigation (this change), with unrestricted past-slot editing and the add-habit affordance hidden on a past day.

## Phase 7: Verification

- [x] 7.1 `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` before any Gradle call below. **(Slice A, run)**
- [x] 7.2 `./gradlew :domain:test :app:testDebugUnitTest` — all Phase 2/2.16 tests green. **(Slice A)** 221/221 `:app:testDebugUnitTest` (26/26 in `TodayViewModelTest`), 52/52 `:domain:test` (10/10 in `StreakCalculatorTest`) — 0 failures, 0 errors.
- [x] 7.3 `./gradlew :domain:detektMain :app:detektMain` — **(Slice A subset only — the `TodayContent`/date-format checks are Slice B)** BUILD SUCCESSFUL, 0 issues on both modules after inlining `readNotificationPermission` to keep `TodayViewModel` at 10 functions under detekt's default `TooManyFunctions` ceiling of 11.
- [x] 7.4 `./gradlew :app:compileDebugAndroidTestKotlin` — instrumented build compiles as its own step, independent of 7.2. **(Slice A)** BUILD SUCCESSFUL.
- [ ] 7.5 `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` — full matrix green on API 31 + API 37, device-free. **Slice B — not run in this slice.**
