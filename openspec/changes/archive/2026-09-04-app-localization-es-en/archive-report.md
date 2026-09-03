# Archive Report: app-localization-es-en

**Archived**: 2026-09-04
**Landed on `main`**: `549f7db`
**Verdict carried in**: pass with findings (see `verify-report.md`)

## What shipped

Constanza speaks English and Spanish. On a fresh install it follows the device language; any other
language falls back to English; and Settings carries a three-option picker that overrides both. On
API 33+ the same override is a real system setting, so the language is also changeable from Android
Settings > Apps > Language.

Installed and confirmed on the maintainer's Galaxy S25 (SM-S931B, API 36) from a signed release
build.

## Specs merged

| Capability | Change |
|---|---|
| `app-localization` | **New.** 9 requirements, 15 scenarios. Created at `openspec/specs/app-localization/spec.md` |
| `reminder-response` | `Notification Actions` gains the language guarantee for the channel name, body and all three action labels, including from a cold process; new cold-process scenario. Every prior guarantee preserved verbatim |
| `data-portability` | `Import` gains the language guarantee for rejection feedback plus the typed-failure constraint that follows from the importer being Android-free; new scenario. Every prior guarantee preserved verbatim |

## Delivery

Four slices, reviewed and merged as PRs #68, #69, #70, #71, plus a recovery PR #72.

| PR | Contents |
|---|---|
| #68 | `values-es/strings.xml`, `locales_config.xml` + manifest, `StringResourceParityTest` |
| #69 | `AppLanguage`/`AppLocale`/`AppLocaleController`, DataStore key, `suspend postReminder` |
| #70 | `ProvideAppLocale` at the Compose root, the `ScheduleEditors` fix, the picker |
| #71 | `ImportFailure` typed errors in portability |
| #72 | Recovery — see below |

**#72 exists because of a bad instruction, not a bad merge.** The handover said GitHub would
automatically retarget the stacked children to `main` once #68 merged. It does not: GitHub
retargets a child PR only when its base branch is *deleted* on merge. `feat/app-localization-resources`
survived, so #69 and #71 merged into it and #70 merged into `feat/app-localization-mechanism`. Every
PR showed MERGED while only #68 had reached `main`.

Recovery was a landing branch cut from `main` that merged both surviving heads — no cherry-picks and
no rewritten history, so the original commits landed exactly as reviewed. Both merges were clean.

Two consequences carried forward, both already applied:
- The repository now has **Automatically delete head branches** enabled (`deleteBranchOnMerge: true`),
  which removes the trap at the source.
- The same failure had occurred once before on PRs #45/#46. The handover note has been rewritten from
  a preference into a hard rule: default to independent PRs off `main`, and when stacking is genuinely
  required, name the retarget as an explicit step with an owner.

## Decisions worth keeping

- **AppCompat was rejected**, and the reasons are in `design.md`. On API 31/32 its backport requires
  migrating `MainActivity` off `ComponentActivity`, reversing the decision recorded at
  `res/values/themes.xml:6` — and even then it patches only the Activity context, not the Application
  context the cold-process reminder path uses. It would have cost a migration and still failed the
  case that mattered most.
- **One store per API level.** `LocaleManager` on 33+, DataStore below. The other is never written.
  Two stores holding the same language drift silently; one per level cannot.
- **`LocalResources` must never be provided.** It is a computed local that recomputes from
  `LocalContext`/`LocalConfiguration` at each read site, which is exactly what makes the Compose-root
  override reach `stringResource`. Providing it would pin a stale `Resources`.
- **`LocalLocale` cannot be reached** from a Compose-root override — its backing local is private to
  compose-ui and is fed from the Activity's configuration. `ScheduleEditors.kt` now reads
  `LocalConfiguration.current.locales[0]`. Its KDoc had argued the opposite; it was rewritten in the
  same commit, and a test fails if the line is reverted.
- **Rollback has one non-additive clause.** On API 33+ the override is a *system* setting that
  outlives a code revert: rolling back must also clear it with
  `setApplicationLocales(emptyLocaleList)`, or the app ships English resources while the system still
  forces `es`.

## Carried-forward open items

None of these blocks the change; all four are recorded in `verify-report.md` with the reason each was
left open.

1. No test drives a Spanish **device** locale with no override — requirements 1-3 rest on Android's
   own resource resolution, and the emulator matrix runs both legs in English.
2. API 33+ system-Settings parity is verified in one direction only; a change made from the system
   side and observed in the in-app picker is unexercised.
3. `SpanishColdProcessNotificationInstrumentedTest` proves "no Activity ever created", not literal
   post-process-death cold start, which remains a manual `adb` check.
4. `importFailureMessage`'s four-branch mapping has no test; exhaustiveness is compiler-enforced,
   branch correctness is not.

Item 4 is the cheapest to close and the most user-visible if wrong.

## Lessons this change is worth remembering for

- **`BUILD SUCCESSFUL` is not evidence.** A matrix run reported success with both emulator tasks
  `UP-TO-DATE` and zero instrumented tests executed. Confirm the executed-task count and read the
  JUnit XML.
- **Debug green says nothing about release.** R8 and resource shrinking run only on release, so the
  signed APK was inspected with `aapt2 dump resources` to confirm 151 `(es)` resources actually
  shipped.
- **A KDoc asserting a design intent is not evidence that the API delivers it.** `ScheduleEditors`
  reasoned correctly toward the wrong API and had been wrong in the repository for months.
- **A design document can describe its own test accurately and still overclaim which scenarios that
  test satisfies.** `StringResourceParityTest` parses XML; it cannot exercise CLDR plural selection.
  A separate runtime test now does.
