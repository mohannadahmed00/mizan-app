import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Supabase configuration, read from `local.properties` (git-ignored) or the
 * environment in CI.
 *
 * **Both default to an empty string.** A build with no configuration must
 * succeed and must run as the offline MVP — `SupabaseClientFactory` returns
 * null, `NoOpRemoteDataSource` is bound, and nothing crashes at start-up
 * (spec 007 FR-003, SC-007). Only the *anon* key belongs here; a service-role
 * key bypasses row-level security and must never ship in a client.
 */
val supabaseProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun supabaseConfig(key: String): String =
    supabaseProperties.getProperty(key) ?: System.getenv(key) ?: ""

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

android {
    namespace = "com.giraffe.mizanapp.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${supabaseConfig("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseConfig("SUPABASE_ANON_KEY")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // java.time on minSdk 24 requires desugaring — the domain model uses it.
        isCoreLibraryDesugaringEnabled = true
    }

    sourceSets {
        // MigrationTestHelper needs the exported schemas bundled into the
        // test APK to read a prior version's shape.
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

ksp {
    // Exported schemas are committed; the constitution forbids destructive migration.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":domain"))

    implementation(libs.adhan)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // spec 007. Supabase is reached only through data/sync/; Ktor is its transport
    // and appears nowhere else. WorkManager is what makes SC-003's one-minute
    // delivery guarantee possible with the app closed.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.work.testing)
    // The instrumentation runner itself. :app gets it transitively via Espresso;
    // :data has no Espresso, so without this the test process cannot start.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
