package com.jjrapps.constanza.seed

/**
 * Marks an `androidTest` class as a manual, on-device SEEDING FIXTURE rather than a behavioural
 * test. Anything carrying this annotation writes to the app's real on-device database and arms real
 * alarms, so it must never run as part of ordinary verification.
 *
 * `app/build.gradle.kts` passes this annotation's fully-qualified name as AndroidJUnitRunner's
 * `notAnnotation` argument, which excludes it from `:app:connectedDebugAndroidTest`. Running it is
 * therefore an explicit, deliberate act: an `adb shell am instrument` invocation that targets the
 * class by name and does NOT pass `notAnnotation` (see [ImminentReminderSeed]'s KDoc for the exact
 * command).
 *
 * `@Ignore` is deliberately NOT used for this purpose: AndroidJUnitRunner honours `@Ignore` even
 * when a single method is targeted with `-e class Pkg.Class#method`, which would make the fixture
 * unrunnable by any means. `notAnnotation` is skipped only when the caller asks for it, so a
 * hand-written `am instrument` command can still run the fixture.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class SeedOnly
