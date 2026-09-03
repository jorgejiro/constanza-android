import java.util.Properties

plugins {
    // AGP 9 has built-in Kotlin support; org.jetbrains.kotlin.android is no longer applied here.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Work unit 7: the actual dependency declaration lives below, distinct from the
    // resolutionStrategy.force block further down, which exists only to patch a room-testing
    // packaging gap and was never itself a real kotlinx-serialization dependency.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

/**
 * The upload signing key, when it is present.
 *
 * `keystore.properties` and the `.jks` it points at are deliberately outside version control
 * (see `.gitignore`), so the `exists()` guard is load-bearing rather than defensive: without it a
 * fresh clone would fail at CONFIGURATION time and could not even build `debug`. With it, a
 * keyless clone builds, tests and lints normally; `assembleRelease` still succeeds there but
 * emits `app-release-unsigned.apk` instead of `app-release.apk`, so the secret is only ever
 * required to produce something installable. Verified by temporarily removing the properties
 * file and building both variants.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.jjrapps.constanza"

    // AGP 9's DSL: compiling against release(37) is a build-time API surface choice, not an
    // opt-in to targetSdk=37 runtime behaviour (design.md §2, §5).
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.jjrapps.constanza"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // `androidTest` holds manual, on-device fixtures for the API 37 delivery matrix that must
        // never run as ordinary verification: ImminentReminderSeed writes to the app's REAL
        // on-device database and arms real alarms, and DatabaseStateReport reads that same real
        // database back out to logcat. A routine that silently touches the app database on every
        // verification run is a trap, and this task also uninstalls both APKs when it finishes,
        // destroying the matrix's state — so AndroidJUnitRunner is told to skip everything
        // annotated @SeedOnly.
        //
        // `@Ignore` cannot serve this purpose: the runner honours it even when a single method is
        // targeted with `-e class Pkg.Class#method`, which would make the fixtures unrunnable.
        // `notAnnotation` is applied only when passed, so a hand-written `adb shell am instrument`
        // command still runs them. See each fixture's KDoc for its exact command.
        testInstrumentationRunnerArguments["notAnnotation"] = "com.jjrapps.constanza.seed.SeedOnly"
    }

    signingConfigs {
        // Registered only when the properties file was actually found, so a keyless clone
        // configures cleanly instead of throwing on a missing `storeFile`.
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // `findByName` rather than `getByName`: on a keyless clone this resolves to null,
            // which leaves the release build unsigned instead of failing configuration.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Work unit 4a: AlarmSchedulerTest constructs a real android.jar Intent/PendingIntent
            // (mocked at the AlarmManager/PendingIntent boundary via MockK, not via Robolectric).
            // Without this, ANY unmocked SDK method call — including the Intent constructor —
            // throws "Method ... not mocked." instead of returning a harmless default.
            isReturnDefaultValues = true
        }

        // The device-free verification matrix. `./gradlew :app:emulatorMatrixGroupDebugAndroidTest`
        // provisions, boots, tests on and tears down both emulators with nothing plugged in — see
        // `openspec/config.yaml`'s "Device-free verification" convention for the standing
        // requirement and for the limits of what a green matrix actually proves.
        //
        // Two devices, because API 31 and API 37 are a REAL behavioural boundary for this app and
        // not merely two versions of the same thing: `POST_NOTIFICATIONS` does not exist below API
        // 33, so `NotificationPermission.decide` returns `NOT_APPLICABLE` there and the Today
        // banner must never render, while on API 37 all of `SHOULD_REQUEST`/`BLOCKED`/`GRANTED`
        // are reachable and the banner drives the real system dialog. One device cannot cover both
        // halves; `CoreFlowE2ETest` asserts each half on the device that can actually reach it.
        managedDevices {
            localDevices {
                create("api31") {
                    device = "Pixel 6"
                    sdkVersion = 31
                    // aosp-atd, the fast path: Automated Test Device images strip the pre-installed
                    // apps and Play services this app never touches. Verified rather than assumed —
                    // the whole 70-test suite passes on `aosp_atd/arm64-v8a`, including
                    // NotificationPosterInstrumentedTest and NotificationActionWiringInstrumentedTest,
                    // so real posted notifications and real PendingIntent dispatch both work on the
                    // stripped image. It needs no permission dialog at all, which is why ATD is safe
                    // on this leg specifically: below API 33 there is no runtime notification
                    // permission to prompt for.
                    systemImageSource = "aosp-atd"
                }
                create("api37") {
                    device = "Pixel 6"
                    sdkVersion = 37
                    // NOT aosp-atd, and the reason is availability rather than preference: Google
                    // publishes no ATD image at API 37 at all. `sdkmanager --list` offers exactly
                    // `google_apis`, `google_apis_ps16k`, `google_apis_playstore`,
                    // `google_apis_playstore_ps16k` and `android-wear-signed` for android-37.0 —
                    // there is no `aosp_atd` or `google_atd` entry to select, and asking for one
                    // fails setup before a single test runs. `google_apis` is the lightest image
                    // that does exist, and it carries the `com.android.permissioncontroller`
                    // package whose dialog this leg has to tap.
                    systemImageSource = "google_apis"
                    // Pinned rather than left to DEFAULT_FOR_SDK_VERSION: above API 35 the default
                    // cannot determine the certified image offline and warns on every run. 4KB is
                    // the alignment of the `google_apis` image selected above.
                    pageAlignment = com.android.build.api.dsl.ManagedVirtualDevice.PageAlignment.FORCE_4KB_PAGES
                }
            }
            groups {
                create("emulatorMatrix") {
                    targetDevices.add(localDevices["api31"])
                    targetDevices.add(localDevices["api37"])
                }
            }
        }
    }

    sourceSets {
        // MigrationTestHelper (task 3.7) reads exported schemas from the androidTest assets
        // folder; this exposes app/schemas/ (design.md §8, §14) there without copying it.
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

ksp {
    // Room's exported schema, checked in from work unit 3 onward (design.md §8, §14).
    arg("room.schemaLocation", "$projectDir/schemas")
}

// ControlStrokeCallSiteTest reads :app's own main sources as text. It has to: the thing it guards
// is a control added with no `border` argument at all, and such a call site names nothing that
// detekt's ForbiddenMethodCall could forbid — the default is evaluated inside material3. See that
// test's KDoc. Handing the path over explicitly beats resolving it from the test's working
// directory: a wrong or missing path then fails loudly rather than scanning nothing and passing.
//
// ViewModelTeardownCallSiteTest reads the androidTest sources the same way, for a related reason:
// `detektMain` below is :app's only type-resolved detekt task and its source is main only, so a
// ForbiddenMethodCall ban on the bare ViewModel constructors would not resolve in androidTest at
// all. Same argument for handing the path over rather than inferring it.
tasks.withType<Test>().configureEach {
    systemProperty("constanza.mainSourceDir", layout.projectDirectory.dir("src/main/kotlin").asFile.absolutePath)
    systemProperty(
        "constanza.androidTestSourceDir",
        layout.projectDirectory.dir("src/androidTest/kotlin").asFile.absolutePath,
    )
    // StringResourceParityTest (app-localization, design.md D9) reads both string tables as plain
    // XML files, same explicit-path convention as the two properties above: a wrong or missing path
    // fails loudly instead of silently resolving from the test's working directory.
    systemProperty(
        "constanza.stringsValuesXml",
        layout.projectDirectory.file("src/main/res/values/strings.xml").asFile.absolutePath,
    )
    systemProperty(
        "constanza.stringsValuesEsXml",
        layout.projectDirectory.file("src/main/res/values-es/strings.xml").asFile.absolutePath,
    )
}

dependencies {
    implementation(project(":domain"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // The FAB's "add" icon on the habit list (task 6a.4) — the small core icon set only, no
    // material-icons-extended dependency.
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Work unit 6a: container/presentational ViewModels (HabitListViewModel, HabitEditorViewModel)
    // and hiltViewModel() in a single-Activity, no-navigation-library setup (design.md §14).
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    // Work unit 4b: @HiltWorker/HiltWorkerFactory (design.md D5) is how ReconcileWorker and
    // MidnightSweepWorker — framework-instantiated like the receivers — get constructor injection.
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Work unit 5-i: task 5.6's snooze-duration setting and task 5.2's "requested notification
    // permission before" flag (design.md §14). Pinned in the version catalog since work unit 1
    // but wired as a dependency only here, on its first actual use.
    implementation(libs.androidx.datastore.preferences)
    // Work unit 7 (task 7.1): the backup file format (design.md §8.4). First real use of
    // kotlinx-serialization in :app — the resolutionStrategy.force block below is a room-testing
    // packaging-gap workaround and was never itself a dependency declaration.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    // AGP does not auto-resolve kotlin("test") to the JUnit4 variant the way kotlin("jvm") does
    // for :domain, so the JUnit-glue artifact is named explicitly here.
    testImplementation(kotlin("test-junit"))
    // Work unit 4a: AlarmScheduler/OccurrencePlanner tests mock Room DAOs (plain interfaces) and
    // android.app.AlarmManager/PendingIntent (final Android framework classes) without Robolectric.
    testImplementation(libs.mockk)
    // Work unit 6a: HabitListViewModelTest is the first Flow-under-test unit; turbine was pinned
    // in the version catalog since work unit 1 for exactly this and wired here on first use.
    testImplementation(libs.turbine)
    // Work unit 6a: the first `viewModelScope.launch` code in this project — Dispatchers.setMain
    // needs a JVM Main dispatcher, which only kotlinx-coroutines-test provides.
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
    // Work unit 4b: TestListenableWorkerBuilder/WorkManagerTestInitHelper for ReconcileWorker and
    // MidnightSweepWorker (task 4b.4); mockk-android mocks AlarmScheduler on-device, mirroring the
    // JVM mockk idiom already used by AlarmSchedulerTest.
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.mockk.android)
    // Work unit 6a: the first Compose UI tests in this project (tasks 6a.6/6a.7's dependencies,
    // added here since this slice needs them for its own create/validate/archive-filter tests).
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // `CoreFlowE2ETest` taps the REAL `POST_NOTIFICATIONS` dialog. That dialog belongs to
    // `com.android.permissioncontroller`, a different process, so neither Compose's test rule nor
    // Espresso can see it — UiAutomator is the only API in this project's test stack that can.
    androidTestImplementation(libs.androidx.test.uiautomator)

    debugImplementation(libs.androidx.compose.ui.tooling)
    // Registers the test-only ComponentActivity ui-test-junit4's createComposeRule launches into.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// androidx.room:room-testing 2.8.4's own published module metadata declares a strict
// kotlinx-serialization-bom constraint at 1.7.3, but the migration-bundle `$$serializer` classes
// it ships are compiled against kotlinx-serialization-core's 1.8.x `GeneratedSerializer` shape
// (which added `typeParametersSerializers()`). Left unresolved, MigrationTestHelper crashes with
// `AbstractMethodError` at runtime. Forcing the newer, backward-compatible core/json artifacts
// resolves the mismatch; this is a Room 2.8.4 packaging gap, not an app-level version choice.
//
// Work unit 6a: ui-test-junit4 1.12.0 only *requires* (a soft minimum) espresso-core 3.5.0, whose
// UiController reflects on `android.hardware.input.InputManager.getInstance()` — removed on the
// connected Pixel 10 (API 37/"Android 17") — throwing `NoSuchMethodException` on every Compose
// `performClick`/`performTextInput`, before it can inject anything. Forcing the latest cached
// espresso-core/espresso-idling-resource/androidx.test:monitor resolves this; an androidx.test/API
// 37 compatibility gap, not an app-level choice, matching the kotlinx-serialization force above.
//
// A note about running Compose UI tests on the physical Pixel 10: they need the **keyguard down**.
// With it showing, the freshly-launched test Activity never resumes and every test fails with
// `IllegalStateException: No compose hierarchies found in the app`, which reads like a Compose or
// dependency problem and is not one. The device is PIN-protected, so `adb shell wm dismiss-keyguard`
// cannot clear it — someone has to unlock it, and `adb shell svc power stayon usb` then keeps it
// from re-locking while plugged in. Check `adb shell dumpsys window | rg isKeyguardShowing` before
// diagnosing anything else. An earlier version of this comment recorded the failure as an
// unexplained device/OS defect that had "ruled out doze/keyguard" and claimed it blocked every
// Compose UI test in this change; that was wrong on both counts, and the tests pass unlocked.
configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1",
            "androidx.test.espresso:espresso-core:3.7.0",
            "androidx.test.espresso:espresso-idling-resource:3.7.0",
            "androidx.test:monitor:1.8.0",
        )
    }
}

// Work unit 4a follow-up: :app now holds time-sensitive code (TimeProvider, InstantResolver,
// OccurrencePlanner), so the clock-access ban that :domain has enforced since work unit 1 applies
// here too. SystemTimeProvider is the single exempted adapter; see config/detekt/detekt.yml.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

/** AGP's artifact-type attribute, used to ask a configuration for jars instead of raw `.aar`s. */
private val ARTIFACT_TYPE_ATTRIBUTE = org.gradle.api.attributes.Attribute.of("artifactType", String::class.java)

// detekt 1.23.8 does not hook AGP 9's variant API, so it registers no `detekt<Variant>` tasks for
// this module — only a plain, PSI-only `detekt`. The task below is registered by hand so :app gets
// detekt coverage at all.
//
// THE CLASSPATH IS THE WHOLE POINT OF THIS TASK, and it took two corrections to make real. Until
// test/guard-control-strokes this block passed `debugCompileClasspath` straight through and left
// the platform out, and the result was a task that ran every PSI rule but silently resolved almost
// nothing — `ForbiddenMethodCall`, the clock ban, did not fire here at all. That was recorded as an
// unfixable AGP 9 limitation. It was not one; it was two defects in these four lines:
//
//  1. `debugCompileClasspath` resolves to what the *dependency graph* holds, which for an Android
//     app is mostly `.aar` files — measured here at 70 of 92 entries. The Kotlin frontend cannot
//     read an `.aar`, so every AndroidX and Compose symbol came back unresolved while `java.time`,
//     which needs no classpath entry at all, resolved fine. That asymmetry is exactly what made the
//     old probe look like a wholesale type-resolution failure. Asking the same configuration for
//     the `android-classes-jar` view runs AGP's own transforms and hands over readable jars.
//  2. `android.jar` was absent entirely. Nothing that touches the framework could resolve without
//     it, which is most of this module.
//
// Both were probed rather than assumed, in both directions: with the view and the platform in
// place, `ForbiddenMethodCall` fires on a planted `java.time.LocalDate.now()` in
// `habit/ScheduleEditors.kt` and on the real `ButtonDefaults.outlinedButtonBorder` call in
// `core/ui/theme/ControlDefaults.kt`; with either one removed, neither fires.
//
// So the clock ban and the control-stroke ban (config/detekt/detekt.yml) are now genuinely enforced
// in :app rather than resting on convention and review, and turning resolution on surfaced exactly
// one finding in existing code, handled at InjectDispatcher in that same config.
val detektMain by tasks.registering(io.gitlab.arturbosch.detekt.Detekt::class) {
    description = "Runs detekt on :app main sources with type resolution."
    setSource(files("src/main/kotlin"))
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    classpath.setFrom(
        // The `android-classes-jar` view, not the raw configuration: see the note above.
        configurations.getByName("debugCompileClasspath").incoming.artifactView {
            attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, "android-classes-jar")
        }.files,
        tasks.named("compileDebugKotlin").map { it.outputs.files },
        // android.jar. Without it nothing that touches the framework resolves, which is most of :app.
        androidComponents.sdkComponents.bootClasspath,
    )
    jvmTarget = "11"
    reports {
        html.required.set(false)
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

tasks.named("check") {
    dependsOn(detektMain)
}
