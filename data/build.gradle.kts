// BLOCKED: Room cannot be added yet.
//
// Room requires the KSP annotation processor. KSP 2.2.10-2.0.2 registers its
// generated sources through the `kotlin.sourceSets` DSL, which AGP 9's built-in
// Kotlin support rejects outright:
//
//   "Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with
//    built-in Kotlin."
//
// Opting out via `android.builtInKotlin=false` and applying
// org.jetbrains.kotlin.android 2.2.10 instead fails differently — that plugin
// expects com.android.build.gradle.BaseExtension, which AGP 9 removed.
//
// Until that is resolved, this module holds only framework-free plumbing that
// needs no annotation processing. See specs/002-today-task-engine/tasks.md,
// Phase 3e, and the implementation report.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.giraffe.mizanapp.data"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // java.time on minSdk 24 requires desugaring — the domain model uses it.
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    api(project(":domain"))

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
