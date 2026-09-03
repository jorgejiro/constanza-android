# Exploration: app-localization-es-en

English/Spanish app localization plus an in-app language override.

## Requirements Under Exploration

1. By default the app follows the device language: any `en-*` variant renders English, any `es-*` variant renders Spanish.
2. Any other device language falls back to English as the universal default.
3. That device-following behaviour applies on first install, with no user action.
4. A Settings/Options area lets the user override the language explicitly, and the override survives restart.

## Current State

### Resources

`app/src/main/res/values/strings.xml` is the only string resource file. `values*/` contains
exactly `colors.xml`, `themes.xml`, `ic_launcher_background.xml`, `strings.xml`. There is no
`locales_config.xml` and no `values-es/`.

- 111 `<string>` entries plus exactly one `<plurals>` block (`habit_delete_dialog_body`,
  quantities `one`/`other`). Spanish uses the same `one`/`other` CLDR categories as English,
  so there is no structural plural mismatch.
- 9 keys carry format args: `habit_delete_dialog_title`, `today_slot_pending_snoozed_until`,
  `today_slot_change_a11y` (2 args), `progress_current_streak`, `progress_best_streak`,
  `progress_compliance` (also carries a literal `%%`), `settings_snooze_minutes`,
  `settings_snooze_hours`; the plurals items also carry `%1$d`.
- None of the format-arg strings interpolate a noun requiring Spanish grammatical-gender
  agreement — habit names, times and counts are inserted as opaque values. Translation is
  mechanical, not structurally risky.
- `app_name` ("Constanza") stays untranslated as a proper noun.

### Remaining hardcoded UI strings

A systematic search for `Text("literal")`, `contentDescription = "…"`, `label =`,
`placeholder =` and `title =` found **zero** hardcoded Compose call sites. Every Compose call
site already uses `stringResource(R.string...)`.

What does remain hardcoded is 4 plain Kotlin string literals in `portability/`, all English,
all reaching the UI indirectly through `ImportResult.Failed(message: String)`, which
`DataPortabilityScreen.kt:73` renders verbatim as `Text(result.message)`:

| # | Location | Literal |
|---|---|---|
| 1 | `portability/DataPortabilityViewModel.kt:56` | `ImportResult.Failed("Could not read the selected file.")` |
| 2 | `portability/BackupImporter.kt:16` | `MALFORMED_MESSAGE` (thrown at lines 73/75, surfaced via `e.message.orEmpty()`) |
| 3 | `portability/BackupImporter.kt:26` | `UnsupportedBackupVersionException(fileVersion)` message |
| 4 | `portability/BackupImporter.kt:83-85` | `MalformedBackupException("Habit … references unknown slot …")` |

Constraint: `BackupImporter`'s own KDoc states it and its exceptions are deliberately
Android-free (pure Kotlin, tested without the Android framework). It has no injected `Context`.
Localizing these correctly means turning `ImportResult.Failed` and the exceptions into a typed
error shape carrying structured data (which failure, plus any interpolated args) and mapping to
`stringResource(...)` at the Compose layer — **not** calling `context.getString()` inside
`BackupImporter`, which would reverse that documented design decision.

### Navigation and the Settings destination

The Settings destination already exists and is not orphaned:

- `MainActivity.kt:78-96` — `ConstanzaRoute` sealed interface already carries a `Settings` case.
- `TodayScreen.kt:114-115` — Today's own "Settings" `TextButton`, wired at `MainActivity.kt:123`.
- `MainActivity.kt:156-158` — `ConstanzaRoute.Settings -> SnoozeSettingsRoute(onBack = …)`.

