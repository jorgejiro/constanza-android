# Day review toggle + morning recap

Objective: move the nightly day review to 23:30, make it only fire when something is unanswered and let it be switched off; add a 07:00 morning notification with yesterday's completion that opens yesterday on Today.
Branch: `feat/day-review-toggle-and-morning-recap` · Delivery: stacked PRs to main (#162 → #163 → #164) · Tests: `./gradlew :domain:test :app:testDebugUnitTest :app:detektMain :app:compileDebugAndroidTestKotlin`

## Specs

- **S1** Day review default time: "sí cambialo a las 23:30h como hora por defecto". `DEFAULT_REVIEW_TIME_MINUTE_OF_DAY` becomes 23:30; the 23:45 ceiling stays.
- **S2** Day review on/off: "que se pueda desactivar si no lo quieres (activado por defecto)". A switch in Settings → Day review; disabled = no day-review notification is posted (the alarm keeps re-arming, same as the weekly recap's switch). Assumption: the time and mode rows are hidden while it is off.
- **S3** Day review default mode: "sólo debe avisar si quedan hábitos por responder". `DEFAULT_REVIEW_FIRES_EVERY_NIGHT` becomes `false`; the mode option stays in Settings. Persisted values chosen by the owner earlier still win over the new defaults.
- **S4** Morning notification: "al día siguiente a primera hora (07:00am) me llegue otra notificación que me avise del grado de cumplimiento de hábitos del día anterior".
- **S5** Tap target: "al pulsarlo me lleve al listado de hábitos del día anterior para ver de forma ágil los que cumplí y los que no". Opens Today navigated to the notification's date (yesterday relative to when it fired), not to "yesterday" relative to the tap.
- **S6** Settings: "activado por defecto, y se puede cambiar la hora y activar/desactivar en ajustes." Default 07:00.
- **S7** Counting (assumption): per habit due yesterday, from Today's own rows; slots SKIPPED are ignored; a habit with every slot skipped is left out; done = every remaining slot COMPLETED; anything else (MISSED, still UNKNOWN) is not done. No notification when nothing is left to count. Late-fire guard: the run posts nothing if it fires on a later date than it was armed for.

## Tasks

| ID | Specs | Route | Status | Commit |
|----|-------|-------|--------|--------|
| T1 | S1, S2, S3 | inline | done (RED→GREEN: store 3, VM 1, worker 1, section compose 2) | 0e04e05 |
| T2 | S4, S6, S7 | inline | done (RED→GREEN: summary 5, store 3, publisher 3, worker 2) | e50f533 |
| T3 | S5, S6 | inline | done (RED→GREEN: showDay 2, VM 3, section compose 2) | see L4 |

- T1: `DayReviewSettingsStore` defaults + `reviewEnabled`, worker skip, Settings switch, strings en/es, tests.
- T2: `summarizeDay`, `MorningRecapSettingsStore` (enabled + time), alarm → receiver → worker → poster on its own channel, re-armed on boot/time change/package replace/app start.
- T3: Settings section (switch + time), notification deep link to the date on Today (cold start and `onNewIntent`).

Route evidence: no writer trigger — the units share `RescheduleReceivers`, `ConstanzaApplication`, `MainActivity`, the settings screen and strings, so they are not disjoint; inline.

## Log

- **L1** (2026-10-24) Original request: "Buenas, quiero hacer un cambio en la app, pues la notificación de por la noche (creo a las 23h) que me dice las tareas que he informado y las que no, y me lleva a un resumen del día, quiero que se quede a las 23:30 y sólo aparezca en el caso que me quede algún hábito por marcar. Y al día siguiente a primera hora (07:00am) me llegue otra notificación que me avise del grado de cumplimiento de hábitos del día anterior, y que al pulsarlo me lleve al listado de hábitos del día anterior para ver de forma ágil los que cumplí y los que no."
- **L2** (2026-10-24) Owner, after learning time and mode were already settings: "sobre el 1, sí cambialo a las 23:30h como hora por defecto, y que se pueda desactivar si no lo quieres (activado por defecto), pues me voy a poner un hábito para las 23h para iniciar relax para dormir, y sólo debe avisar si quedan hábitos por responder, no sé si es lo que hace ahora por defecto. y sobre el 2, todo OK; activado por defecto, y se puede cambiar la hora y activar/desactivar en ajustes."
- **L3** Finding: the current default mode is "every night" (`DEFAULT_REVIEW_FIRES_EVERY_NIGHT = true`), so S3 changes a default. `TodayViewModel` sits at detekt's `TooManyFunctions` ceiling (10), so the deep link generalises `showToday()` into `showDay(date: LocalDate? = null)` instead of adding an 11th function.
- **L4** (2026-10-24) T1–T3 implemented inline. `./gradlew :domain:test :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:detektMain` green (app 410 tests, domain 65). detekt `LongMethod` on `ConstanzaApp` resolved by extracting `OpenTodayEffect`, shared by the reminder tap (live edge) and the morning-recap tap (its date). README (en/es) and store listing texts updated from "23:00". Pending: device-free emulator matrix (`:app:emulatorMatrixGroupDebugAndroidTest`).
- **L5** (2026-10-24) `.atl/` skill-registry files had been committed by mistake in 0e04e05; untracked again in a follow-up commit (files kept on disk). RDD assess over `2e56ca7..HEAD` (committed-only, untracked excluded): risk `medium`, `review_due: false` (`under_budget`) → no native review started. Risk line: none of items 1–6 (new preference keys only; changed defaults are requested; `showToday()`→`showDay()` is internal).
- **L6** (2026-10-24) Emulator matrix: api31 237/237 green (3 skipped). api37: `CoreFlowE2ETest` fails 3 tests (a1, a2 permission dialog never reached; delete/remove habit "not in the database") with an ANR "Close app / Wait" dialog in the foreground. Reproduced identically on `main` (2e56ca7) in a scratch worktree → pre-existing on api37, not caused by this change. All other api37 tests pass.
- **L7** (2026-10-24) Owner chose "3 PRs encadenados". Slices: #162 `feat/day-review-23-30-toggle` (T1, 274 lines) → #163 `feat/morning-recap-core` (T2, 587 lines, size exception requested: one alarm→worker→notification unit, about half tests/KDoc) → #164 `feat/morning-recap-settings-and-tap` (T3 + ODD log, 404 lines). Branches rebuilt from the original commits without the stray `.atl/` files; the stack's final tree equals the tested one; #162 and #163 verified on their own (394 / 405 app unit tests, detekt green).
- **L8** (2026-10-24) Released as 1.2.0 (versionCode 21): bump PR #166, GitHub Release `v1.2.0` on d22bc1c with `constanza-1.2.0.apk` (same certificate as 1.1.1, SHA-256 `59659af6…64c912d0`; same six permissions; anonymous download 200). Play: the Play service-account key is not on the MacBook Air M1 nor in Bitwarden, so the owner uploaded from the other Mac. F-Droid left to the `UpdateCheckMode: Tags` bot. api37 E2E reliability fixed separately in #165. Still not observed on a device: the 07:00 morning notification and its tap, and the 23:30 night review's new default.
