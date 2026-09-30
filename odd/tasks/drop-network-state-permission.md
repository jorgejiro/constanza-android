# Drop ACCESS_NETWORK_STATE and ship 1.0.0

## Objective

Answer the F-Droid reviewer on MR !50477 (2026-09-30): `ACCESS_NETWORK_STATE` "looks unnecessary
for an app described as fully offline". Remove it, ship 1.0.0 (vc18) to GitHub, Play and the MR.

## Why

The permission is not ours: `androidx.work:work-runtime:2.11.2` declares it for `NetworkType`
constraints (manifest-merger report). No `Constraints`/`NetworkType`/`ConnectivityManager` use exists
in `app/src`. `WAKE_LOCK` and `FOREGROUND_SERVICE` come from the same library and stay: the reviewer
did not question them and removing them is not proven safe.

## Scope

- In: `tools:node="remove"` in the app manifest, bump 1.0.0/18 (decided on 2026-09-28), changelogs
  `18.txt` (en-US, es-ES), privacy page in `vps` (permissions + false "release notes screen" claim),
  GitHub Release, Play production upload, F-Droid recipe build block and MR comment.
- Out: WAKE_LOCK / FOREGROUND_SERVICE.

## Checks

TDD: off (no project/session setting). Runner: Gradle.
`:app:testDebugUnitTest :app:detektMain :app:compileDebugAndroidTestKotlin`,
`:app:emulatorMatrixGroupDebugAndroidTest` (API 31 + 37), `aapt2 dump permissions` on the release APK.

## Tasks

- [ ] T1 · Manifest removal + bump + changelogs, checks green. Route: inline (3 mechanical files).
- [ ] T2 · Privacy page updated and deployed before the Play upload. Route: inline (1 file, vps).
- [ ] T3 · PR merged, tag `v1.0.0`, GitHub Release `constanza-1.0.0.apk`. Route: inline.
- [ ] T4 · Play production upload via `fastlane subir`. Route: inline.
- [ ] T5 · Recipe build block for 1.0.0 in `docs/fdroid` and the fork branch, MR comment. Route: inline.

## Evidence

- T1 · release APK permissions: SCHEDULE_EXACT_ALARM, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED,
  WAKE_LOCK, FOREGROUND_SERVICE (no ACCESS_NETWORK_STATE); badging vc18 / 1.0.0. Unit tests, detekt,
  androidTest compile green. Matrix: pending.
- T2 · vps `5a44b47`, deployed, live page carries the new text (curl).
