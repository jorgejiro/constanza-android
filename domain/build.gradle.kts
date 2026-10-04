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

// The plugin's `detekt` and baseline tasks default their --jvm-target to the JDK running Gradle.
// Under Android Studio's JBR (Java 25) that is a value detekt 1.23.8 rejects (it accepts up to 22),
// which crashed `check`. Pin every detekt task to this module's bytecode target instead.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach { jvmTarget = "11" }
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach { jvmTarget = "11" }

// `detekt` is PSI-only and never fires ForbiddenMethodCall; `detektMain` has type resolution.
tasks.named("check") {
    dependsOn("detektMain")
}
