# Verify Report: app-localization-es-en

**Verified against**: `main` @ `549f7db` (merge of PR #72, which landed the three slices that had
merged into intermediate branches instead of `main`).

**Verdict**: pass with findings. All 11 requirements have an implementation. Four coverage gaps are
recorded below and none of them blocks archive; each is a test that does not exist, not a behaviour
that is wrong.

## Evidence

Re-run on `main` itself, not inherited from the individual PR branches:

| Gate | Result |
|---|---|
| `:app:testDebugUnitTest --rerun-tasks` | **209 tests, 0 failures, 0 errors, 0 skipped** |
| `:app:detektMain` | 0 issues |
| `:app:compileDebugAndroidTestKotlin` | clean |
| `:app:emulatorMatrixGroupDebugAndroidTest --rerun-tasks` | **84/84 tasks executed**; API 31 126 tests / 2 skips, API 37 126 tests / 6 skips, **0 failures, 0 errors on both legs** |
| Release APK | `assembleRelease` with R8 and resource shrinking; `lintVitalRelease` passed |

The matrix figures are from the tree now at `549f7db`: they were produced on
`fix/land-app-localization-remainder`, whose `git diff --stat origin/main HEAD` was empty at the
point that branch was pushed and merged unchanged.

Instrumented results were read from
`app/build/outputs/androidTest-results/managedDevice/debug/api{31,37}/TEST-api{31,37}-_app-.xml`,
not from the console. This matters: during this change a matrix run reported `BUILD SUCCESSFUL`
while both emulator tasks were `UP-TO-DATE` and **no instrumented test executed at all**. A bare
success line is not evidence; the executed-task count and the XML are.

**Release-artifact check.** R8 and resource shrinking run on release but not on debug, so a green
debug matrix says nothing about whether the Spanish resources survive packaging. `aapt2 dump
resources` on the signed APK reports **151 resources carrying an `(es)` variant**, including
`string/settings_title → "Ajustes"` and `string/settings_language_section_title → "Idioma"`. The
translation is present in the shipped binary, not only in source.

## Requirement coverage

### `app-localization` (new capability, 9 requirements)

| Requirement | Implementation | Test |
|---|---|---|
| Device-Locale Resolution | `res/values-es/`; Android resource qualifiers, zero code | `StringResourceParityTest` (parity only) — see gap 1 |
| Supported Language Set And Universal Fallback | base `values/` serves as English **and** fallback; no `values-en/` exists | see gap 1 |
| First-Install Resolution Needs No User Action | no stored state; `AppLanguage.SystemDefault.tag` is `null` | see gap 1 |
| Three-State Language Override | `AppLanguage`, `AppLocaleController.set` | `LanguageOverrideStoreInstrumentedTest` (3), `AppLanguageTest` |
| Override Persistence Across Process Death | DataStore below 33, `LocaleManager` on 33+ | `LanguageOverrideStoreInstrumentedTest` — survives store re-creation |
| API 33+ System-Settings Parity, In-App Picker Only Below | `res/xml/locales_config.xml`, `android:localeConfig`, `AppLocaleController` | `AppLocaleInstrumentedTest` — see gap 2 |
| Every User-Visible String Renders In The Resolved Language | `ProvideAppLocale` at the Compose root; `NotificationPoster` wraps its Application context | `LanguageOverrideComposeTest` (5, API 31/32), `SpanishColdProcessNotificationInstrumentedTest` |
| Format-Argument And Plural Integrity Under Translation | translation authored against the English source | `StringResourceParityTest` (static, multisets) **plus** the runtime `pluralStringResource` case in `LanguageOverrideComposeTest` |
| Locale-Sensitive Formatting Follows The Resolved Language | `ScheduleEditors.kt:238` reads `LocalConfiguration.current.locales[0]`; `TimeOfDayFormat` already did | `TimeOfDayFormatTest` (21), `LanguageOverrideComposeTest.spanishOverrideReachesDayOfWeekNames` |

