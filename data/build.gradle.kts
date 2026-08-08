plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.giraffe.data"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.kotlinx.datetime)
    implementation(libs.retrofit)
    implementation(libs.logging.interceptor)
    implementation(libs.gson)
    implementation(libs.converter.gson)
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
