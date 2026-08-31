# constanza-android
Constanza is an Android habit tracker app

## Building

This repository targets the `android-37` platform (`compileSdk`/`targetSdk` = 37, `minSdk` = 31).
AGP downloads the `android-37.0` SDK platform automatically on first build if not already installed.

```bash
./gradlew assembleDebug   # build the debug APK
./gradlew :domain:test    # :domain JVM unit tests (JUnit4)
./gradlew check           # both modules' tests + the detekt clock-access rule (see below)
```

### Linting

`./gradlew detekt` alone is a no-op for the clock-access rule: it is PSI-only and never resolves
fully qualified call targets. Run `./gradlew detektMain` (already wired into `:domain`'s `check`)
to actually enforce it.
