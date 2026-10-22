# Weekly recap

Objective: a Monday-morning notification with last week's completion %, opening a per-habit week screen (design option B).
Branch: `feat/weekly-recap` · Delivery: single PR (merge commit) · Tests: `./gradlew :domain:test :app:testDebugUnitTest :app:detektMain :app:compileDebugAndroidTestKotlin`

## Specs

- **S1** Recap content, per the owner: "una especie de recap semanal de cómo han ido los hábitos y cuáles he cumplido y cuál no. A lo largo de la semana."
- **S2** Notification timing: "lunes por la mañana" (owner's choice over Sunday night). Assumption: 09:00 local time, fixed, covering the previous Monday–Sunday.
- **S3** Notification body: "que en la notificación me resuma el % de cumplimiento semanal de hábitos".
- **S4** Tap target: "al pulsarle me lleve a una pantalla de detalle, que me permita ver de forma ágil el listado de hábitos, y para cada hábito cuantas veces lo he cumplido y cuantas no, con un resumen total como sea."
- **S5** Design: option B "Semana día a día" from canvas https://claude.ai/artifact/HaHr7EHEJ6DoYojqfZbYed — header with week range, `NN % · X de Y`, progress line; per habit a dot + name, a Monday–Sunday row of ✓ / ✕ marks, and `done/total` on the right; legend under the list.
- **S6** Counting (assumption, matches `ComplianceCalculator`): done = COMPLETED, not done = MISSED, SKIPPED/UNKNOWN/not-due excluded; `NTimesPerWeek` counts `min(completed, times)` of `times`. Habits with nothing to count that week are left out; no notification when the week has nothing to count.
- **S7** In-app access: "añade el acceso desde la app". Assumption: a third icon in Today's header (between habit list and settings) opening last week's recap.
- **S8** Setting: "añade una opción en las opciones de configuración, para habilitar / deshabilitar esta notificación semanal (por defecto estará activada)." Disabled = no recap notification is posted; the screen stays reachable from the app.

## Tasks

| ID | Specs | Route | Status | Commit |
|----|-------|-------|--------|--------|
| T1 | S1, S6 | inline | done (RED→GREEN, 8 tests) | cfdfc02 |
| T2 | S4, S5, S6 | inline | done (loader 3 tests) | 397d858 |
| T3 | S2, S3, S4 | inline | done (schedule 6 tests; api31+api37 7/7) | 2f9792b |
| T4 | S7 | inline | done (VM 3 tests, header compose test) | see L8 |
| T5 | S8 | inline | done (store 2, publisher 3, section compose 2) | see L8 |

- T1: `:domain` `WeeklyRecapCalculator` + tests.
- T2: week loader, `WeeklyRecapViewModel`, `WeeklyRecapScreen`, route + notification deep link (cold start and `onNewIntent`), strings en/es.
- T3: Monday 09:00 alarm → receiver → worker → poster on its own channel, re-armed on boot/time change/package replace and app start.

## Log

- **L1** (2026-10-22, owner): "Quiero una nueva funcionalidad para hacer una especie de recap semanal de cómo han ido los hábitos y cuáles he cumplido y cuál no. A lo largo de la semana. No sé cómo podría ser el diseño. Y cómo podría llegarme la notificación de alerta o similar para que por ejemplo al principio de la semana siguiente (lunes por la mañana), o final de la semana (domingo por la noche), por me llegue notificación, que en la notificación me resuma el % de cumplimiento semanal de hábitos, y al pulsarle me lleve a una pantalla de detalle, que me permita ver de forma ágil el listado de hábitos, y para cada hábito cuantas veces lo he cumplido y cuantas no, con un resumen total como sea."
- **L2** (owner): "lunes por la mañana".
- **L3** (owner): "la B, adelante".
- **L4** Verification: `:domain:test`, `:app:testDebugUnitTest` (382+), `detekt`, `detektMain`, `compileDebugAndroidTestKotlin` green. On api31 and api37: `WeeklyRecapNotificationPosterInstrumentedTest` 2/2, `WeekRangeLabelInstrumentedTest` 4/4, `GraphiteScreensRealRenderTest#renderWeeklyRecap` rendered and checked against board B. The first render showed "October 12 – 18" on a Spanish screen (`DateUtils.formatDateRange` reads the JVM default locale for month names); replaced by `getBestDateTimePattern` + explicit locale and pinned by a test.
- **L5** Not verified: a real Monday alarm firing, and a notification tap opening the screen (cold start and warm). Not done: no in-app entry point to the recap besides the notification; recap time is fixed at 09:00.
- **L6** (owner, after PR #158 merged): "sí, añade el acceso desde la app, y añade una opción en las opciones de configuración, para habilitar / deshabilitar esta notificación semanal (por defecto estará activada)." Branch `feat/weekly-recap-entry-and-toggle`.
- **L7** (owner): "y una vez tengas todo, integra o pásame el PR a revisar y aceptar por mi parte" — open the PR and leave the merge to the owner.
- **L8** T4+T5 verification: app unit tests 390/0 failures, detekt + detektMain green; full `emulatorMatrixGroupDebugAndroidTest` api31 and api37 460 tests, 0 failures (skips unchanged: 6 / 12, API-gated). Renders checked: Today header shows list · bars · settings; Settings shows "Resumen semanal / Aviso del lunes" switched on. Disabling skips the read and the post; the alarm keeps cycling. PR left for the owner to merge (L7).
