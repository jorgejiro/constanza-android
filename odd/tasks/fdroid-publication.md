# Feature: publish Constanza on F-Droid

## Objective

Get Constanza into the main F-Droid catalogue: the repository meets F-Droid's build requirements, the
`fdroiddata` recipe is written and linted, and the inclusion merge request is open.

## Why

Constanza is MIT, has no INTERNET permission, no accounts and no Google services: F-Droid's audience
is exactly the one that values that. Same path already taken by sleep-noise-android (MR !50449),
bebe-agua-android (!50455) and aquihaytomate-pomodoro-android (!50458).

## Scope

- In: drop AGP's Google-encrypted dependency block from the APK/AAB (`dependenciesInfo`), the recipe
  `docs/fdroid/com.jjrapps.constanza.yml`, a Spanish guide `docs/fdroid/LEEME.md`, the merge request
  to `gitlab.com/fdroid/fdroiddata` from the fork `jorgejiro/fdroiddata` (glab, authenticated as
  jorgejiro).
- The MR description explains what sets Constanza apart from habit trackers already on F-Droid
  (owner's request), with claims checked against those apps rather than assumed.
- Out: reproducible builds signed with the developer key. F-Droid signs; Play and F-Droid installs
  cannot update each other.

## Constraints

- Already met, verified 2026-09-28: repo public; fastlane listing (en-US, es-ES, changelog 17,
  icon, feature graphic, screenshots) present at tag `v0.1.16`; signing config optional
  (`findByName`); no foojay plugin, no `gradle-daemon-jvm.properties`; no Google Play Services; no
  committed binaries besides `gradle-wrapper.jar`.
- The recipe `commit:` must contain both `fastlane/` and the `dependenciesInfo` change. `v0.1.16`
  predates the latter, so the recipe pins the full hash of the build-fix commit. This branch must be
  merged with a merge commit (repo convention), never squashed, or that hash disappears from `main`.
- Lesson from aquihaytomate: verify `git ls-tree -r --name-only <sha> | rg ^fastlane/` is non-empty.
- Categories: `Habit Tracker` (from fdroiddata `config/categories.yml`).

## TDD

Not applicable: build configuration and metadata only. Checks: `./gradlew :app:assembleRelease`
without `keystore.properties`, `./gradlew testDebugUnitTest`, `fdroid lint` and `fdroid rewritemeta`
on the recipe, then the MR pipeline.

## Tasks

- [x] T1 · `dependenciesInfo { includeInApk = false; includeInBundle = false }` in
  `app/build.gradle.kts`; unsigned `assembleRelease` builds. Route: inline (one mechanical file).
- [x] T2 · Recipe + `LEEME.md`, linted. Route: inline (adapted from sleep-noise-android).
- [ ] T3 · Push branch, open GitHub PR; fork branch `com.jjrapps.constanza` in `jorgejiro/fdroiddata`,
  commit `metadata/com.jjrapps.constanza.yml`, open MR `New app: Constanza` with the differentiators.
  Route: inline (glab/gh state operations).
- [ ] T4 · MR pipeline green or failures explained. Route: inline.

## Progress

- T1 · `4b1d379`. `:app:assembleRelease` without `keystore.properties` produces
  `app-release-unsigned.apk`; `testDebugUnitTest` green. RDD assess: medium, `under_budget`, no
  review due.
- T2 · `cb125c4`. Recipe pins `4b1d379` (contains 56 `fastlane/` files). `fdroid lint -f`
  (fdroidserver 2.4.5, fdroiddata `config/categories.yml`) clean; `fdroid rewritemeta` leaves it
  unchanged.

## Next step

T3.
