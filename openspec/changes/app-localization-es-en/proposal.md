# Proposal: English/Spanish localization with an in-app language override

## Intent

The app exists only in English, in one `res/values/strings.xml`. Its maintainer and only current
user is Spanish, based in Barcelona, and uses a Spanish device. Every screen of a habit app read
every single day is read in the wrong language.

This is not "add a translation file". Three things make it a change rather than a resource drop:

1. Notification text is resolved on Hilt's `@ApplicationContext` in a **cold process with no
   Activity** (`reminding/NotificationPoster.kt:36`, channel name `:109`, question `:125`, actions
   `:128-130`, reached via `scheduling/ReminderFireReceiver.kt` → `ReminderFireWorker.kt:52`). A
   user who picks Spanish and still gets English reminders is the **headline failure** of this
   change: the reminder is the product.
2. `minSdk = 31` (`app/build.gradle.kts:43`) sits below the API 33 floor of `LocaleManager`, so the
   override needs a second mechanism for 31/32.
3. Four English literals reach the UI outside the resource system, from a module whose KDoc
   records that it is deliberately Android-free (`portability/BackupImporter.kt`).

Success: a Spanish device shows Spanish on first launch with no user action; any other device shows
English; the in-app picker changes the language, survives process death, and is honoured by a
reminder fired from a cold process.

## Scope

### In Scope

- `res/values-es/strings.xml` — full `es-ES` informal (tú) translation: 111 strings + 1 `plurals`
  block; `app_name` untranslated (proper noun). **No `values-en/`** (see Approach).
- Typed-error refactor of the 4 portability literals (`portability/DataPortabilityViewModel.kt:56`,
  `BackupImporter.kt:16`, `:26`, `:83-85`) into a sealed error shape carrying which-failure + args,
  mapped to `stringResource(...)` at `portability/DataPortabilityScreen.kt:73`.
- Language-override key (`stringPreferencesKey`) in `reminding/ReminderSettingsStore.kt`, on the
  existing single `DataStore<Preferences>` (`core/di/DataStoreModule.kt:17,28-29`).
- The hybrid application mechanism across `ConstanzaApplication`, the Compose root
  (`core/ui/MainActivity.kt`), and `NotificationPoster`.
- `res/xml/locales_config.xml` + `android:localeConfig` on `<application>`.
- Three-option picker (System default / English / Español) as a **new section** in the existing
  `reminding/SnoozeSettingsScreen.kt`, following the `DataPortabilitySection()` fold-in precedent
  (`:56`). Selection order fixed; "System default" = **absence** of the stored key, never a third value.
- Settings title rename: `settings_snooze_title` ("Default snooze duration") becomes a general
  "Settings"/"Ajustes" heading; the snooze radio list gets its own section heading.
- Locale-aware tests (unit + instrumented), including the cold-process Spanish notification.

### Out of Scope

| Excluded | Reason |
|---|---|
| Store-listing metadata | Not in this repository; no Play listing exists yet. |
| RTL support | No RTL language is in scope; `es`/`en` are both LTR. |
| Any language beyond `en`/`es` | Requested set is exactly these two; the fallback covers the rest. |
| Full Settings redesign | Pre-existing debt; folding in a third section plus the title rename is the proportional fix. |
| `androidResources.localeFilters` / APK-size trimming | APK size only, orthogonal to correctness. Genuinely **deferred**, not irrelevant → belongs in `openspec/config.yaml` `carried_forward_open_items` with owner condition "any change already touching `app/build.gradle.kts` resource config; must include BOTH `en` and `es` if adopted". |
| Localizing the OS permission-controller dialog copy | Another process; follows the *device* locale, not this override (`androidTest/e2e/SystemPermissionDialog.kt:40,43` stays English-safe while the matrix keeps an English device locale). |

## Capabilities

### New Capabilities
- `app-localization`: the language the app presents itself in — device-locale resolution, the
  `en`/`es` supported set, the universal English fallback, the three-state in-app override and its
  persistence, and the requirement that every user-visible string (including from a cold background
  process) renders in the resolved language.

### Modified Capabilities
- `reminder-response`: `Notification Actions` — the three action labels and the notification body
  MUST render in the app's resolved language, including when posted from a cold process with no
  Activity ever created.
- `data-portability`: `Import` — import rejection feedback MUST be presented in the app's resolved
  language, which requires the failure to be a typed value rather than an English message string.

## Approach

**Hybrid: native `LocaleManager` on API 33+, self-rolled `createConfigurationContext` wrapping on
API 31/32 and for the notification path. AppCompat rejected.**

