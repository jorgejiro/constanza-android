# Tasks: English/Spanish localization with an in-app language override

## Review Workload Forecast

Re-estimated from design.md's own File Changes + Testing Strategy tables (not from
proposal.md's earlier ~440-600 figure, which predates three corrective rounds: the mandatory
`ScheduleEditors.kt` fix, `suspend postReminder` rippling through 6 test files, the new
`StringResourceParityTest`, the `lint {}` block, and 4 added spec scenarios).

| Field | Value |
|---|---|
| Estimated changed lines | ~950-1200 (additions + deletions, authored text) |
| 400-line budget risk (contract heuristic) | High |
| 800-line budget risk (this session's cached `review_budget_lines`) | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (mechanism) → PR 2 (Compose wiring + picker UI) → PR 3 (portability refactor) |
| Delivery strategy | auto-chain |
| Chain strategy | pending — not yet collected; this forecast is High, so the orchestrator MUST collect it now |

```text
Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High
```

`res/values-es/strings.xml` is ~115 lines and mechanical/low-risk to review, but it still counts
toward the ceiling — it is not excluded. Each of the three slices below stays independently under
800 lines on its own (~650-700 / ~300-350 / ~85-120), so no `size:exception` is needed once split.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1 | Translation, format-integrity gate, `AppLocaleController`/`AppLocale`/`AppLanguage`, `ReminderSettingsStore` key, `suspend postReminder`, cold-process Spanish notification | PR 1 | `./gradlew :app:testDebugUnitTest --tests "*StringResourceParityTest*" --tests "*AppLanguageTest*" --tests "*NotificationPosterTest*"` | `./gradlew :app:emulatorMatrixGroupDebugAndroidTest --tests "*AppLocaleInstrumentedTest*" --tests "*SpanishColdProcessNotificationInstrumentedTest*" --tests "*LanguageOverrideStoreInstrumentedTest*"`, API 31+37 | Delete `res/values-es/strings.xml`, `res/xml/locales_config.xml`, the manifest attribute, and `localization/`; revert `ReminderSettingsStore.kt`/`NotificationPoster.kt`; on API 33+ also clear a stray `LocaleManager` override via `setApplicationLocales(emptyList)` — it is a system setting that survives a code revert |
| 2 | Compose-root wiring (`ProvideAppLocale` at `MainActivity`/`FirstRunGate`), `ScheduleEditors` day-of-week fix, in-app language picker | PR 2 | `./gradlew :app:testDebugUnitTest --tests "*TimeOfDayFormatTest*"` | `./gradlew :app:emulatorMatrixGroupDebugAndroidTest --tests "*LanguageOverrideComposeTest*"`, API 31+37 | Revert `MainActivity.kt`, `ScheduleEditors.kt`, `localization/LanguageSettingsViewModel.kt`, `localization/LanguageSection.kt`, `SnoozeSettingsScreen.kt`; PR 1's mechanism keeps working headless (System-Settings picker on 33+ and cold-process localization stay intact) |
| 3 | Portability typed-error refactor (`ImportFailure`), localized rejection messages | PR 3 | `./gradlew :app:testDebugUnitTest --tests "*BackupImporterTest*"` | N/A — importer module is Android-free by design (D6); no instrumented coverage needed for this slice | Revert `BackupImporter.kt`, `DataPortabilityViewModel.kt`, `DataPortabilityScreen.kt`, `BackupImporterTest.kt` independently — kept in its own commit per design's Rollback Plan |

## Requirement Traceability

| Requirement | Task(s) |
|---|---|
| Device-Locale Resolution | 1.1 |
| Supported Language Set And Universal Fallback | 1.1 |
| First-Install Resolution Needs No User Action | 1.1 (zero code beyond translation, per proposal.md's Approach) |
| Three-State Language Override | 2.3, 2.4, 2.10, 4.1, 4.2 |
| Override Persistence Across Process Death | 2.3, 2.4, 2.10 |
| API 33+ System-Settings Parity, In-App Picker Only Below | 2.4, 4.2 |
| Every User-Visible String Renders In The Resolved Language | 2.5, 2.9 |
| Format-Argument And Plural Integrity Under Translation | 1.5 |
| Locale-Sensitive Formatting Follows The Resolved Language | 3.2, 3.4 |
| `reminder-response`: Notification Actions (MODIFIED) | 2.5, 2.9 |
| `data-portability`: Import (MODIFIED) | 5.1, 5.2, 5.3, 5.4 |

## Phase 1: Translation Resources & Format Integrity (PR 1)

- [x] 1.1 Create `app/src/main/res/values-es/strings.xml`: full `es-ES` informal-tú translation, 111
      strings + the one `<plurals>` block, Spain lexicon ("Ajustes", "Aplazar"); `app_name` omitted.
      No `values-en/`.
- [x] 1.2 Modify `app/src/main/res/values/strings.xml`: add `translatable="false"` to `app_name`;
      rename `settings_snooze_title` (`:139`) → `settings_title`; add `settings_snooze_section_title`,
      `settings_language_section_title`, `settings_language_system_default`,
      `settings_language_english`, `settings_language_spanish`, and 4 `portability_import_error_*`
      keys (one per `ImportFailure` variant from 5.1).
- [x] 1.3 Create `app/src/main/res/xml/locales_config.xml`: `<locale-config>` with `en` and `es`
      (`res/xml/` does not exist yet).
- [x] 1.4 Modify `AndroidManifest.xml`: `android:localeConfig="@xml/locales_config"` on
      `<application>` (`:9-15`), plus `tools:targetApi="33"` to silence lint `UnusedAttribute` at
      `minSdk 31` (the `tools` namespace is already declared at `:3`).
- [x] 1.5 Create `app/src/test/kotlin/com/jjrapps/constanza/localization/StringResourceParityTest.kt`
      (D9, load-bearing gate for `Format-Argument And Plural Integrity Under Translation`, pure JVM,
      no Android framework): parses both `strings.xml` files and asserts (a) format-specifier
      **multiset** parity per key — a multiset, not a sequence, since Spanish word order may legally
      reorder `%1$s`/`%2$s` at `today_slot_change_a11y` (`:118`) while keeping both indices; (b) key
      parity both directions, modulo `translatable="false"`; (c) `progress_compliance` (`:136`)
      carries a literal `%%` in both files; (d) `habit_delete_dialog_body` (`:29-30`) carries Spanish
      `one` **and** `other`, each with its own `%1$d`.

## Phase 2: Locale Mechanism (PR 1)

- [x] 2.1 Create `app/src/main/kotlin/com/jjrapps/constanza/localization/AppLanguage.kt`:
      `enum class AppLanguage(val tag: String?) { SystemDefault(null), English("en"), Spanish("es") }`
      + `fromTag(String?)`.
- [x] 2.2 Create `app/src/main/kotlin/com/jjrapps/constanza/localization/AppLocale.kt`:
      `fun localizedContext(base: Context, tag: String?): Context` (pass-through when `tag == null`)
      and `@Composable fun ProvideAppLocale(...)` providing **exactly**
      `LocalContext`/`LocalConfiguration` and never `LocalResources` (Finding A) or a locale-list
      local (Finding B — cannot be provided; compose-ui's backing local is private).
- [x] 2.3 Modify `app/src/main/kotlin/com/jjrapps/constanza/reminding/ReminderSettingsStore.kt`:
      `LANGUAGE_TAG_KEY = stringPreferencesKey("language_tag")` in the `internal companion object`
      (`:72-76`); `languageTag: Flow<String?>`, `suspend fun currentLanguageTag(): String?`,
      `suspend fun setLanguageTag(tag: String?)` — `remove()` on `null` (D7, tri-state clear).
- [x] 2.4 Create `app/src/main/kotlin/com/jjrapps/constanza/localization/AppLocaleController.kt`
      (`@Singleton`): `suspend fun current(): AppLanguage`, `suspend fun set(AppLanguage)`,
      `fun observe(): Flow<AppLanguage>`, `suspend fun localizedApplicationContext(): Context` — the
      **only** place `Build.VERSION.SDK_INT >= 33` / `LocaleManager` appears (D1: one source of truth
      per API level, never both).
- [x] 2.5 Modify `app/src/main/kotlin/com/jjrapps/constanza/reminding/NotificationPoster.kt`: inject
      `AppLocaleController`; `postReminder` → `suspend`, resolving a localized `Context` once per post
      via `localizedApplicationContext()` for `ensureChannel()`, the question, and the three action
      labels; `canPost()` (`:94`) keeps its non-suspend public signature, delegating to a private
      `canPost(ctx: Context)`. Call site: `ReminderFireHandler.fire()`
      (`ReminderFireWorker.kt:52`, read-only) inside `doWork()` (`:79-84`, read-only), already
      `suspend`.
- [x] 2.6 Update every `postReminder` construction/stub site for the new suspend signature (6 files
      total per design's Risks table): `app/src/androidTest/kotlin/com/jjrapps/constanza/scheduling/ReminderFireWorkerTest.kt`
      (`:84,90,106,128` — `coEvery`/`coVerify`), `app/src/test/kotlin/com/jjrapps/constanza/reminding/NotificationPosterTest.kt`
      (`:96` moves into `runTest`; the 6 `canPost()` assertions at `:47,55,65,75,83,84` stay as-is).
      Run `./gradlew :app:compileDebugAndroidTestKotlin` to surface any remaining call sites
      (e.g. `NotificationPosterInstrumentedTest.kt`, `NotificationActionWiringInstrumentedTest.kt`)
      and update them to compile.
- [x] 2.7 Create `app/src/test/kotlin/com/jjrapps/constanza/localization/AppLanguageTest.kt` (pure
      unit): `fromTag` round-trip; `SystemDefault.tag == null`.
- [x] 2.8 Create `app/src/androidTest/kotlin/com/jjrapps/constanza/localization/AppLocaleInstrumentedTest.kt`:
      `localizedContext(ctx, "es").getString(R.string.notification_action_yes) == "Sí"`;
      `localizedContext(ctx, null) === ctx`.
- [x] 2.9 Create `app/src/androidTest/kotlin/com/jjrapps/constanza/localization/SpanishColdProcessNotificationInstrumentedTest.kt`
      — the headline test. Seed the override (below 33 via `ReminderSettingsDataStoreEntryPoint`,
      `DataStoreModule.kt:49-53`, read-only; on 33+ via `LocaleManager`), fire the byte-identical
      `ReminderFireReceiver` intent `CoreFlowTestFixture.fireArmedAlarmFor` already builds
      (`CoreFlowTestFixture.kt:163-166`, read-only), with no Activity ever created, and assert the
      posted notification's channel name, body, and three action labels are Spanish. **Honest limit**:
      this proves no Activity was ever created and the poster resolved Spanish from the persisted tag
      — not literal post-process-death cold start, which stays a manual `adb` check under
      `testing.instrumented.device_free_matrix.limits`.
- [x] 2.10 Create `app/src/androidTest/kotlin/com/jjrapps/constanza/localization/LanguageOverrideStoreInstrumentedTest.kt`:
      tri-state — `set(Spanish)` stores `"es"`; `set(SystemDefault)` **removes** the key (below 33) /
      empties `applicationLocales` (33+); survives store re-creation.

## Phase 3: Compose Root Wiring & Locale-Sensitive Formatting (PR 2)

- [ ] 3.1 Modify `app/src/main/kotlin/com/jjrapps/constanza/core/ui/MainActivity.kt`:
      `FirstRunGateViewModel` (`:175-183`) combines the resolved language with `onboardingDone` into
      `StartupState?` (`null` still renders nothing, `:202`); `FirstRunGate` (`:193-213`) wraps its
      non-null branches in `ProvideAppLocale` (D3 — reuses the existing gate, adds no second one).
- [ ] 3.2 Modify `app/src/main/kotlin/com/jjrapps/constanza/habit/ScheduleEditors.kt`: `:228`
      `LocalLocale.current.platformLocale` → `LocalConfiguration.current.locales[0]` (Finding B —
      `LocalLocale` cannot reach a per-app override, its backing local is private to compose-ui);
      remove the now-unused `LocalLocale` import (`:30`); rewrite the KDoc (`:215-221`), which
      currently argues the opposite, with Finding B's evidence, in the same commit as the line change.
- [ ] 3.3 Add assertions to `app/src/test/kotlin/com/jjrapps/constanza/core/ui/TimeOfDayFormatTest.kt`
      using the existing `Locale.forLanguageTag("es-ES")` (`:184`): `es-ES` day-period text (`p. m.`)
      and hour-cycle independence.
- [ ] 3.4 Create `app/src/androidTest/kotlin/com/jjrapps/constanza/localization/LanguageOverrideComposeTest.kt`
      — regression protection for Findings A/B: `ProvideAppLocale(Spanish) { SnoozeSettingsScreen(...) }`
      renders "Ajustes"; a `DayOfWeekPicker` chip renders a Spanish short day name.

## Phase 4: Language Picker UI (PR 2)

- [ ] 4.1 Create `app/src/main/kotlin/com/jjrapps/constanza/localization/LanguageSettingsViewModel.kt`
      (`@HiltViewModel`): `selected: StateFlow<AppLanguage>`, `refresh()`, `select(AppLanguage)`.
- [ ] 4.2 Create `app/src/main/kotlin/com/jjrapps/constanza/localization/LanguageSection.kt`:
      three-option radio section (System default / English / Español, fixed order) +
      `LifecycleStartEffect { refresh() }` — re-reads on every `ON_START` rather than caching (D2),
      mirroring `DataPortabilitySection`.
- [ ] 4.3 Modify `app/src/main/kotlin/com/jjrapps/constanza/reminding/SnoozeSettingsScreen.kt`: title
      → `settings_title` (`:45`); add a `settings_snooze_section_title` header above the existing
      snooze radio list; `item { LanguageSection() }` after `DataPortabilitySection()` (`:56`).

## Phase 5: Portability Typed-Error Refactor (PR 3)

- [ ] 5.1 Modify `app/src/main/kotlin/com/jjrapps/constanza/portability/BackupImporter.kt`: add
      `ImportFailure` sealed interface (`UnreadableFile`, `MalformedFile`,
      `UnsupportedVersion(fileVersion)`, `UnknownSlotReference(habitId, slotId)`); change
      `MalformedBackupException`/`UnsupportedBackupVersionException` to carry the typed payload;
      delete the private `MALFORMED_MESSAGE` constant (`:16`).
- [ ] 5.2 Modify `app/src/main/kotlin/com/jjrapps/constanza/portability/DataPortabilityViewModel.kt`:
      `ImportResult.Failed(val message: String)` (`:20`) → `Failed(val failure: ImportFailure)`; both
      `catch` blocks map their exception to an `ImportFailure`.
- [ ] 5.3 Modify `app/src/main/kotlin/com/jjrapps/constanza/portability/DataPortabilityScreen.kt`:
      replace `Text(result.message)` (`:73`) with a `@Composable importFailureMessage(failure)` `when`
      over the 4 new `portability_import_error_*` keys from 1.2.
- [ ] 5.4 Update `app/src/test/kotlin/com/jjrapps/constanza/portability/BackupImporterTest.kt`: `:52`
      `assertTrue(error.message.orEmpty().contains("99"))` → `assertEquals(99, error.fileVersion)`
      (removes the message-string coupling this refactor exists to remove); `:59` add
      `assertEquals(ImportFailure.UnknownSlotReference(1L, 999L), e.failure)`. `:39`, `:44`, `:51`
      (`assertFailsWith<...>`) stay unchanged — they compile against the new payload with no edit.

## Phase 6: Verification (spans PR 1-3)

- [ ] 6.1 Modify `app/build.gradle.kts`: add a `lint { abortOnError = true }` block — there is none
      today (confirmed: no `lint\s*\{`/`abortOnError`/`checkAllWarnings`/`warningsAsErrors` in the
      file). No check IDs yet — see 6.2.
- [ ] 6.2 **Apply-time verification, not a code-writing step.** Run `./gradlew :app:lint` once and
      read the report to confirm which of `MissingTranslation`, `ExtraTranslation`,
      `StringFormatCount`, `StringFormatMatches`, `ImpliedQuantity` the installed AGP actually fires;
      write into 6.1's block **only** the confirmed IDs, dropping any that cannot be confirmed. D9's
      guarantee rests entirely on `StringResourceParityTest` (1.5), so dropping every candidate ID
      changes nothing load-bearing.
- [ ] 6.3 Run `./gradlew check` (unit + lint + detekt): confirms `StringResourceParityTest`,
      `AppLanguageTest`, `BackupImporterTest`, `TimeOfDayFormatTest`, `NotificationPosterTest`.
- [ ] 6.4 Run `./gradlew :app:compileDebugAndroidTestKotlin` — mandatory; `androidTest` does not
      compile with the unit-test source set, and this change alters `postReminder`'s signature across
      6 `androidTest` files.
- [ ] 6.5 Run `./gradlew :app:emulatorMatrixGroupDebugAndroidTest`, API 31 + API 37: new instrumented
      tests (2.8, 2.9, 2.10, 3.4) plus regression on `HabitEditorRotationComposeTest`,
      `TodayAdaptiveComposeTest`, `NotificationPosterInstrumentedTest`,
      `NotificationActionWiringInstrumentedTest`, `CoreFlowE2ETest` (read-only regression targets —
      D8's `enableEdgeToEdge`/`rememberSaveable` route state and the `suspend postReminder` change).

## Rollback Note (carried from design.md)

Everything is additive except one clause: on API 33+ a `LocaleManager` override is a *system*
setting that survives a code revert, so any revert MUST also call
`setApplicationLocales(LocaleList.getEmptyLocaleList())` once, or the app ships English resources
while the system still forces Spanish. Otherwise: deleting `res/values-es/strings.xml`,
`res/xml/locales_config.xml`, and the manifest attribute restores exact pre-change behavior, and a
reverted DataStore key is simply never read again. PR 3 (portability) is kept in its own commit so
it reverts independently of the localization mechanism.
