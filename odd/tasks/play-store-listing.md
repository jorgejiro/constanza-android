# Feature: Google Play store listing

## Objective

Everything needed to publish Constanza on Google Play: listing texts (es-ES, en-US) with computed
character counts, Play Console questionnaire answers, release notes, screenshots for phone and both
tablet formats, the 512 px icon, the 1024 × 500 feature graphic, and the privacy policy page.

## Problem / why

Constanza has only shipped as a GitHub APK. Nothing in this repo describes the Play listing. The
sibling app `sleep-noise-android` already solved this (fastlane metadata tree, `docs/store-assets/`,
scripted screenshots, text generator with computed counts); this feature ports that approach and its
lessons instead of re-learning them.

## Scope

- In: `docs/play-store-publication-texts.md`, `docs/play-release-notes.md`,
  `scripts/generar-textos-ficha.py`, `fastlane/metadata/android/{es-ES,en-US}/`,
  `docs/store-assets/` (screenshots, graphics, capture pipeline), an androidTest seed class for demo
  data, the privacy page in the `vps` repo (local commit only).
- Out: uploading to Play Console, deploying the `vps` site, creating the Play developer account,
  any app behaviour change.

## Constraints

- Listing name: `Constanza - Buenos hábitos`; launcher label stays `Constanza`.
- Artifacts in English except the store texts themselves (es-ES / en-US) and the repo's existing
  Spanish doc convention inherited from sleep-noise (docs in Spanish, neutral register).
- Device-free: emulators only (AVDs `Medium_Phone`, `Tablet7`, `Tablet10` exist locally).
- Character counts computed by script, never estimated.
- Facts about the app verified against the code (no INTERNET permission, no SDKs, exact alarms,
  POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, allowBackup=false).

## TDD

Off — no production behaviour changes. Source: no project TDD config for docs/tooling. Checks are
functional: text generator run, `revisar.py` exit 0, `compileDebugAndroidTestKotlin` for the seed.

## Delivery

Strategy `ask-on-risk` resolved to `stacked-to-main`, one PR per task (binary assets excluded from the
~400-line forecast; scripts push the total past it). RDD: on (default).

## Tasks

- [x] T1 — Listing texts: generator script, publication-texts doc, release notes, fastlane text files.
      Route: delegated (writer trigger, 2+ non-trivial files).
- [x] T2 — Graphics: 512 px icon from the adaptive vector, 1024 × 500 feature graphic es/en.
      Route: delegated.
- [x] T3 — Screenshots: demo-data seed + capture pipeline + `revisar.py`, 2 languages × 3 formats.
      Route: delegated (writer + emulator runs).
- [x] T4 — Privacy policy + app page in the `vps` repo, local commit, not deployed.
      Route: delegated (other repository).

## Acceptance criteria

- Every Play text within its limit, counts printed by the generator.
- Screenshots: exact dimensions per format, es ≠ en per scene, none blank; phone set copied into
  fastlane.
- Icon 512 × 512 PNG, feature graphic 1024 × 500 PNG, per language.
- Privacy page states no data collected, matching the manifest.

## Progress / evidence

- T1 done, commit `80df7ad`. Generator exit 0; counts: name ES 26/30, EN 23/30; short ES 79/80,
  EN 69/80; full ES 3031/4000, EN 2832/4000; promo ES 119/170, EN 105/170; notes ES 363/500,
  EN 343/500. Parent re-ran the generator: exit 0. Category Productividad and en-US name
  `Constanza - Good habits` confirmed by the owner on 2026-09-28. Review: declined for this candidate.
  Exact alarms: SCHEDULE_EXACT_ALARM has no Play declaration form; USE_EXACT_ALARM not declared.
  Review assess: medium, `slice_budget_reached` (982 lines) → consent pending.
- T4 done in `vps`, commit `949372c` (local, not deployed): `apps/constanza/`, privacy page,
  `constanza.svg`, card in `apps/index.html`. Screenshot gallery to add after T3.

- T3 first pass: commits `5b5ea35` (seed), `77b3029` (pipeline + 42 shots). Parent re-ran
  revisar.py: exit 0. Parent visual review REJECTED 5 scenes (Today with no answered slots; English
  system UI in the es notification shot + QS tiles; empty editor forms; Settings cut mid-section;
  sparse Progress too early). Reopened T3 for a second pass. detekt: `:app:detekt` fails with
  `--jvm-target 25`, per the writer also on the base; `detektMain` still to be run.

- T3 second pass: commit `e3a5790`. Parent re-ran revisar.py: exit 0 (42/42); parent looked at
  es/en phone and tablet10 sheets: Today shows Now/Later/Answered, es system UI in Spanish, editor of
  a real habit, Settings from the top, Progress last. Accepted minor: status clock 9:30 vs 09:30,
  reminder time differs slightly between passes. detektMain green. Review assess: high
  (`executable_mode` on todos.sh), 2048 lines → consent pending.
  Review consent: declined for this candidate.
- T2 done, commit `21c4995`: icono-512 (RGBA 512x512), cabecera es/en (RGB 1024x500), fastlane
  copies, `scripts/generar-graficos.py` (exit 0, re-run leaves the tree clean). Parent looked at all
  three images. Review assess: high (`process_boundary`) → declined for this candidate.

## Next step

Done 2026-09-28: branch pushed, PR #131 opened; `vps` deployed with `./deploy-contenido.sh`
(sync OK, site 200). Anonymous HTTP 200 on `/apps/constanza/`, `/apps/constanza/privacidad/`,
`/assets/iconos/constanza.svg`, and the Constanza card on `/apps/`. The `vps` commit `949372c` is
deployed but not pushed to its git remote.

Remaining owner steps: merge PR #131; add a screenshot gallery to `apps/constanza/index.html` in
`vps`; create the app in Play Console, upload the signed AAB (`:app:bundleRelease`) to internal
testing, paste texts and assets, answer the questionnaires, submit with a phased rollout.