**Requirements 1-3 need zero code.** Any `es-*` locale matches `values-es/` on language alone; every
other locale, `en-*` included, falls through to base `values/`, which is already English and serves
as both the English variant and the universal fallback. Creating `values-en/` would duplicate
`values/` and invite drift — do not.

| Surface | API 33+ | API 31/32 |
|---|---|---|
| Picker write | `LocaleManager.setApplicationLocales(...)`; "System default" = `LocaleList.getEmptyLocaleList()` | write/clear the DataStore key |
| UI | system-applied | `CompositionLocalProvider(LocalContext provides wrapped, LocalConfiguration provides wrapped.resources.configuration)` at the Compose root |
| Notification | system-applied before the cold process initializes | `NotificationPoster` wraps its `@ApplicationContext` before `ensureChannel()` and every `getString(...)` |

`stringResource()` needs **both** CompositionLocals: it fetches `Resources` from
`LocalContext.current.resources` and reads `LocalConfiguration.current` only to force recomposition.
Overriding `LocalConfiguration` alone fixes date/time formatting and leaves all 111
`stringResource(...)` calls in the wrong language.

The persisted key is written to on both paths so the DataStore stays the single source of truth the
background path can read; note DataStore has no synchronous read today, which constrains anything
running in `attachBaseContext`.

**Formatting comes along for free, and this change *fulfils* a documented prior intent rather than
inventing one.** `core/ui/TimeOfDayFormat.kt:88` already reads `LocalConfiguration` and
`habit/ScheduleEditors.kt:228` already reads `LocalLocale.current.platformLocale`; both KDocs state
they exist so a per-app override is honoured. Both are `@Suppress`-free and compile clean on
`composeBom 2026.08.00`. No separate formatting work.

**No navigation work.** `core/ui/MainActivity.kt:78-96,123,156-158` already routes
`ConstanzaRoute.Settings` → `SnoozeSettingsRoute`, entered from `tracking/TodayScreen.kt:114-115`.

### Rejected Alternatives

| Rejected | Why it loses |
|---|---|
| **AppCompat backport** (`AppCompatDelegate.setApplicationLocales`) | (a) On API 31/32 it requires migrating `MainActivity` off `ComponentActivity` onto `AppCompatActivity` (official Android docs), reversing the explicit decision recorded at `app/src/main/res/values/themes.xml:6` — "no extra AppCompat/Material XML library needed", `Theme.Constanza` parented on `android:Theme.Material.NoActionBar`. (b) Even with `AppCompatActivity`, the 31/32 backport patches only the Activity context, not the Application context `NotificationPoster` holds — so it does **not** fix notification text there, and the custom wrapping is still required. Pays a migration cost to buy strictly less. |
| **Native API only** | API 33+ only; requirement 4 simply unmet on 31/32, which are inside `minSdk`. |
| **`LocalConfiguration`-only Compose override** | Fixes date/time formatting and nothing else; all 111 `stringResource(...)` calls stay English, and the background path is untouched. Viable only as one half of the chosen mechanism. |

## Affected Areas

| Path | Impact | What changes |
|---|---|---|
| `res/values/strings.xml` | Unchanged (+~4 new keys) | Already both English source and universal fallback; gains the picker/section labels |
| `res/values-es/strings.xml` | New | 111 strings + 1 plurals block, `es-ES` informal |
| `res/xml/locales_config.xml`, `AndroidManifest.xml` | New / Modified | `android:localeConfig` for system-Settings integration |
| `portability/DataPortabilityViewModel.kt`, `BackupImporter.kt`, `DataPortabilityScreen.kt` | Modified | Typed-error refactor; importer stays Android-free |
| `reminding/ReminderSettingsStore.kt`, `core/di/DataStoreModule.kt` | Modified | Language-override key |
| `reminding/NotificationPoster.kt` | Modified | Locale-aware `Context` wrapping for the cold-process path |
| `core/ui/MainActivity.kt` | Modified | Override applied at the composition root |
| `ConstanzaApplication.kt` | Modified | Possibly an `attachBaseContext` override for the background path |
| `reminding/SnoozeSettingsScreen.kt` | Modified | Language section + title rename |
| `core/ui/AppLocale.kt` (or similar) | New | Small wrapping/resolution helper |
| `test/.../TimeOfDayFormatTest.kt` | Modified | Spanish-locale assertions (already declares `Locale.forLanguageTag("es-ES")` at `:184`) |
| `androidTest/...` | New | Override persistence + cold-process Spanish notification |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Notification text stays English on API 31/32 (headline failure) | Med | Dedicated instrumented test posting a reminder in Spanish from a cold process, run on the **API 31** matrix leg |
| Portability refactor breaks tests asserting exception message strings verbatim | Med | `sdd-design`/`sdd-tasks` MUST grep the test sources for the current message literals before the refactor |
| Self-rolled wrapping regresses `enableEdgeToEdge` / `rememberSaveable` route state | Low | Same `onCreate`/`setContent` call site is being modified; existing rotation-survival instrumented tests must stay green |
| First-frame flash on cold start while the suspend DataStore read resolves | Low | Design decision for `sdd-design`; API 33+ has no such window (system-applied) |
| Translation drift between `values/` and `values-es/` | Low | No `values-en/`; lint's `MissingTranslation` covers the pair |

