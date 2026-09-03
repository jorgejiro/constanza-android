# Design: English/Spanish localization with an in-app language override

## Technical Approach

Hybrid, exactly as the proposal settled it. AppCompat is not adopted; `MainActivity` stays a
`ComponentActivity` and `res/values/themes.xml:12` keeps its `android:Theme.Material.NoActionBar`
parent, preserving the decision recorded at `themes.xml:6`.

| Surface | API 33+ | API 31/32 |
|---|---|---|
| Source of truth | `LocaleManager.applicationLocales` (system) | new `stringPreferencesKey` in the existing DataStore |
| UI | system-applied; provider is a pass-through | `ProvideAppLocale` at the Compose root |
| Notification | system-applied before the process starts | `NotificationPoster` wraps its `@ApplicationContext` per post |

Requirements 1–3 need zero code: any `es-*` locale matches `values-es/` on language alone, and every
other locale falls through to base `values/`, which is already English. **No `values-en/`.**

A new feature package `localization/` owns the mechanism, consistent with the repo's screaming-
architecture slicing (`reminding/`, `portability/`, `tracking/`, `habit/`, `scheduling/`, `core/`).
The persisted key still lives in `reminding/ReminderSettingsStore.kt`, whose own KDoc
(`ReminderSettingsStore.kt:13-20`) already justifies unrelated scalar keys sharing that one file.

## Architecture Decisions

### D1 — One source of truth per API level, never two

