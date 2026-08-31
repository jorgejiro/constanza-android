plugins {
    // AGP 9 has built-in Kotlin support; org.jetbrains.kotlin.android is no longer applied here.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
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
    }

    buildTypes {
        release {
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

dependencies {
    implementation(project(":domain"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    // AGP does not auto-resolve kotlin("test") to the JUnit4 variant the way kotlin("jvm") does
    // for :domain, so the JUnit-glue artifact is named explicitly here.
    testImplementation(kotlin("test-junit"))

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

// androidx.room:room-testing 2.8.4's own published module metadata declares a strict
// kotlinx-serialization-bom constraint at 1.7.3, but the migration-bundle `$$serializer` classes
// it ships are compiled against kotlinx-serialization-core's 1.8.x `GeneratedSerializer` shape
// (which added `typeParametersSerializers()`). Left unresolved, MigrationTestHelper crashes with
// `AbstractMethodError` at runtime. Forcing the newer, backward-compatible core/json artifacts
// resolves the mismatch; this is a Room 2.8.4 packaging gap, not an app-level version choice.
configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1",
            "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1",
        )
    }
}
