# Graphite release (1.1.0)

## Objective
Ship the merged graphite redesign (PRs #140–#152) as a new release with a green `./gradlew check`, fresh store screenshots and an installable signed APK.

## Problem / why
- `./gradlew check` is red: 16 plain-detekt findings in test code (pre-existing before 2af4246) and plain `detekt` crashes under Android Studio's JBR Java 25 (`--jvm-target 25` unsupported by detekt 1.23.8).
- Play / F-Droid screenshots and graphics still show the old colourful UI.
- The redesign (including Room v8 colour remap) only reaches devices with a release.

## Scope (authorized by owner 2026-10-04, order chosen by owner: detekt → screenshots → release)
- R1 Make `./gradlew check` green under the project's usual JDK (JBR) without lowering rules: fix the 16 findings; pin detekt's jvmTarget so it no longer follows the running JDK.
- R2 Regenerate phone/7"/10" screenshots (es-ES, en-US) with the existing pipeline (`docs/store-assets/generar-capturas/`), check the feature graphic, review every image.
- R3 Release: version bump PR, signed `assembleRelease` via `con-claves`, apksigner + aapt2 verification, `constanza-<version>.apk` GitHub Release with notes, anonymous HTTP 200 check; Play listing upload of screenshots/notes via fastlane (owner sends to review); F-Droid picks the tag automatically (`UpdateCheckMode: Tags`).

## Version
1.1.0 (versionCode 19): a visible redesign and a DB migration justify a minor bump. (Precedent was patch bumps pre-1.0; 1.0.0 is the first public version.)

## Tasks
- [x] R1 detekt green. Route: delegated writer + parent (daemon JDK pin inline, one mechanical build change).
- [ ] R2 store screenshots. Route: delegated.
- [ ] R3 release 1.1.0. Route: delegated + parent verification.

## Delivery
One PR per task, merged by the owner in order. Slicing budget ~900 lines. RDD disabled by owner.

## Progress / evidence
- R1 (partial, blocked):
  - `010e517` test: clear pre-existing detekt findings (16 → 0: 14 MaxLineLength wrapped, NestedBlockDepth in ContrastingInkTest replaced by flatMap + minBy, LongParameterList on TodayViewModelTest.buildViewModel suppressed with justification).
  - `55e7e28` build(detekt): pin jvmTarget = "11" on every Detekt / DetektCreateBaselineTask in :app and :domain. Removes the `Invalid value (25) passed to --jvm-target` crash.
  - Blocker found: under JBR 25 every detekt task (including `detektMain`) then fails with `IllegalArgumentException: 25.0.3` from `JavaVersion.parse` in detekt 1.23.8's embedded Kotlin 2.0.21 compiler. Pinning jvmTarget cannot fix that; detekt itself cannot run on Java 25. `--no-build-cache` is required to see it (a cache hit from a JDK 21 run masks it).
  - Evidence: JDK 21 (`~/.gradle/jdks/eclipse_adoptium-21…`) `clean check assembleDebug`: BUILD SUCCESSFUL, 0 detekt findings. JBR 25 `clean check --no-build-cache`: BUILD FAILED, `:app:detekt`, `:domain:detekt`, `:domain:detektMain` with `25.0.3`.
  - README.md:175 / README.es.md:178 say "the Gradle daemon downloads it if missing", but the repo has no `gradle/gradle-daemon-jvm.properties`; that claim is currently unbacked.

  - Blocker resolved: `settings.gradle.kts` applies `org.gradle.toolchains.foojay-resolver-convention` 1.0.0 and `./gradlew updateDaemonJvm --jvm-version=21` generated `gradle/gradle-daemon-jvm.properties` (toolchain 21 + download URLs). The daemon now runs on JDK 21 whatever JAVA_HOME says (observed process: `~/.gradle/jdks/eclipse_adoptium-21…/bin/java` while JAVA_HOME = JBR 25). README "the Gradle daemon downloads it if missing" is now true.
  - Evidence (JAVA_HOME = JBR 25): `clean check --no-build-cache` → BUILD SUCCESSFUL, app unit 373 tests / 0 failures, domain 0 failures; `:app:detekt :domain:detekt :app:detektMain :domain:detektMain --rerun-tasks --no-build-cache` → all four executed, BUILD SUCCESSFUL.

## Next step
R2 store screenshots.