**Choice**: On API 33+ the DataStore key is never written or read; `LocaleManager` is the only store.
Below 33 the DataStore key is the only store. The API split lives in one class,
`localization/AppLocaleController`.
**Alternatives**: write both on every path (the proposal's provisional wording).
**Rationale**: writing both creates a divergence the app cannot detect — a change made in Android
Settings > Apps > Language never reaches DataStore, so a mirrored copy is stale by construction. With
one store per level there is nothing to reconcile. On 33+ the system override is already in effect
before a cold WorkManager process initializes (explore.md:166-174, verified against official docs),
so the background path needs no persisted tag there.

### D2 — The picker re-reads on every `ON_START`, it does not cache

**Choice**: `LanguageSettingsViewModel.refresh()` is called from `init` and from a
`LifecycleStartEffect` in `LanguageSection`. It calls `AppLocaleController.current()`.
**Alternatives**: rely on the system recreating the Activity after a system-Settings language change.
**Rationale**: recreation-vs-process-kill after a background system-Settings change is
platform-dependent; a read on every start is deterministic and costs one system call. This is the
whole answer to "two surfaces, one truth" on 33+.

### D3 — The first-frame gate reuses `FirstRunGate`, it does not add a second one

**Choice**: `FirstRunGateViewModel` (`MainActivity.kt:175-183`) gains the resolved language and
exposes `StateFlow<StartupState?>`; `null` still renders nothing — the `null -> Unit` branch at
`MainActivity.kt:202`, inside `FirstRunGate` (`:193-213`). `ProvideAppLocale` wraps the non-null branches.
**Alternatives**: a second gate above `FirstRunGate`; accepting one frame in the device language.
**Rationale**: zero added latency, provably. Below 33 the language tag and `onboardingDone` are both
`map`s over the same `dataStore.data` flow, so they resolve on the same first emission. On 33+ the
language read is a synchronous `LocaleManager` call, so the combined state resolves exactly when
`onboardingDone` does. The blank hold stays what it is today — window-background pixels, per
`MainActivity.kt:186-191` — and there is no flash to accept.

### D4 — `postReminder` becomes `suspend`; no synchronous mirror

**Choice**: `NotificationPoster` gains an injected `AppLocaleController` and
`postReminder` becomes `suspend`. It resolves a localized `Context` once per post and uses it for
`ensureChannel()`, the default question and the three action labels. `canPost()` keeps its current
non-suspend public signature — defined at `NotificationPoster.kt:94`, the only `fun canPost` in
`app/src/main/kotlin/`, and depended on by 6 unit assertions (`NotificationPosterTest.kt:47,55,65,75,83,84`)
— and delegates to a private `canPost(ctx: Context)`.
**Alternatives**: a SharedPreferences synchronous mirror; a defaulted extra parameter.
**Rationale**: the exact call site is `ReminderFireHandler.fire()` at
`ReminderFireWorker.kt:52`, inside a `suspend fun` reached from `CoroutineWorker.doWork()`
(`ReminderFireWorker.kt:79-84`). The suspend read is already available there, and
`ReminderSettingsStore.currentSnoozeDuration()` (`ReminderSettingsStore.kt:37`) is the same one-shot
pattern `SnoozeWorker` already uses. A second persistence mechanism would be unjustified. A
Kotlin-defaulted parameter is rejected as a trap: `ReminderFireWorkerTest.kt:84` stubs
`every { notificationPoster.postReminder(any(), any(), any(), any()) }`, and a default would bind
that stub to `tag = null` only, so it would stop matching the moment production passes a real value —
a silently broken test, not a failing one. (`:90` is a `verify` with concrete literal arguments, not a
second `any()` stub; the argument rests on `:84` alone.)
Channel-name re-localization comes free: `ensureChannel()` is idempotent and re-invoked on every
post (`NotificationPoster.kt:102-113`), so the channel name follows a language change on the next post.

### D5 — `ConstanzaApplication` is not touched

**Choice**: no `attachBaseContext` override.
**Rationale**: DataStore has no synchronous read (explore.md:82-84), so an override would mean a
blocking disk read during Application startup for every process, including the UI one, to fix a
problem `NotificationPoster`'s per-post wrapping already fixes. The proposal listed this as
"possibly" needed; it is not.

### D6 — Portability failures become a typed value

**Choice**: `ImportFailure` sealed interface in `portability/`, Android-free. The two exception
**types** are preserved so existing tests keep compiling; only their payload changes.

The "deliberately Android-free" premise this rests on lives at
`portability/DataPortabilityViewModel.kt:25` — "the SAF read/write boundary around
[BackupExporter]/[BackupImporter], **which stay Android-free themselves**" — not in
`BackupImporter.kt`'s own class KDoc (`:46-52`), which explains the parse/write split instead.
Corrected here because explore.md attributes it to the wrong file and D6's whole argument rests on it.

```kotlin
sealed interface ImportFailure {
    data object UnreadableFile : ImportFailure                                   // was DataPortabilityViewModel.kt:56
    data object MalformedFile : ImportFailure                                    // was MALFORMED_MESSAGE, BackupImporter.kt:16
    data class UnsupportedVersion(val fileVersion: Int) : ImportFailure          // was BackupImporter.kt:26
    data class UnknownSlotReference(val habitId: Long, val slotId: Long) : ImportFailure // was BackupImporter.kt:83-85
}

class MalformedBackupException(val failure: ImportFailure, cause: Throwable? = null) :
    Exception(failure.toString(), cause)                    // diagnostic only, never user-visible
class UnsupportedBackupVersionException(val fileVersion: Int) : Exception("formatVersion=$fileVersion")
```

`ImportResult.Failed(val message: String)` becomes `Failed(val failure: ImportFailure)`
(`DataPortabilityViewModel.kt:20`), and `DataPortabilityScreen.kt:73` maps it through a
`@Composable importFailureMessage(failure)` `when` over four new `stringResource` keys.

**Tests affected — exact evidence** (`rg` over `app/src`; only one test file references these types):

| Location | Effect |
|---|---|
| `BackupImporterTest.kt:39` | `assertFailsWith<MalformedBackupException>` — compiles and passes unchanged |
| `BackupImporterTest.kt:44` | same — unchanged |
| `BackupImporterTest.kt:59` | same — unchanged; SHOULD gain `assertEquals(ImportFailure.UnknownSlotReference(1L, 999L), e.failure)` |
| `BackupImporterTest.kt:51` | `assertFailsWith<UnsupportedBackupVersionException>` — compiles unchanged |
| `BackupImporterTest.kt:52` | `assertTrue(error.message.orEmpty().contains("99"))` — **MUST become** `assertEquals(99, error.fileVersion)`; asserting a message string is the coupling this refactor removes |

No test constructs `ImportResult.Failed(...)`; `MALFORMED_MESSAGE` is `private const`. Nothing else breaks.

### D7 — The tri-state clear removes, it never stores

`AppLanguage.SystemDefault` → below 33 `dataStore.edit { it.remove(LANGUAGE_TAG_KEY) }`; on 33+
`localeManager.applicationLocales = LocaleList.getEmptyLocaleList()`. Absent key = System default on
read. No third persisted value exists anywhere.

### D8 — What is preserved at `MainActivity`'s shared call site

`enableEdgeToEdge(...)` (`MainActivity.kt:54-57`) runs in `onCreate` **before** `setContent` and is
outside the composition entirely — untouched. The `rememberSaveable` route state
(`MainActivity.kt:110`) and `startRoute` (`MainActivity.kt:200`) sit *below* the new
`CompositionLocalProvider`, which does not override `LocalSaveableStateRegistry`, so rotation
survival is unchanged. `lifecycle.addObserver(replanOnResumeObserver)` is untouched.

### D9 — Format-argument and plural integrity is enforced by a resource-parity unit test, not by lint alone

Covers `specs/app-localization/spec.md`'s `Format-Argument And Plural Integrity Under Translation`
requirement and all four of its scenarios, which had no mechanism before this revision.

**The exact surface at risk**, read from `res/values/strings.xml` (8 `<string>` keys plus the one
`<plurals>` resource = 9 format-arg-carrying resources):

| Line | Key | Specifiers |
|---|---|---|
| `:27` | `habit_delete_dialog_title` | `%1$s` |
| `:29`, `:30` | `habit_delete_dialog_body` (plurals `one` / `other`) | `%1$d` in each item |
| `:107` | `today_slot_pending_snoozed_until` | `%1$s` |
| `:118` | `today_slot_change_a11y` | `%1$s`, `%2$s` — **two args; the likely Spanish-word-order break** |
| `:134` | `progress_current_streak` | `%1$d` |
| `:135` | `progress_best_streak` | `%1$d` |
| `:136` | `progress_compliance` | `%1$d` **plus a literal `%%`** |
| `:140` | `settings_snooze_minutes` | `%1$d` |
| `:141` | `settings_snooze_hours` | `%1$d` |

A translator who reorders `%1$s`/`%2$s` at `:118`, drops a positional index, or unescapes `:136`'s
`%%` produces a runtime `IllegalFormatException` or a raw `%` on screen. Neither is caught by
compilation or by any test in the repo today.

**Choice**: a deterministic JVM unit test, `StringResourceParityTest`, as the load-bearing gate, plus
a `lint { }` block as defence in depth.
**Alternatives**: lint alone; manual review of the translation.
**Rationale**: the unit test depends on nothing but the two XML files, runs in `./gradlew check` with
no emulator, and fails with a message naming the offending key. Lint alone cannot be the gate here for
two reasons — `app/build.gradle.kts` currently has **no `lint { }` block at all** (verified: `rg
'lint\s*\{|abortOnError|checkAllWarnings|warningsAsErrors'` over that file returns nothing), so
translation findings do not fail the build today; and I could not establish from this repository which
translation/format check IDs the installed AGP actually ships or how they behave. Manual review is not
a mechanism.

`StringResourceParityTest` parses `app/src/main/res/values/strings.xml` and
`app/src/main/res/values-es/strings.xml` and asserts, per key:

1. the **multiset** of format specifiers is identical in both languages (catches a dropped,
   duplicated or added argument; a multiset rather than a sequence, because Spanish word order MAY
   legitimately reorder `%1$s` and `%2$s` in the rendered string while keeping both indices);
2. every key present in `values/` is present in `values-es/`, except those marked
   `translatable="false"` (catches a missed translation independently of lint `MissingTranslation`);
3. no key exists in `values-es/` that is absent from `values/` (catches a stale or misspelled key);
4. `progress_compliance` contains a literal `%%` in **both** files (the one case where the specifier
   multiset alone would pass while the rendered output is wrong);
5. the `habit_delete_dialog_body` plurals resource carries **both** `one` and `other` items in
   Spanish, each with its own `%1$d`.

It reads the XML as files on the JVM classpath-independent filesystem, so it needs no Android
framework and none of the mockable-jar stubbing traps apply.

**The `lint { }` block** is specified as a second net, deliberately not as the gate:

```kotlin
// app/build.gradle.kts — new block
lint {
    abortOnError = true
    // Confirm each ID against `./gradlew :app:lint` output before relying on it — see below.
}
```

Candidate check IDs are `MissingTranslation`, `ExtraTranslation`, `StringFormatCount`,
`StringFormatMatches` and `ImpliedQuantity`. **Their existence and behaviour in the installed AGP are
NOT verified by this design.** Apply MUST run `./gradlew :app:lint` once and read the report to
confirm which of them actually fire before promoting any to an error severity; any ID that cannot be
confirmed is dropped from the block rather than asserted. Because D9's guarantee already rests
entirely on `StringResourceParityTest`, dropping every one of them still leaves the spec requirement
covered — the lint block is redundancy, and it is scoped so that discovering a check does not exist
changes nothing load-bearing.

## Data Flow

Reminder / alarm path (the headline risk):

    AlarmManager ─→ ReminderFireReceiver ─→ WorkManager ─→ ReminderFireWorker.doWork() [suspend]
                                                                    │
                                                          ReminderFireHandler.fire()  (:52)
                                                                    │
                                                    NotificationPoster.postReminder() [now suspend]
                                                                    │
                                              AppLocaleController.localizedApplicationContext()
                                                     ├─ API 33+ → @ApplicationContext as-is
                                                     └─ API <33 → ReminderSettingsStore.currentLanguageTag()
                                                                  → createConfigurationContext(es)
                                                                    │
                                          ensureChannel(ctx) + getString(question, yes/no/snooze)
                                                                    │
                                                          NotificationManager.notify()

Snooze path — unchanged, and deliberately so:

    Notification "Aplazar" ─→ ActionReceiver ─→ SnoozeWorker
                                                  ├─ notificationPoster.cancel()  (no strings)
                                                  └─ AlarmScheduler re-arm ─→ re-enters the path above

UI path:

    setContent { ConstanzaTheme { FirstRunGate() } }
              │
      FirstRunGateViewModel.startup: StateFlow<StartupState?>   (null → render nothing)
              │
      ProvideAppLocale(language)   ── API 33+ or SystemDefault → pass-through
              │                    ── else CompositionLocalProvider(LocalContext, LocalConfiguration)
              ├─ OnboardingRoute / ConstanzaApp → 111 stringResource(...)
              │        └─ LocalResources, recomputed from both (Finding A) ✔
              ├─ rememberTimeOfDayFormat()  → LocalConfiguration (TimeOfDayFormat.kt:88)   ✔
              └─ DayOfWeekPicker            → LocalLocale        (ScheduleEditors.kt:228)  ✘
                       must become LocalConfiguration.current.locales[0] (Finding B)

## File Changes

| File | Action | Description |
|---|---|---|
| `res/values-es/strings.xml` | Create | 111 strings + the one `<plurals>` block, `es-ES` informal (tú), Spain lexicon ("Ajustes", "Aplazar"). `app_name` omitted. Single file; no `values-es-rES/`, no `values-en/`. |
| `res/values/strings.xml` | Modify | `app_name` gains `translatable="false"` (keeps lint `MissingTranslation` clean while it stays untranslated); + ~8 keys: `settings_title`, `settings_snooze_section_title`, `settings_language_section_title`, `settings_language_system_default`, `settings_language_english`, `settings_language_spanish`, and 4 `portability_import_error_*`. `settings_snooze_title` (`:139`) is repurposed → renamed `settings_title` = "Settings"/"Ajustes". |
| `res/xml/locales_config.xml` | Create | `<locale-config>` with `en` and `es` (contents below). `res/xml/` does not exist yet. |
| `AndroidManifest.xml` | Modify | `android:localeConfig="@xml/locales_config"` on `<application>` (line 9-15), plus `tools:targetApi="33"` to silence lint `UnusedAttribute` at `minSdk 31`. |
| `localization/AppLanguage.kt` | Create | `enum class AppLanguage(val tag: String?) { SystemDefault(null), English("en"), Spanish("es") }` + `fromTag(String?)`. |
| `localization/AppLocale.kt` | Create | `fun localizedContext(base: Context, tag: String?): Context` (returns `base` when `tag == null`); `@Composable fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit)`. |
| `localization/AppLocaleController.kt` | Create | `@Singleton`. `suspend fun current(): AppLanguage`, `suspend fun set(AppLanguage)`, `fun observe(): Flow<AppLanguage>`, `suspend fun localizedApplicationContext(): Context`. The **only** place `Build.VERSION.SDK_INT >= 33` / `LocaleManager` appears. |
| `localization/LanguageSettingsViewModel.kt` | Create | `@HiltViewModel`; `selected: StateFlow<AppLanguage>`, `refresh()`, `select(AppLanguage)`. |
| `localization/LanguageSection.kt` | Create | Three-option radio section + `LifecycleStartEffect { refresh() }`, mirroring `DataPortabilitySection`. |
| `reminding/ReminderSettingsStore.kt` | Modify | `LANGUAGE_TAG_KEY = stringPreferencesKey("language_tag")` in the `internal companion object` (`:72-76`); `languageTag: Flow<String?>`, `suspend fun currentLanguageTag(): String?`, `suspend fun setLanguageTag(tag: String?)` with `remove()` on null. |
| `reminding/NotificationPoster.kt` | Modify | Second injected param `AppLocaleController`; `postReminder` → `suspend`; `ensureChannel`/`buildNotification`/`action` take a `Context`. |
| `reminding/SnoozeSettingsScreen.kt` | Modify | Title → `settings_title` (`:45`); a `settings_snooze_section_title` header above the radio list; `item { LanguageSection() }` after `DataPortabilitySection()` (`:56`). |
| `core/ui/MainActivity.kt` | Modify | `FirstRunGateViewModel` combines language + `onboardingDone` into `StartupState?`; `FirstRunGate` wraps its non-null branches in `ProvideAppLocale`. |
| `habit/ScheduleEditors.kt` | Modify | **Required, per Finding B.** `:228` `LocalLocale.current.platformLocale` → `LocalConfiguration.current.locales[0]`; drop the now-unused `LocalLocale` import (`:30`); rewrite the KDoc (`:215-221`) to record *why* `LocalLocale` cannot carry a per-app override. |
| `portability/BackupImporter.kt` | Modify | `ImportFailure` added; exception payloads typed; `MALFORMED_MESSAGE` deleted. |
| `portability/DataPortabilityViewModel.kt` | Modify | `ImportResult.Failed(failure)`; the two `catch` blocks map to `ImportFailure`. |
| `portability/DataPortabilityScreen.kt` | Modify | `importFailureMessage(...)` replaces `Text(result.message)` (`:73`). |
| `app/build.gradle.kts` | Modify | New `lint { abortOnError = true }` block — there is none today. Second net only; see D9 for why the check IDs are confirmed at apply time rather than asserted here. |
| `test/.../StringResourceParityTest.kt` | Create | **D9's load-bearing gate.** Parses both `strings.xml` files and asserts format-specifier multiset parity, key parity, the literal `%%`, and Spanish `one`/`other` plurals. Pure JVM. |
| `core/di/DataStoreModule.kt` | Unchanged | The existing single `DataStore<Preferences>` already suffices; no new module wiring. |
| `ConstanzaApplication.kt` | Unchanged | See D5. |

**Divergence from the proposal, recorded rather than silently taken.** `proposal.md`'s Affected Areas
table lists `core/di/DataStoreModule.kt` as Modified; this table marks it Unchanged. The proposal
wrote that row before the persistence shape was settled. Nothing in this design needs it: the new key
goes into the existing `ReminderSettingsStore` against the `DataStore<Preferences>` that
`DataStoreModule.kt:26-29` already provides `@Singleton`, and no new binding, qualifier or second
DataStore file is introduced. This design's call supersedes the proposal's provisional one; a reader
comparing the two documents should treat the Unchanged marking as the current answer.

`res/xml/locales_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="es" />
</locale-config>
```

## Interfaces / Contracts

```kotlin
// localization/AppLocale.kt
fun localizedContext(base: Context, tag: String?): Context =
    if (tag == null) base
    else base.createConfigurationContext(
        Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(tag)))
        },
    )

@Composable
fun ProvideAppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    val base = LocalContext.current
    // API 33+ is system-applied, and SystemDefault has nothing to override.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || language.tag == null) {
        content()
        return
    }
    val wrapped = remember(base, language) { localizedContext(base, language.tag) }
    // EXACTLY these two, and no more. `LocalResources` MUST NOT be provided — see Finding A:
    // it is a computed local, and recomputation from these two IS the mechanism that carries the
    // override into all 111 `stringResource(...)` calls. Providing it would pin a stale `Resources`.
    // `LocalLocale`/`LocalLocaleList` CANNOT be provided — see Finding B.
    CompositionLocalProvider(
        LocalContext provides wrapped,
        LocalConfiguration provides wrapped.resources.configuration,
        content = content,
    )
}
```

## What the Compose-root override actually reaches — read from source

Settled against `ui-android-1.12.0-sources.jar`. Version confirmed first:
`./gradlew :app:dependencies --configuration debugRuntimeClasspath` resolves
`androidx.compose.ui:ui-android:1.12.0` — 1.11.0 is also in the Gradle cache and is **not** the
resolved version. All `file:line` references below are inside that sources jar.

### Finding A — `stringResource` DOES reach the override, via a *computed* local

Every `res/` helper reads `LocalResources.current`, never `LocalContext.current.resources`:
`res/StringResources.android.kt:35,48,60,73,87` (`stringResource`, `stringArrayResource`,
`pluralStringResource`). So the concern was well-founded — but the local resolves the right way:

```kotlin
// platform/AndroidCompositionLocals.android.kt:46-58
val LocalResources =
    compositionLocalWithComputedDefaultOf<Resources> {
        LocalConfiguration.currentValue
        LocalContext.currentValue.resources
    }
```

`LocalResources` is **never explicitly provided anywhere** in compose-ui — declared only at that
line and read only by the `res/*` helpers. The composition root
(`platform/ComposeViewContext.android.kt:491-506`) provides `LocalContext provides owner.context` and
`LocalConfiguration provides owner.configuration` and deliberately **not** `LocalResources`; its own
comment at `AndroidCompositionLocals.android.kt:48` explains that providing it would break invalidation.

Because the default is computed **at the read site** from `currentValue`, every `stringResource` call
below `CompositionLocalProvider(LocalContext provides wrapped, LocalConfiguration provides wrapped…)`
recomputes `Resources` from *the wrapped context*. **All 111 strings resolve in the overridden
language, and `LocalResources` must NOT be provided** — the computed path is the mechanism, not a
workaround around a missing provide.

### Finding B — `LocalLocale` does NOT reach the override, and cannot be made to

- `platform/CompositionLocals.kt:167-169` — `LocalLocale` computes from
  `LocalLocaleList.currentValue.first()`.
- `LocalLocaleList` is a read-only alias for the **private** `LocalProvidableLocaleList`, which *is*
  explicitly provided at the composition root from `owner.localeList`.
- `platform/AndroidComposeView.android.kt:633-643` —
  `override val localeList: LocaleList by derivedStateOf { ConfigurationCompat.getLocales(configuration) … }`,
  where `configuration` is the View's own `mutableStateOf(Configuration(context.resources.configuration))`
  at `:633`: the **Activity's** context, captured at view creation and refreshed only on
  `onConfigurationChanged`. Never the wrapped context.

Two firm consequences:

1. `LocalProvidableLocaleList` is private to compose-ui, so there is **no** "provide every
   locale-bearing local" option. `ProvideAppLocale` provides exactly two locals and no more.
2. `habit/ScheduleEditors.kt:228`'s `LocalLocale.current.platformLocale` would keep returning the
   **device** locale, so `DayOfWeekPicker`'s day names would stay English under a Spanish override.
   Changing it to `LocalConfiguration.current.locales[0]` is therefore a **required work item**, not a
   fallback. It also makes the app's two locale-reading formatters consistent — the same expression
   `core/ui/TimeOfDayFormat.kt:92` already uses. Traceability: without this change the spec's
   `Locale-Sensitive Formatting Follows The Resolved Language` scenario "Day-of-week names follow a
   Spanish override on an English device" fails.

**Recorded so a future reader does not "fix" this back:** `ScheduleEditors.kt:216-219`'s own KDoc
argues `LocalLocale` was chosen over `Locale.getDefault()` *precisely* so a per-app override would be
honoured. The intent was right; the chosen CompositionLocal does not deliver it, because it is fed
from the Activity's configuration rather than from the composition's `LocalConfiguration`. Reverting
to `LocalLocale` would silently re-break Spanish day names. The KDoc must be updated with this
evidence when the line changes.

### `TimeOfDayFormat` — reached, unchanged

`core/ui/TimeOfDayFormat.kt:87-93` keys `remember(context, configuration)` on **both**
`LocalContext.current` and `LocalConfiguration.current`. Overriding both re-keys the `remember` and
rebuilds `TimeOfDayFormat` with `configuration.locales[0]` = the override. No change needed.

## Testing Strategy

`createConfigurationContext` returns a stub under AGP's mockable-jar unit-test path (the same
type-resolution/stubbing class of trap already documented at `NotificationPoster.kt:134-137`), so
every wrapping assertion is **instrumented**, not unit. The matrix straddles the split: API 31 covers
the self-rolled branch, API 37 the native one. Use `assumeTrue(SDK_INT < 33)` / `>= 33` for the
branch-specific ones — complementary per-leg skips are already the established pattern
(`openspec/config.yaml:552-554`).

