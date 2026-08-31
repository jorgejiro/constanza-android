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

    debugImplementation(libs.androidx.compose.ui.tooling)
}
