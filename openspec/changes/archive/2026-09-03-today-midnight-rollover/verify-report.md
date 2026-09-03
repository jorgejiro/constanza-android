# Verification Report — today-midnight-rollover

**Change**: today-midnight-rollover
**Mode**: full spec-driven verification (proposal/design/spec/tasks all present)
**Tasks**: 19/19 complete (task 4.3 matrix regression confirmed done post-snapshot, tasks.md updated)

## Completeness

All 19 tasks checked `[x]` in tasks.md and match code state 1:1 (verified by reading every changed file, not trusting the checkboxes).

## Build/Test Evidence (independently re-run, not assumed from apply-progress)

Command: `JAVA_HOME=<Android Studio JBR> ./gradlew :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:detektMain --console=plain`

Result: BUILD SUCCESSFUL, exit 0, all 42 tasks UP-TO-DATE (proves the fixture compile-break fix is stable, no drift since).

- Unit tests: 196 total, 0 failures, 0 errors (aggregated from all `testDebugUnitTest` XML reports).
- `MidnightDateSourceTest`: 3 tests (re-anchor-per-emission, late-wakeup-emits-true-date-once, exact-midnight-positive-floor) — all present and green.
- `TodayViewModelTest`: contains the rollover re-subscription test, the answer-attribution test, and the refreshDate resume test — all green.
- `detekt.xml`: empty `<checkstyle>` root, zero findings.
- Emulator matrix (task 4.3, run by orchestrator 2026-09-03, not re-run here — device provisioning is out of this phase's scope): api31 115/0/0/2-skipped (17:12), api37 115/0/0/1-skipped (17:15). Skips are complementary by design (POST_NOTIFICATIONS prompt tests below API 33 vs the "permission screen skipped" test at/below the boundary) — no coverage loss.

## Spec Compliance Matrix

| Requirement / Scenario | Status | Evidence |
|---|---|---|
| In-App Answer Date Attribution — answer before midnight | PASS | `TodayViewModel.answer()` reads `uiState.value.date` (TodayViewModel.kt:180); covered by TodayViewModelTest tests |
| In-App Answer Date Attribution — answer after midnight targets new date | PASS | `answer writes against the currently displayed date, even for a slot captured before midnight rolled over` test: captures a slot pre-rollover, advances date, asserts write goes to TOMORROW only, `coVerify(exactly = 0)` against TODAY |
| Day-Level Rollup — today screen tracks live date, rolls over while displayed | PASS | `observedDate.flatMapLatest` re-subscribes `EntryDao.observeByDate`; `crossing midnight while displayed re-subscribes EntryDao and moves both the rollup and the occurrence filter` test green |
| Day-Level Rollup — resume corrects stale date | PASS | `refreshDate()` (TodayViewModel.kt:191-193) called from `TodayScreen.kt` `ON_RESUME`; `refreshDate corrects a stale observedDate left over from backgrounding` test green |
| Foregrounded timezone travel — accepted gap | PASS (documented, not tested) | Matches spec's scoping scenario text; recorded in `openspec/config.yaml` as `today-foregrounded-timezone-travel`, owner condition matches design.md's Open Questions |
| Day-Level Rollup precedence rules (partial/missed) | Unaffected by this change (pre-existing, still green) | Not part of this change's scope |

## Correctness — specific hard checks

1. **`answer()` reads `uiState.value.date`, not `observedDate.value` or a fresh clock read.** CONFIRMED at TodayViewModel.kt:180 (`val date = uiState.value.date`), with KDoc explicitly naming this as the fix for the data-corruption bug. Both rejected alternatives are absent from the method.
2. **`combine` still has exactly five typed sources; date is the `flatMapLatest` key outside it.** CONFIRMED: `observedDate.flatMapLatest { date -> combine(habitRepository.observeAll(), entryDao.observeByDate(date.toString()), reminderOccurrenceDao.observeUnresolved(), expansionState, permissionBanners) {...} }` — five arguments, ceiling not raised, date not a sixth source.
3. **Midnight tick re-anchors from the clock every tick, not `plusDays(1)`, with a positive delay floor.** CONFIRMED in `CurrentDateSource.kt`: `while (true) { emit(timeProvider.today()); delay(timeProvider.millisUntilNextMidnight().coerceAtLeast(MIN_DELAY_FLOOR_MS)) }` — `today()` and `millisUntilNextMidnight()` both called fresh inside the loop body every iteration; `MIN_DELAY_FLOOR_MS = 1000L` sits on top of the existing zero-clamp. `MidnightDateSourceTest` proves re-anchoring, late-wakeup-emits-true-date-once (no duplicate/missing emission), and the floor gating the next emission at exact midnight.
4. **`TodayModel.kt` unedited; `openspec/specs/` untouched.** CONFIRMED via `git diff main...HEAD` — zero diff on `TodayModel.kt`; zero diff under `openspec/specs/`. Merging the delta into the base spec is archive's job.
5. **`Midnight Transition` requirement (habit-entry-tracking) not referenced/reworded anywhere in this change's actual artifacts beyond explicit non-target mentions.** CONFIRMED — occurrences of the string in this change's own proposal.md/design.md/exploration.md are all explicit "this is NOT touched" statements; the active `openspec/specs/habit-entry-tracking/spec.md` text for that requirement is byte-identical (no diff).
6. **Accepted foregrounded-timezone-travel gap recorded and matches spec's scoping scenario.** CONFIRMED — `openspec/config.yaml` carries `today-foregrounded-timezone-travel` (status: open, severity narrowed to "continuously foregrounded, never backgrounded" — matches spec.md's own scenario text in spirit).

