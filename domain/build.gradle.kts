// kotlin("jvm") only, never com.android.library — android.*/androidx.* cannot resolve here,
// which IS the Android-free enforcement (design.md §4).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    testImplementation(libs.junit)
    // JUnit4 runner + kotlin.test assertions, per design.md D10.
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnit()
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

// `detekt` is PSI-only and never fires ForbiddenMethodCall; `detektMain` has type resolution.
tasks.named("check") {
    dependsOn("detektMain")
}
