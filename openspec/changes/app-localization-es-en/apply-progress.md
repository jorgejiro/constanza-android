# Apply Progress: English/Spanish localization with an in-app language override

## Scope of this batch

Phase 1 (tasks 1.1-1.5) and Phase 2 (tasks 2.1-2.10) only — PR 1 (translation resources, format
integrity gate, and the locale mechanism). Phase 3-6 are explicitly out of scope for this batch.

## Mode

Standard (strict TDD does not apply — `strict_tdd_scope` is `:domain only`, and this PR is
entirely in `:app`). Tests were written alongside the code per task, not after.

## Completed Tasks

- [x] 1.1 `app/src/main/res/values-es/strings.xml` — full es-ES informal-tú translation, 119
      translatable strings + the one `<plurals>` block (`app_name` omitted, `translatable="false"`
      in the base file). Spain lexicon ("Ajustes", "Aplazar", "Eliminar").
- [x] 1.2 `app/src/main/res/values/strings.xml` — `app_name` gained `translatable="false"`;
      `settings_snooze_title` renamed to `settings_title`; added
      `settings_snooze_section_title`, `settings_language_section_title`,
      `settings_language_system_default`, `settings_language_english`, `settings_language_spanish`,
      and 4 `portability_import_error_*` keys (one per future `ImportFailure` variant — Phase 5 is
      out of scope, but the keys are needed now for key parity with `values-es/`).
- [x] 1.3 `app/src/main/res/xml/locales_config.xml` created (`<locale-config>` with `en`/`es`).
- [x] 1.4 `AndroidManifest.xml` — `android:localeConfig="@xml/locales_config"` +
      `tools:targetApi="33"` on `<application>`.
- [x] 1.5 `StringResourceParityTest.kt` — D9's load-bearing gate. 5 tests, all green (see
      Evidence below).
- [x] 2.1 `localization/AppLanguage.kt` — `SystemDefault`/`English`/`Spanish` enum + `fromTag`.
- [x] 2.2 `localization/AppLocale.kt` — `localizedContext` + `ProvideAppLocale` composable,
      exactly per design's Interfaces/Contracts snippet (Findings A/B honored).
- [x] 2.3 `reminding/ReminderSettingsStore.kt` — `LANGUAGE_TAG_KEY`, `languageTag: Flow<String?>`,
      `currentLanguageTag()`, `setLanguageTag(tag)` (removes on `null`, D7).
- [x] 2.4 `localization/AppLocaleController.kt` — `@Singleton`, `current()`, `set()`, `observe()`,
      `localizedApplicationContext()`. The only class referencing `Build.VERSION.SDK_INT >= 33` /
      `LocaleManager` for this feature (D1).
- [x] 2.5 `reminding/NotificationPoster.kt` — injected `AppLocaleController`; `postReminder` is now
      `suspend`; `canPost()` keeps its non-suspend public signature and delegates to a private
      `canPost(ctx: Context)`; channel, question, and all three action labels resolve through one
      localized `Context` per post.
