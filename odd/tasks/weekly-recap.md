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

## Tasks

| ID | Specs | Route | Status | Commit |
|----|-------|-------|--------|--------|
| T1 | S1, S6 | inline | todo | |
| T2 | S4, S5, S6 | inline | todo | |
| T3 | S2, S3, S4 | inline | todo | |

- T1: `:domain` `WeeklyRecapCalculator` + tests.
- T2: week loader, `WeeklyRecapViewModel`, `WeeklyRecapScreen`, route + notification deep link (cold start and `onNewIntent`), strings en/es.
- T3: Monday 09:00 alarm → receiver → worker → poster on its own channel, re-armed on boot/time change/package replace and app start.

## Log

- **L1** (2026-10-22, owner): "Quiero una nueva funcionalidad para hacer una especie de recap semanal de cómo han ido los hábitos y cuáles he cumplido y cuál no. A lo largo de la semana. No sé cómo podría ser el diseño. Y cómo podría llegarme la notificación de alerta o similar para que por ejemplo al principio de la semana siguiente (lunes por la mañana), o final de la semana (domingo por la noche), por me llegue notificación, que en la notificación me resuma el % de cumplimiento semanal de hábitos, y al pulsarle me lleve a una pantalla de detalle, que me permita ver de forma ágil el listado de hábitos, y para cada hábito cuantas veces lo he cumplido y cuantas no, con un resumen total como sea."
- **L2** (owner): "lunes por la mañana".
- **L3** (owner): "la B, adelante".
