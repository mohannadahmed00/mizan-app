// Pure Kotlin JVM library.
//
// Deliberately NOT an Android library: with no Android SDK on the classpath,
// `import android.*` is a compile error rather than a review miss. This is how
// constitution Principle II (domain purity) is enforced structurally.
//
// Do not add com.android.library, Room, Compose, or Koin to this module.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