- [x] 2.6 Updated every `postReminder` construction/stub site (discovered via
      `compileDebugAndroidTestKotlin`, exactly as the task specifies): `ReminderFireWorkerTest.kt`
      (`coEvery`/`coVerify`), `NotificationPosterTest.kt` (`runTest` + `AppLocaleController` mock),
      `NotificationPosterInstrumentedTest.kt`, `NotificationActionWiringInstrumentedTest.kt`
      (real `AppLocaleController` via the entry-point-backed `ReminderSettingsStore`, mirroring
      `CoreFlowTestFixture`'s own pattern), plus two sites the design's task text did not name but
      the compiler surfaced: `AnswerWorkerTest.kt`, `SnoozeWorkerTest.kt`,
      `TodayViewModelTestFactory.kt` (relaxed `AppLocaleController` mocks — none of those paths ever
      call `postReminder`, only `cancel()`). Also fixed one stale reference the 1.2 rename broke:
      `SnoozeSettingsScreen.kt:45` (`settings_snooze_title` → `settings_title`) — a minimal fix, not
      Phase 4's section/picker wiring.
- [x] 2.7 `localization/AppLanguageTest.kt` — 4 tests, all green.
- [x] 2.8 `localization/AppLocaleInstrumentedTest.kt` — 2/2 tests passed on API 31 and API 37 (see
      Evidence — matrix confirmed after this batch's initial report).
- [x] 2.9 `localization/SpanishColdProcessNotificationInstrumentedTest.kt` — the headline test,
      1/1 passed on API 31 and API 37.
- [x] 2.10 `localization/LanguageOverrideStoreInstrumentedTest.kt` — 3/3 tests passed on API 31 and
      API 37.

## Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `app/src/main/res/values-es/strings.xml` | Created | es-ES translation, 119 strings + 1 plurals |
| `app/src/main/res/values/strings.xml` | Modified | `app_name` non-translatable; renamed + 9 new keys |
| `app/src/main/res/xml/locales_config.xml` | Created | `<locale-config>` en/es |
| `app/src/main/AndroidManifest.xml` | Modified | `android:localeConfig` + `tools:targetApi="33"` |
| `app/build.gradle.kts` | Modified | Two `systemProperty` entries so `StringResourceParityTest` reads both `strings.xml` files by explicit path, matching this file's own existing convention for `ControlStrokeCallSiteTest`/`ViewModelTeardownCallSiteTest` |
| `app/src/test/kotlin/.../localization/StringResourceParityTest.kt` | Created | D9's load-bearing gate, 5 tests |
| `app/src/test/kotlin/.../localization/AppLanguageTest.kt` | Created | 4 tests |
| `app/src/main/kotlin/.../localization/AppLanguage.kt` | Created | enum + `fromTag` |
| `app/src/main/kotlin/.../localization/AppLocale.kt` | Created | `localizedContext` + `ProvideAppLocale` |
| `app/src/main/kotlin/.../localization/AppLocaleController.kt` | Created | API-33 split, single source of truth |
| `app/src/main/kotlin/.../reminding/ReminderSettingsStore.kt` | Modified | language-tag key, flow, read/write |
| `app/src/main/kotlin/.../reminding/NotificationPoster.kt` | Modified | suspend `postReminder`, per-post localized `Context` |
| `app/src/main/kotlin/.../reminding/SnoozeSettingsScreen.kt` | Modified | stale string-key reference fixed (minimal; Phase 4 owns the rest) |
| `app/src/androidTest/kotlin/.../scheduling/ReminderFireWorkerTest.kt` | Modified | `coEvery`/`coVerify` for the new suspend signature |
| `app/src/test/kotlin/.../reminding/NotificationPosterTest.kt` | Modified | `AppLocaleController` mock + `runTest` |
| `app/src/androidTest/kotlin/.../reminding/NotificationPosterInstrumentedTest.kt` | Modified | real `AppLocaleController`, `runBlocking` |
| `app/src/androidTest/kotlin/.../reminding/NotificationActionWiringInstrumentedTest.kt` | Modified | real `AppLocaleController`, `runBlocking` |
| `app/src/androidTest/kotlin/.../reminding/AnswerWorkerTest.kt` | Modified | relaxed `AppLocaleController` mock (compiler-surfaced) |
| `app/src/androidTest/kotlin/.../reminding/SnoozeWorkerTest.kt` | Modified | relaxed `AppLocaleController` mock (compiler-surfaced) |
| `app/src/androidTest/kotlin/.../tracking/TodayViewModelTestFactory.kt` | Modified | relaxed `AppLocaleController` mock (compiler-surfaced) |
| `app/src/androidTest/kotlin/.../localization/AppLocaleInstrumentedTest.kt` | Created | 2 instrumented tests |
| `app/src/androidTest/kotlin/.../localization/SpanishColdProcessNotificationInstrumentedTest.kt` | Created | headline instrumented test |
| `app/src/androidTest/kotlin/.../localization/LanguageOverrideStoreInstrumentedTest.kt` | Created | 3 instrumented tests |

## Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `./gradlew :app:testDebugUnitTest --rerun-tasks` — full suite 205/205 passed, 0 failures, 0 errors. `StringResourceParityTest` 5/5, `AppLanguageTest` 4/4, `NotificationPosterTest` 6/6 |
| Runtime harness command/scenario and exact result | `./gradlew :app:emulatorMatrixGroupDebugAndroidTest` (API 31 + API 37) — see note below; this is the real instrumented coverage for 2.8/2.9/2.10 and the `NotificationPoster` androidTest changes |
| Rollback boundary | Delete `res/values-es/strings.xml`, `res/xml/locales_config.xml`, revert the manifest attribute, and delete `localization/`; revert `ReminderSettingsStore.kt`/`NotificationPoster.kt` and the 6 test-site edits. On API 33+ also run `setApplicationLocales(LocaleList.getEmptyLocaleList())` once — a `LocaleManager` override is a system setting that survives a code revert (design.md's Rollback Note) |

## Gate Results (this batch)

1. `JAVA_HOME=.../jbr/Contents/Home ./gradlew :app:testDebugUnitTest --rerun-tasks` — **PASS**, 205/205.
2. `JAVA_HOME=... ./gradlew :app:detektMain` — **PASS**, 0 issues (one `MaxLineLength` violation in
   `NotificationPoster.kt`'s new `action(...)` signature was found and fixed by wrapping the
   parameter list).
3. `JAVA_HOME=... ./gradlew :app:compileDebugAndroidTestKotlin` — **PASS**. This surfaced 3 call
   sites the task text did not name (`AnswerWorkerTest.kt`, `SnoozeWorkerTest.kt`,
   `TodayViewModelTestFactory.kt`), exactly as task 2.6 anticipated; all fixed.
4. `JAVA_HOME=... ./gradlew :app:emulatorMatrixGroupDebugAndroidTest` — **PASS.**
   `BUILD SUCCESSFUL in 8m 11s`. 123 tests on API 31 (2 skipped — pre-existing onboarding-permission
   scenarios not applicable to that leg, 0 failed), 122 tests on API 37 (1 skipped, same reason,
   0 failed). Confirmed from the JUnit XML reports
   (`app/build/outputs/androidTest-results/managedDevice/debug/api{31,37}/TEST-api{31,37}-_app-.xml`),
   not just the console summary:
   - `AppLocaleInstrumentedTest`: 2/2 passed, both legs.
   - `LanguageOverrideStoreInstrumentedTest`: 3/3 passed, both legs.
   - `SpanishColdProcessNotificationInstrumentedTest` (the headline test): 1/1 passed, both legs.
   - Every touched regression suite — `NotificationPosterInstrumentedTest` (2/2),
     `NotificationActionWiringInstrumentedTest` (2/2), `AnswerWorkerTest` (2/2), `SnoozeWorkerTest`
     (3/3) — passed clean on both legs with the new `AppLocaleController`-injecting
     `NotificationPoster` constructor and the suspend `postReminder` signature.
   - No `testsuite` anywhere in either XML report has a non-zero `failures` or `errors` count.

## Review Workload / PR Boundary

- Delivery strategy: `auto-chain`; chain strategy: `stacked-to-main` (PR 1 targets `main`).
- Measured authored change for this batch (additions + deletions, new files counted as full
  additions): **~962 lines** — `git diff --stat` on modified tracked files (153 insertions, 40
  deletions) plus 769 lines across 10 new files (`values-es/strings.xml` 180,
  `StringResourceParityTest.kt` 145, `SpanishColdProcessNotificationInstrumentedTest.kt` 129,
  `LanguageOverrideStoreInstrumentedTest.kt` 94, `AppLocaleController.kt` 69,
  `AppLocale.kt` 57, `AppLanguageTest.kt` 33, `AppLocaleInstrumentedTest.kt` 33,
  `AppLanguage.kt` 21, `locales_config.xml` 8).
- This exceeds the session's cached `review_budget_lines: 800`. The tasks artifact's own forecast
  (~650-700 for this slice) predates the actual measured size; per the apply skill's guard, the
  slice was implemented honestly rather than compressed to hit a number. It could not be split
  further as one cohesive, independently-revertible work unit: the `suspend postReminder` signature
  change is one atomic edit that ripples through 6 call/stub sites by construction (task 2.6), and
  `StringResourceParityTest` requires both `strings.xml` files to exist simultaneously to have
  anything to gate. **Recommending `size:exception` for this PR 1 slice** — flagging to the
  orchestrator/user rather than deciding unilaterally.
- Rollback boundary for this whole batch: see the Work Unit Evidence table above.

## Deviations from Design

1. **`app/build.gradle.kts` gained two `systemProperty` entries** so `StringResourceParityTest` can
   read both `strings.xml` files by an explicit, fail-loud path. This is not in design's File
   Changes table, but it follows that same file's own pre-existing, documented convention
   (`constanza.mainSourceDir`/`constanza.androidTestSourceDir` for `ControlStrokeCallSiteTest`/
   `ViewModelTeardownCallSiteTest`) rather than inferring a working directory. Minimal, mechanical,
   and load-bearing for 1.5 to work at all.
2. **`SnoozeSettingsScreen.kt:45`** needed a one-line fix (`settings_snooze_title` →
   `settings_title`) purely because task 1.2's rename broke compilation. This is NOT Phase 4's
   section-header/`LanguageSection` wiring (task 4.3) — that remains untouched and out of scope.
3. **Three additional test call sites** needed a relaxed `AppLocaleController` mock beyond the two
   task 2.6 named explicitly: `AnswerWorkerTest.kt`, `SnoozeWorkerTest.kt`,
   `TodayViewModelTestFactory.kt`. The task text itself anticipated this ("Run
   `./gradlew :app:compileDebugAndroidTestKotlin` to surface any remaining call sites... and update
   them to compile") — not a deviation from design, but recorded since design's own Risks table only
   named 6 files and the actual total (including production `NotificationPoster.kt` and these 3) is
   10.
4. No other deviations — implementation matches design.md's Interfaces/Contracts snippet,
   Findings A/B, and D1-D9 exactly.

## Issues Found

None beyond the compile-time call-site discoveries already listed above (expected and resolved).

## Remaining Tasks (not in this batch's scope)

- [ ] Phase 3: Compose Root Wiring & Locale-Sensitive Formatting (PR 2)
- [ ] Phase 4: Language Picker UI (PR 2)
- [ ] Phase 5: Portability Typed-Error Refactor (PR 3)
- [ ] Phase 6: Verification (spans PR 1-3)

## Status

17/17 assigned tasks (Phase 1 + Phase 2) complete with passing evidence at every gate: unit tests
205/205, detekt 0 issues, `compileDebugAndroidTestKotlin` clean, and the full device-free emulator
matrix (API 31 + API 37) green with 0 failures/errors on either leg — including all 6 new
localization instrumented tests and every touched regression suite. Ready for `sdd-verify`.
Recommend the orchestrator/user weigh in on the `size:exception` flag above before merge (the
measured ~962 authored lines exceed this session's 800-line budget; see Review Workload / PR
Boundary).