## Post-apply compile-break fix (verified, not just trusted)

`TodayViewModelTestFactory.kt` (androidTest) now wraps the fixture's fake `TimeProvider` in the real `SelfReschedulingCurrentDateSource` (`SelfReschedulingCurrentDateSource(timeProvider)`), with KDoc justifying the choice (rollover itself is proven at the JVM virtual-time layer; the instrumented fixture only needs `today()`'s immediate value, which the real wrapper still produces synchronously through the injected fake clock). `:app:compileDebugAndroidTestKotlin` passes; all three source sets (main, test, androidTest) compile clean. This closes the coverage gap `testDebugUnitTest`/`detektMain` alone left open (neither compiles androidTest).

## Design Coherence

All 5 design.md decisions matched in code: (1) new `CurrentDateSource` port, `TimeProvider` unchanged shape; (2) flatMapLatest-over-date pattern exact; (3) all three consumer sites (`observeByDate`, `TodaySnapshot`, `answer()`) verified; (4) `millisUntilNextMidnight()` moved to `TimeProvider.kt`, KDoc carried verbatim, `WorkScheduler.kt` now imports it; (5) re-anchor invariant + positive floor. Two documented deviations in apply-progress.md (added `today()` sync method, `MIN_DELAY_FLOOR_MS` naming) are both additive/non-breaking and consistent with design's decision 5 prose.

## Open item flagged for archive (not fixed here, per phase boundary)

`openspec/config.yaml`'s pre-existing `carried_forward_open_items` entry `today-never-rolls-over-at-midnight` is exactly the defect this change fixes (title cites the same construction-time-capture bug; origin cites the same root cause this change's design addresses) and still reads `status: open`. This is an **archive-phase obligation**: add a `resolution:` field (or equivalent) closing it once this change lands. Not actioned here per assigned scope and per the "do not fix" instruction.

## Issues

**CRITICAL**: None.

**WARNING**: None.

**SUGGESTION**:
1. Consider whether `today-never-rolls-over-at-midnight`'s closure should be an explicit sdd-archive task rather than an implicit side effect, so it isn't silently dropped.

## Final Verdict: PASS

All 19 tasks complete and code-verified (not just checkbox-trusted). The CRITICAL data-corruption requirement (`In-App Answer Date Attribution`) is proven correct by direct source read and a targeted runtime test that captures a slot before rollover and asserts the write lands on the post-rollover date only. The five-source `combine` ceiling is respected. The midnight timer re-anchors every tick with a positive delay floor, proven under virtual time including a late-wakeup case. No production files outside intended scope were touched; `openspec/specs/` and `TodayModel.kt` are untouched; the `Midnight Transition` requirement is undisturbed. Build/test/detekt suite independently re-run and green: 196/196 unit tests, 0 failures, 0 errors, 0 detekt findings, and all three source sets compile. Ready for archive, with one explicit archive-phase obligation carried forward (closing `today-never-rolls-over-at-midnight`).
