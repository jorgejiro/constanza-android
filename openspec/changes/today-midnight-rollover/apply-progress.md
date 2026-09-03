# Apply Progress: today-midnight-rollover

**Batch**: 1 of 1 (no prior apply-progress existed). All 4 phases attempted.

## Completed Tasks (18/19)

- Phase 1 — `core/time` Foundation: 1.1, 1.2, 1.3, 1.4, 1.5 — all `[x]`
- Phase 2 — `TodayViewModel` Wiring: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7 — all `[x]`
- Phase 3 — Test Fixture Audit + Coverage: 3.1, 3.2, 3.3, 3.4 — all `[x]`
- Phase 4 — Config + Verification: 4.1 `[x]`, 4.2 `[x]`, 4.3 `[ ]` (not run this batch — see below)

## Files Changed

| File | Action | What |
|---|---|---|
| `app/src/main/kotlin/.../core/time/TimeProvider.kt` | Modified | added public `TimeProvider.millisUntilNextMidnight()` extension (moved from WorkScheduler, verbatim KDoc/logic) |
| `app/src/main/kotlin/.../core/time/CurrentDateSource.kt` | Created | `CurrentDateSource` interface (`dates()`, `today()`, `zone()`) + `SelfReschedulingCurrentDateSource` impl; `MIN_DELAY_FLOOR_MS = 1000L` internal floor on top of the existing zero-clamp |
| `app/src/main/kotlin/.../core/di/TimeModule.kt` | Modified | added `@Binds bindCurrentDateSource` |
| `app/src/main/kotlin/.../scheduling/WorkScheduler.kt` | Modified | removed `millisUntilNextMidnight()`, now imports it from `core.time` |
| `app/src/main/kotlin/.../tracking/TodayViewModel.kt` | Modified | replaced `TimeProvider` param with `CurrentDateSource`; added `observedDate` MutableStateFlow seeded from `currentDateSource.today()`, collected from `currentDateSource.dates()` in `init`; wrapped 5-source `combine` in `observedDate.flatMapLatest`; added `date: LocalDate` to `TodayUiState`; added `refreshDate()`; `answer()` now reads `uiState.value.date` instead of a captured `today` val |
| `app/src/main/kotlin/.../tracking/TodayScreen.kt` | Modified | `TodayRoute`'s `ON_RESUME` `DisposableEffect` now calls `viewModel.refreshDate()` first, before the two existing permission refreshes |
| `app/src/test/kotlin/.../scheduling/MidnightAnchorTest.kt` | Modified | import-only fix for the moved extension function |
| `app/src/test/kotlin/.../core/time/MidnightDateSourceTest.kt` | Created | 3 virtual-time tests: re-anchoring per emission, late-wakeup-emits-true-date-once, exact-midnight positive floor |
| `app/src/test/kotlin/.../tracking/TodayViewModelTest.kt` | Modified | `buildViewModel()` fixture audit: `FakeCurrentDateSource` (decoupled `emissions` flow vs synchronous `current` field, so timer-vs-resume paths are independently testable) + `entryDaoStub()` (any()-dispatching over a date→flow map, throws a named-date error instead of an opaque `MockKException`); added 3 new tests (rollover re-subscription + occurrence-filter-follows-date, answer-attributes-to-currently-displayed-date even for a pre-rollover slot reference, `refreshDate()` resume correction) |
| `openspec/config.yaml` | Modified | added `today-foregrounded-timezone-travel` carried-forward open item (task 4.1) |

## Verification (real output, not assumed)

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest :app:detektMain --console=plain
```

Result: **BUILD SUCCESSFUL**, exit 0, no warnings on the final run.

- `MidnightAnchorTest`: tests=5 failures=0 errors=0
- `MidnightDateSourceTest`: tests=3 failures=0 errors=0
- `TodayViewModelTest`: tests=14 failures=0 errors=0 (was 11, +3 new)
- `app/build/reports/detekt/detekt.xml`: empty (zero findings) — no `ForbiddenMethodCall` violation

## Not done this batch

- **4.3** (both emulator matrix legs as regression) was NOT run. The orchestrator's explicit "Verification before you report done" block only listed `testDebugUnitTest` + `detektMain`; running the full Gradle Managed Device matrix was out of scope for this apply call. Left `[ ]` in tasks.md pending an explicit request.

## Deviations from design (both additive, non-breaking)

1. `CurrentDateSource` gained a synchronous `today(): LocalDate` method beyond the `dates()`/`zone()` the tasks list names. Needed because `TodayViewModel` must seed `observedDate` and implement `refreshDate()` without a suspend collection — design.md's decision-2 code sample uses `currentDate.current()` for the same purpose, just under a different name; `today()` matches `TimeProvider`'s existing naming convention instead.
2. `MIN_DELAY_FLOOR_MS` (1000ms) added inside `SelfReschedulingCurrentDateSource`, on top of `millisUntilNextMidnight()`'s existing zero-clamp — required per task 1.3's explicit "applies a positive delay floor... so it never spins" acceptance criterion; not separately named in design.md's decision list but directly implied by decision 5's prose.

## Risk flagged for orchestrator/verify (not acted on — out of assigned scope)

`openspec/config.yaml`'s pre-existing `carried_forward_open_items` entry `today-never-rolls-over-at-midnight` (status: open) describes exactly the defect this change fixes, down to citing `flatMapLatest` over a date flow as the owner condition. This apply batch did not mark it `resolved` because task 4.1 only asked for the NEW `today-foregrounded-timezone-travel` entry — expanding to close a pre-existing item wasn't in the assigned task list. Recommend the orchestrator (or a follow-up) add a `resolution:` field to that item once this change lands, per the file's own "resolve it explicitly... or replace it with a new decision" policy.

## Line count

`git diff --numstat` on tracked files: +312/-68. Plus 2 new untracked files (CurrentDateSource.kt 60 lines, MidnightDateSourceTest.kt ~123 lines) ≈ +182. Total authored ≈ 562 changed lines — over the ~300-380 forecast but under the session's 800-line budget (guard already recorded "Low" risk, "Decision needed before apply: No").