`SnoozeSettingsScreen.kt` is already a multi-section settings screen in practice: it renders the
snooze-duration radio list and folds in `DataPortabilitySection()` as a second section
(`SnoozeSettingsScreen.kt:56`, with the comment "export/import lives on this same Settings
screen, not a new route"). A language picker is a natural third section following the same
precedent. **No new route or navigation-graph work is needed.**

The screen's title string (`settings_snooze_title` = "Default snooze duration") is now stale
given it hosts two-plus unrelated sections. That is pre-existing debt, surfaced under Open
Product Decisions.

### Persistence mechanism

`reminding/ReminderSettingsStore.kt` wraps a single `DataStore<Preferences>`
(androidx.datastore.preferences), file name `"reminder_settings"`, wired via
`DataStoreModule.kt:17,28-29` (`Context.reminderSettingsDataStore by preferencesDataStore(...)`,
Hilt `@Singleton`). A language-override key belongs here as a new `stringPreferencesKey`,
consistent with the existing snooze-duration and onboarding-flag keys.

Wrinkle that constrains the mechanism decision: DataStore reads are suspend/async only. There is
no synchronous read path today, which matters for anything running in `attachBaseContext`.

### Notification path, end to end

`scheduling/ReminderFireReceiver.kt` (a `BroadcastReceiver` triggered by `AlarmManager`,
`exported=false`) enqueues `ReminderFireWorker` via WorkManager (`enqueueUniqueWork`, expedited).
`scheduling/ReminderFireWorker.kt:52` calls `NotificationPoster.postReminder(...)`.

`reminding/NotificationPoster.kt` is constructed with Hilt's `@ApplicationContext Context`
(line 36) and resolves **all** notification text on that injected Application context:
the channel name (`ensureChannel()`, line 109), the default question (line 125) and the three
action labels (lines 128-130), each via `context.getString(R.string...)`.

Critically, this whole path can run in a **cold process with no Activity ever created** — an
`AlarmManager` fire after process death is the normal case the receiver's own KDoc already
accounts for. Any mechanism that patches only an Activity's configuration cannot localize
notification text. The fix has to reach the Application-level `Context` that `NotificationPoster`
holds.

### Locale-sensitive formatting: already correct, and already anticipating this change

Two places already read locale reactively rather than through `Locale.getDefault()`, and both
document the intent explicitly:

- `core/ui/TimeOfDayFormat.kt:75-77,87-93` — `rememberTimeOfDayFormat()` reads
  `LocalConfiguration.current.locales[0]`, with the KDoc: *"The locale comes from
  LocalConfiguration rather than Locale.getDefault() so a per-app language override is honoured."*
- `habit/ScheduleEditors.kt:216-228` — `DayOfWeekPicker` reads `LocalLocale.current.platformLocale`
  (a real `androidx.compose.ui.platform.LocalLocale`, in active use against
  `composeBom = "2026.08.00"`, `gradle/libs.versions.toml:8`) so day-of-week labels recompose on
  locale change.

Both depend on Compose's `LocalConfiguration`/`LocalLocale`. Combined with how
`stringResource()` resolves — reading `LocalConfiguration.current` to force recomposition but
fetching `Resources` from `LocalContext.current.resources` — whichever mechanism updates the
`Context`/`Configuration` seen by the Compose tree fixes these two formatters and every
`stringResource()` call together. No separate formatting fix is needed.

### Tests

No unit or instrumented test asserts a raw English UI string coming from `stringResource`-backed
content (checked via `assertEquals("`, `onNodeWithText("`, `assertTextEquals("`, `hasText("`
across `app/src`).

- The `onNodeWithText("…")` hits in `HabitEditorCancelComposeTest.kt` and
  `HabitEditorRotationComposeTest.kt` match user-*entered* habit names and notes
  ("Drink water", "Read before bed"), not app copy. Unaffected by locale.
- `TimeOfDayFormatTest.kt` already tests multiple locales (`Locale.US`, `Locale.UK`,
  `Locale.FRANCE`) and already declares `Locale.forLanguageTag("es-ES")` at line 184. This class
  is the right home for new Spanish-locale format assertions.
- `androidTest/e2e/SystemPermissionDialog.kt:40,43` has English-only regex fallbacks
  (`ALLOW_BUTTON_TEXT`/`DENY_BUTTON_TEXT`) for the OS permission-controller dialog. That dialog
  belongs to another process and follows the *device* locale, not this app's override, so it is
  unaffected as long as the managed-device emulator matrix keeps its default English device locale.

No test-side blocker found.

### Build config

`app/build.gradle.kts` has no `resourceConfigurations` / `androidResources.localeFilters` block
(full file read). No locale is being stripped today, so `values-es/` is picked up with no Gradle
change. An optional `androidResources { localeFilters += listOf("en", "es") }` (AGP 9 DSL) would
trim non-en/es locale strings bundled by dependencies — an APK-size optimization, not a
correctness requirement. If adopted it must include **both** `"en"` and `"es"`.

## Affected Areas

| Path | Change |
|---|---|
| `res/values/strings.xml` | unchanged; already both the English source and the universal fallback |
| `res/values-es/strings.xml` | **new** — full Spanish translation of 111 strings + 1 plurals block |
| `portability/DataPortabilityViewModel.kt`, `BackupImporter.kt`, `DataPortabilityScreen.kt` | typed-error refactor so the 4 literals route through `stringResource` |
| `reminding/ReminderSettingsStore.kt`, `core/di/DataStoreModule.kt` | new language-override preference key |
| `reminding/NotificationPoster.kt` | locale-aware `Context` wrapping so cold-process notifications honour the override |
| `core/ui/MainActivity.kt` | apply the override at the composition root |
| `ConstanzaApplication.kt` | currently has no `attachBaseContext` override; may need one for the background path |
| `reminding/SnoozeSettingsScreen.kt` | language-picker section, same pattern as the `DataPortabilitySection()` fold-in |
| `res/values/themes.xml` | affected **only** if AppCompat is chosen; currently deliberately platform-only (`parent="android:Theme.Material.NoActionBar"`, line 6: "no extra AppCompat/Material XML library needed") |
| `test/.../TimeOfDayFormatTest.kt` | Spanish-locale format assertions |
| `app/build.gradle.kts` | optional `localeFilters` |

## Approaches

### 1. Native per-app locale only (`LocaleManager.applicationLocales` + `android:localeConfig`)

**Pros.** Zero new dependency. One-line application. System-persisted: survives process death,
appears in system Settings > Apps > Language, and per Android's own docs "these APIs
automatically sync with system settings". Because it is a real per-app *system* setting, it is
already in effect before a cold WorkManager process's `Application` and `NotificationPoster`
initialize — the notification path is covered with no extra code.

**Cons.** API 33+ only, confirmed against official documentation. This app's `minSdk = 31`
(`app/build.gradle.kts:43`), so API 31/32 gets nothing and requirement 4 is unmet there.

**Effort.** Low, for 33+ only.

### 2. AppCompat backport (`AppCompatDelegate.setApplicationLocales`, `AppLocalesMetadataHolderService`)

Verified against official Android documentation: *"If you're using Compose with
`setApplicationLocales`, you must extend your activity from `AppCompatActivity`. Otherwise,
setting the app locale won't work"*, and *"the backward compatible APIs work with the
AppCompatActivity context, not the application context, for Android 12 (API level 32) and
earlier."*

So on API 31/32 this **requires** migrating `MainActivity` off `ComponentActivity` onto
`AppCompatActivity`.

**Concrete cost in this codebase.** `res/values/themes.xml:6` currently records, as a deliberate
prior decision, *"no extra AppCompat/Material XML library needed"*, with `Theme.Constanza`
parented on plain `android:Theme.Material.NoActionBar`. `AppCompatActivity` requires its manifest
theme to descend from `Theme.AppCompat.*`/`Theme.MaterialComponents.*` or it throws at inflate
time. This change would have to reverse that explicit decision, not merely add a dependency.
`gradle/libs.versions.toml` has zero `androidx.appcompat` entries today.

**Further verified limitation.** Even with `AppCompatActivity`, the backport on API 31/32 updates
only the `AppCompatActivity` context, not the Application context. It therefore does **not** fix
`NotificationPoster`'s Application-context `getString()` calls on those two API levels. The
notification path still needs custom `Context` wrapping on top of AppCompat — undercutting
AppCompat's turnkey selling point for exactly the path that matters most here.

**Pros.** Well-trodden, Google-recommended. Covers 33+ by delegating to the framework API. The
static `AppCompatDelegate.getApplicationLocales()` read needs no Activity, so it can serve as a
read-only source of truth even from `NotificationPoster`.

**Cons.** New dependency. Reverses a documented architectural decision. Migration risk across
Hilt `@AndroidEntryPoint`, `enableEdgeToEdge` and the `rememberSaveable` route state in
`MainActivity.kt` — though `AppCompatActivity` is itself a `ComponentActivity` subclass, so those
specific APIs remain available and that part is low risk. Still needs custom notification-path
code on 31/32 regardless.

**Effort.** Medium-High.

### 3. Self-rolled: persisted language tag + explicit `Context` wrapping (`createConfigurationContext`)

Store the tag in the existing `ReminderSettingsStore` DataStore as a new `stringPreferencesKey`
(absent/null = system default, `"en"`, `"es"`).

*UI side.* Wrap `LocalContext` and `LocalConfiguration` **together** at the Compose composition
root (`MainActivity.onCreate`'s `setContent { ConstanzaTheme { FirstRunGate() } }`) via
`CompositionLocalProvider(LocalContext provides wrapped, LocalConfiguration provides wrapped.resources.configuration) { … }`,
where `wrapped = context.createConfigurationContext(Configuration(base).apply { setLocale(tag) })`.
Because `stringResource()` fetches `Resources` from `LocalContext.current.resources` (reading
`LocalConfiguration.current` only to force recomposition), overriding both at the root makes
every `stringResource(...)` in the tree resolve to the chosen locale — no `attachBaseContext` or
`recreate()` needed for the UI, since it is purely reactive to a `mutableStateOf` locale tag.

*Background side.* `NotificationPoster` wraps its injected `@ApplicationContext` the same way,
reading the persisted tag before `ensureChannel()` and `getString(...)`. Identical on API 31
through 37, with no OS-version branching.

**Pros.** No new dependency. No `ComponentActivity` → `AppCompatActivity` migration. One uniform
code path across the whole supported API range. Reuses the existing DataStore, Hilt and Compose
patterns already in this codebase.

**Cons.** All custom code, so all correctness burden (recreate-vs-recompose edge cases,
first-frame flash on cold start) sits with this project rather than a maintained library. The
persisted tag is invisible to the system Settings per-app language picker — a real if minor UX
regression versus options 1 and 2, mitigated by also calling the native `LocaleManager` API on
33+ so the system picker keeps working.

**Effort.** Medium.

### 4. Compose-level-only override (providing `LocalConfiguration`/`LocalLocale` without `LocalContext`)

Explicitly insufficient alone. `stringResource()`'s resource lookup goes through
`LocalContext.current.resources`, not through the `LocalConfiguration` CompositionLocal's value,
so overriding `LocalConfiguration` alone changes recomposition timing but not which
`Resources`/locale `getString()` reads from. It would fix only code that manually reads
`LocalLocale.current`/`LocalConfiguration.current` for formatting (`TimeOfDayFormat`,
`DayOfWeekPicker`) while leaving all 111 `stringResource(...)` calls untouched — i.e. it fixes
date/time formatting but not translated static copy, which is the actual point of requirement 4.

Viable only as one half of approach 3, never alone.

### Zero-code resolution for requirements 1-3

Android's resource-qualifier fallback needs no explicit configuration:

- Any `es-*` device locale (es-ES, es-MX, es-419, …) matches the `values-es/` qualifier on
  language alone; no `values-es-rXX` folder exists to compete for a more specific match.
- Any `en-*` locale, and any other locale entirely (fr-FR, de-DE, …), falls through to the base
  `values/` folder because no `values-en/` or other qualifier folder exists. The base folder,
  already written in English, serves simultaneously as the English variant and the universal
  fallback.

No `values-en/` folder should be created — it would duplicate `values/` and invite drift.
Requirements 1-3 need zero code, only the new `values-es/strings.xml`. `resConfigs`/`localeFilters`
is optional and orthogonal (APK size only).

### The "system default" tri-state

Requirement 4's picker must have exactly three options — System default / English / Español —
where System default is realized by **clearing** the override
(`LocaleManager.setApplicationLocales(LocaleList.getEmptyLocaleList())` on 33+; deleting the
DataStore key or writing an absent sentinel otherwise), never by storing a third persisted value.
This must be an explicit instruction to `sdd-design`, not left implicit.

## Recommendation

**Hybrid: native `LocaleManager` on API 33+, self-rolled `createConfigurationContext` wrapping
(approach 3) as the API 31/32 fallback and for the notification path. Do not adopt AppCompat.**

Reasoning, from the evidence above:

- AppCompat's main selling point — turnkey and Google-blessed — is undercut twice here. It forces
  reversing an explicit, already-documented architectural decision (`themes.xml:6`, zero-AppCompat
  `ComponentActivity`), **and** it still does not solve the notification path on 31/32 without the
  same custom `Context` wrapping approach 3 already requires. Paying the AppCompat migration cost
  buys strictly less than approach 3 against this codebase's actual constraints.
- The native API is genuinely free wherever it is available (33+): one line, no dependency,
  system-integrated, and its own documentation confirms it syncs with system settings including
  for background contexts. Use it there.
- Approach 3's wrapping technique is needed regardless — for 31/32, and for the notification path
  below 33 — so it is written once and reused, rather than adding a second, redundant
  library-provided mechanism for the same subset of API levels AppCompat would only partly help.
- It keeps `MainActivity` as `ComponentActivity`, consistent with this codebase's stated decisions.

## Scope Boundaries

**IN.** `values-es/strings.xml` full translation; typed-error refactor of the 4 hardcoded
portability strings; language-override DataStore key in `ReminderSettingsStore`; the hybrid
native + self-rolled application mechanism (Application, Compose root, `NotificationPoster`);
three-option picker as a new section in the existing `SnoozeSettingsScreen.kt`;
`TimeOfDayFormatTest.kt` Spanish-locale assertions; instrumented coverage for the override
surviving restart and for a reminder notification posted in Spanish from a cold process.

**OUT** (recommended, pending user confirmation). Translating store-listing metadata; RTL
support; any language beyond en/es; a full Settings-screen redesign beyond folding in one more
section; `localeFilters`/APK-size trimming.

## Size Forecast

Review budget for this session is 800 lines; `delivery_strategy` is `auto-chain`.

| Slice of work | Estimated changed lines |
|---|---|
| `values-es/strings.xml` | ~115 new (mechanical translation; low review risk despite line count, but counts toward the ceiling) |
| Portability typed-error refactor + 4 new string resources | ~40-60 |
| Locale store + hybrid application mechanism (`ReminderSettingsStore`, `DataStoreModule`, `ConstanzaApplication`, `MainActivity`, `NotificationPoster`, a small `AppLocale` helper) | ~120-180 |
| Settings picker UI + 3-4 new strings | ~60-90 |
| Tests (`TimeOfDayFormatTest` additions, locale-override store unit test, instrumented picker-persistence and Spanish-notification tests) | ~100-150 |
| **Total** | **~440-600** |

Likely fits the 800-line budget as one PR, but close enough that chaining into two slices is the
safer call if `sdd-tasks` forecasts high:

- **Slice 1** — translation + string extraction + locale store + application mechanism. No visible
  UI yet, fully testable in isolation.
- **Slice 2** — picker UI and its tests, layered on slice 1.

The final call belongs to `sdd-tasks`' own forecast under the review workload guard.

## Open Product Decisions

Enumerated only; not resolved here.

1. **Spanish register** — `tú` vs `usted` throughout the UI.
2. **Neutral/international Spanish vs Spain-specific (`es-ES`)** wording. No plural-you copy
   exists, but idiom choices such as "ajustes" vs "configuración" are genuinely regional.
3. **Picker option labels and order** — System default / English / Español; confirm wording and
   which option is selected by default.
4. **Whether to rename `SnoozeSettingsScreen`'s title** (currently `settings_snooze_title` =
   "Default snooze duration") to a general Settings heading now that it hosts three unrelated
   sections. Pre-existing debt this change makes more visible; not strictly in scope.
5. **Whether to invest in native-API system-Settings integration** (`LocaleManager` +
   `android:localeConfig` manifest entry) at all, versus self-rolling uniformly across every API
   level for a single simpler code path that loses the system-picker integration on 33+.

## Risks

- The portability error-message refactor touches exception types used by
  `BackupImporterNormalizationTest`-adjacent tests (not exhaustively confirmed). `sdd-design` or
  `sdd-tasks` must check for any test asserting the current exception message strings verbatim.
- `attachBaseContext` / cold-start `Context` wrapping is genuinely new engineering surface with no
  library backing it. It needs dedicated coverage — a unit test for the wrapping helper and an
  instrumented test for the full cold-process notification path — rather than being treated as glue.
- The self-rolled mechanism must be verified not to break the already-passing `enableEdgeToEdge`
  and rotation-survival (`rememberSaveable` route state) behaviour in `MainActivity.kt`. Unrelated
  today, but they share the `onCreate`/`setContent` call site being modified.
- No claim above needed a RESEARCH REQUIRED flag: every mechanism claim was verified either
  directly against this repository or against official Android developer documentation.

## Ready for Proposal

Yes. Scope, mechanism, affected files and size forecast are concrete enough for `sdd-propose`.
The five open product decisions should be put to the user as one grouped question at proposal
time; they do not block the proposal's non-decision-dependent scaffolding.