| Layer | Test | What it proves |
|---|---|---|
| Unit | `BackupImporterTest.kt` (edit `:52`, add `:59` payload assertion) | typed `ImportFailure` carries `fileVersion`, `habitId`, `slotId`; no message-string coupling remains |
| Unit | `TimeOfDayFormatTest` additions using the existing `Locale.forLanguageTag("es-ES")` (`:184`) | `es-ES` day-period text (`p. m.`) and hour-cycle independence |
| Unit | `NotificationPosterTest` | the 6 `canPost()` assertions stay as-is; the one `postReminder` assertion (`:96`) moves into `runTest` |
| Unit | `AppLanguageTest` (new, pure) | `AppLanguage.fromTag` round-trip; `SystemDefault.tag == null` |
| Unit | `StringResourceParityTest` (new, pure — **D9**) | The whole `Format-Argument And Plural Integrity Under Translation` requirement, all four scenarios: specifier-multiset parity per key across `values/` and `values-es/` (covers `today_slot_change_a11y`'s two args at `strings.xml:118`); key parity both directions modulo `translatable="false"`; the literal `%%` in `progress_compliance` (`:136`) in both files; `habit_delete_dialog_body` carrying Spanish `one` **and** `other`, each with `%1$d` (`:29-30`). Runs in `./gradlew check`, no emulator, fails naming the offending key |
| Instrumented | `AppLocaleInstrumentedTest` (new) | `localizedContext(ctx, "es").getString(R.string.notification_action_yes) == "Sí"`; `localizedContext(ctx, null) === ctx` |
| Instrumented | `LanguageOverrideComposeTest` (new) | **Regression protection, not discovery** — both mechanisms are now known from source (Findings A/B). It pins two things a future compose-ui bump could silently change: (1) `ProvideAppLocale(Spanish) { SnoozeSettingsScreen(...) }` renders "Ajustes", i.e. `LocalResources` is still computed from the provided `LocalContext`/`LocalConfiguration` and never explicitly provided; (2) a `DayOfWeekPicker` chip renders a Spanish short day name, i.e. Finding B's `LocalConfiguration.current.locales[0]` change is in place and nobody reverted it to `LocalLocale` |
| Instrumented | `LanguageOverrideStoreInstrumentedTest` (new) | tri-state: `set(Spanish)` stores `"es"`; `set(SystemDefault)` **removes** the key (below 33) / empties `applicationLocales` (33+); survives store re-creation |
| Instrumented | `SpanishColdProcessNotificationInstrumentedTest` (new) | **the headline test.** Seeds the override (below 33 via `ReminderSettingsDataStoreEntryPoint`, `DataStoreModule.kt:49-53`; on 33+ via `LocaleManager`), fires the byte-identical `ReminderFireReceiver` intent `CoreFlowTestFixture.fireArmedAlarmFor` already builds (`CoreFlowTestFixture.kt:163-166`), with **no Activity ever created**, and asserts the posted notification's text, its three action titles and the channel name are Spanish |
| Instrumented | existing `HabitEditorRotationComposeTest`, `TodayAdaptiveComposeTest`, `NotificationPosterInstrumentedTest`, `NotificationActionWiringInstrumentedTest`, `CoreFlowE2ETest` | regression guard for D8 (`enableEdgeToEdge`, `rememberSaveable` route state) and for the `suspend postReminder` signature change |

Honest limit: an instrumented test always has a live process.
`SpanishColdProcessNotificationInstrumentedTest` proves *"no Activity was ever created and the poster
resolved Spanish from the persisted tag"* — not literal post-process-death cold start, which stays a
manual `adb` check under `testing.instrumented.device_free_matrix.limits`.

Gates: `./gradlew check` (unit + lint + detekt) and `./gradlew :app:emulatorMatrixGroupDebugAndroidTest`.
`values/`↔`values-es/` drift is caught by `StringResourceParityTest` (D9), which runs inside `check`
and does not depend on any lint check ID being present. `compileDebugAndroidTestKotlin` must be part
of the loop — `androidTest` does not compile with the unit tests, and this change alters a signature 6
`androidTest` files construct or stub. Apply MUST additionally run `./gradlew :app:lint` once and read
the report to settle D9's candidate check IDs before writing any of them into the new `lint { }` block.

## Stack Confirmation

Confirmed against `openspec/config.yaml` `stack:` rather than assumed. `status: ratified`,
`still_unpinned: []` — unchanged. **No new dependency**; `gradle/libs.versions.toml` is untouched, and
in particular no `androidx.appcompat` entry is added. No module-boundary change: `:domain` gains
nothing and stays Android-free; `portability/`'s Android-free importer stays Android-free by
construction (D6). `stack.sdk.open_risk` (targetSdk 37 background/notification behaviour, never read
against authoritative docs) is leaned on by the cold-process path but is neither opened nor closed here.

## Threat Matrix

N/A — no routing change (`ConstanzaRoute` gains no case; `MainActivity.kt:78-96,156-158` already
routes `Settings`), no shell command, no subprocess, no VCS/PR automation, no executable-file
classification, and no new process-integration boundary. The `LocaleManager` call is an in-process
system-service call, and the manifest `<application>` attribute adds no exported component.

## Migration / Rollout

No data migration. One additive `stringPreferencesKey`; no Room schema change; no default changes for
any existing user (absent key = System default = today's behaviour). Single-shot rollout, no flag.

**Rollback.** Everything is additive except one clause: on API 33+ a `LocaleManager` override is a
*system* setting that survives a code revert, so the revert MUST also call
`setApplicationLocales(LocaleList.getEmptyLocaleList())` once, or the app ships English resources while
the system still forces `es`. Otherwise: deleting `res/values-es/strings.xml`, `res/xml/locales_config.xml`
and the manifest attribute restores exact pre-change behaviour, and a reverted DataStore key is simply
never read again. Keep D6 (the portability signature change) in its own commit so it can be reverted
independently of the localization work.

## Open Questions

No RESEARCH REQUIRED items remain. The CompositionLocal question that blocked `ProvideAppLocale`'s
body is settled from `ui-android-1.12.0-sources.jar` — see Findings A and B.

- [ ] **Q1 — non-blocking.** Does a per-app language change made in Android Settings while Constanza
      is backgrounded recreate the Activity, or kill the process? D2 makes the answer irrelevant (the
      picker re-reads on `ON_START` either way), so this is a nice-to-know, not a gate.

## Risks

| Risk | Mitigation |
|---|---|
| A future compose-ui version explicitly provides `LocalResources` at the composition root, or stops computing it from `LocalContext`/`LocalConfiguration` → all 111 strings silently revert to the device language while date/time formatting still looks correct | `LanguageOverrideComposeTest` pins a real `stringResource` render. This is internal-ish compose behaviour reached through a public computed local, so it is worth a test rather than a comment |
| `ScheduleEditors.kt:228` reverted to `LocalLocale` by a future reader following its current KDoc → Spanish day names silently regress | Rewrite that KDoc with Finding B's `file:line` evidence in the same commit as the line change, and assert a Spanish day name in `LanguageOverrideComposeTest` |
| `suspend postReminder` churn across 6 `androidTest`/`test` construction and stubbing sites | Explicit, no Kotlin default (D4). `compileDebugAndroidTestKotlin` in the loop; `ReminderFireWorkerTest.kt:84,90,106,128` need `coEvery`/`coVerify` |
| `android:localeConfig` at `minSdk 31` trips lint `UnusedAttribute` | `tools:targetApi="33"` on `<application>`; the `tools` namespace is already declared (`AndroidManifest.xml:3`) |
| `settings_snooze_title` repurposed rather than added → a stale reference renders the wrong heading | Rename the key to `settings_title`; a rename breaks the build instead of rendering wrong copy |
| Spanish action labels ("Sí", "Aplazar") change notification width/wrapping | Covered by the existing `NotificationPosterInstrumentedTest` shape on both matrix legs |
| Untranslated `app_name` trips lint `MissingTranslation` | `translatable="false"` in `values/strings.xml`; `StringResourceParityTest` honours that attribute so the two mechanisms cannot disagree |
| A translation reorders `%1$s`/`%2$s` at `strings.xml:118`, drops a positional index, or unescapes `:136`'s `%%` → runtime `IllegalFormatException` or a raw `%` on screen, on a screen the user reads daily | `StringResourceParityTest` (D9) fails in `./gradlew check` naming the key. Deliberately not delegated to lint, whose relevant check IDs are unconfirmed and whose findings do not fail this build today |
| D9's `lint { }` block names a check ID the installed AGP does not ship → build configuration error, or false confidence | The block carries no ID until `./gradlew :app:lint` confirms it; D9's guarantee rests entirely on the unit test, so dropping every candidate ID changes nothing load-bearing |