## Verification Strategy

What must be **proven**, not assumed:

1. Device-default resolution: `es-*` → Spanish, `en-*` → English, another locale (e.g. `fr-FR`) → English.
2. The override survives process death.
3. **A reminder notification posted in Spanish from a cold process** — the headline risk.
4. The system-Settings per-app language picker works on API 33+.
5. Date/time and day-of-week formatting follow the override.

This repository verifies instrumented behaviour **device-free**:
`./gradlew :app:emulatorMatrixGroupDebugAndroidTest` on API 31 and API 37 emulators that Gradle
provisions itself (`openspec/config.yaml` `rules.verify.device_free_matrix_command`;
`testing.instrumented.device_free_matrix` is ratified). API 31 and API 37 sit **exactly on both sides
of the API 33 mechanism split** — a genuine stroke of luck for this change: the existing matrix
already exercises both branches with no new infrastructure. Unit gate: `./gradlew check`.

## Rollback Plan

Required by `rules.proposal` because this touches persisted data and the reminder path.

- The change is additive at the data layer: one new `stringPreferencesKey`. Reverting the code
  leaves an orphan key that is simply never read — no migration, no Room schema change, no data loss.
- Deleting `res/values-es/strings.xml` and the `locales_config.xml`/manifest attribute restores exact
  pre-change behaviour, since base `values/` was never modified in meaning.
- On API 33+ a stale `LocaleManager` override would outlive a code revert (it is a system setting):
  the revert MUST also clear it via `setApplicationLocales(LocaleList.getEmptyLocaleList())`, or be
  documented as cleared by the user through Android Settings.
- Riskiest revert unit is the portability typed-error refactor (a signature change, not a resource);
  keep it in its own commit so it can be reverted independently of the localization work.

## Dependencies

- **No new library.** No Gradle dependency is added; `gradle/libs.versions.toml` is untouched.
- Stack ratification: `openspec/config.yaml` `stack.status: ratified`, `still_unpinned: []`. This
  change introduces **nothing new requiring ratification**. The one pre-existing unverified item it
  leans on is `stack.sdk.open_risk` (targetSdk 37 background-execution/notification behaviour never
  read against authoritative docs) — relevant only because the cold-process notification path is
  this change's headline risk; it is not opened or closed here.

## Success Criteria

- [ ] A Spanish-locale device shows Spanish on first launch, no user action, no code path involved.
- [ ] `en-*` and any unlisted locale show English.
- [ ] The picker offers exactly System default / English / Español, in that order, defaulting to
      System default realized as an absent key.
- [ ] The override survives process death and is honoured by the whole UI.
- [ ] A reminder notification fired from a cold process is in the selected language on API 31 and API 37.
- [ ] The app's language is changeable from Android Settings > Apps > Language on API 33+.
- [ ] Zero user-visible English literals remain outside the resource system.
- [ ] `./gradlew check` and the API 31 + API 37 matrix are green.

## Size Forecast

Review budget this session: **800 lines**; `delivery_strategy: auto-chain`.

| Slice | Est. changed lines |
|---|---|
| `values-es/strings.xml` | ~115 (mechanical, low review risk, still counts) |
| Portability typed-error refactor + new strings | ~40-60 |
| Locale store + hybrid mechanism + `locales_config.xml` | ~120-180 |
| Settings picker UI + section/title strings | ~60-90 |
| Tests | ~100-150 |
| **Total** | **~440-600** |

Recommendation: **fits one PR** against the 800-line budget, but close enough that chaining is the
safer call if `sdd-tasks` forecasts high — Slice 1 = translation + string extraction + locale store +
application mechanism (no visible UI, fully testable in isolation); Slice 2 = picker UI and its tests.
`sdd-tasks` owns the final call under the review workload guard.