### Modified requirements

| Requirement | Implementation | Test |
|---|---|---|
| `reminder-response` → Notification Actions | `postReminder` is `suspend` and resolves text through a locale-wrapped Application context | `SpanishColdProcessNotificationInstrumentedTest`, both legs |
| `data-portability` → Import | `ImportFailure` sealed interface; wording chosen at the Compose layer | `BackupImporterTest` (10) — see gap 4 |

## Findings

All four are missing tests, not defects. None was hidden at any point: each is stated in the PR
that introduced it.

**1. No test drives a Spanish device locale with no override.** Requirements 1-3 are satisfied by
Android's own resource-qualifier resolution and need no code, which is exactly why nothing exercises
them: the emulator matrix runs both legs in English, and `AppLocaleInstrumentedTest` exercises the
*override* wrapping path, not the base path. Those are genuinely different code paths.
Closing it means a locale-configured `Context` asserting that `values-es/` is selected without any
stored override.

**2. API 33+ system-Settings parity is verified in one direction only.** `AppLocaleController`
reads and writes `LocaleManager`, and `LanguageOverrideStoreInstrumentedTest` covers that on the
33+ leg. What no test does is change the language from the *system* side and assert the in-app
picker reflects it. `LifecycleStartEffect { refresh() }` is the mechanism; it is unexercised.

**3. The cold-process notification test does not achieve literal process death.**
`SpanishColdProcessNotificationInstrumentedTest` proves no Activity was ever created and that the
poster resolved Spanish from the persisted tag. Real post-process-death cold start stays a manual
`adb` check under `testing.instrumented.device_free_matrix.limits`. Stated in the test's own KDoc.

**4. `importFailureMessage` has no test.** The compiler enforces that the `when` over
`ImportFailure` is exhaustive, so a new case cannot be added without copy. Nothing checks that each
of the four branches selects the right string. Covering it means making two private composables
`internal`.

## Deviations from the plan, all deliberate

- **Task 3.3 asked for an assertion on the Spanish `p. m.` marker; it was not added.**
  `TimeOfDayFormatTest` already covers the day-period marker and asserts it loosely on purpose — its
  KDoc explains that pinning the spelling would make the test a statement about the JDK's bundled
  CLDR data. Added instead: an explicit hour cycle must win over the locale's own convention, since
  Spain writes 24-hour by convention and deriving the cycle from the language would silently
  override a user who chose 12-hour on their device.
- **`LanguageSection` was split into container and presentational.** The Compose test needs no
  Hilt-enabled Activity, matching this codebase's existing split.
- **`LanguageOverrideComposeTest` is API 31/32 only.** `ProvideAppLocale` is a deliberate
  pass-through on 33+, where the platform carries the override. An earlier revision asserted both
  legs on the assumption that the same conclusions held there for another reason; the API 37 leg
  failed and disproved it. The correction is recorded in the test's own KDoc rather than quietly
  applied.
- **The `lint { }` block carries `abortOnError = true` and no check IDs.** The candidates were never
  confirmed against this AGP version, and `StringResourceParityTest` carries the whole guarantee, so
  an unwritten ID costs nothing. Asserting an unverified one would have cost credibility.

## Process notes worth carrying forward

- `design.md` claimed `StringResourceParityTest` covered all four plural-integrity scenarios. It
  parses XML and cannot execute CLDR plural selection. The gap was found by an earlier verify pass
  and closed with a runtime `pluralStringResource` test. A document can describe its own test
  accurately and still overclaim which scenarios that test satisfies.
- `ScheduleEditors.kt`'s KDoc argued that `LocalLocale` had been chosen *precisely* so a per-app
  override would be honoured. The reasoning was sound and the API does not deliver it. The KDoc was
  rewritten in the same commit as the line change, and `spanishOverrideReachesDayOfWeekNames` fails
  if anyone reverts the line while following the old comment.
