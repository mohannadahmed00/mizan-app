package com.giraffe.mizanapp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Principle II, guarded at its source.
 *
 * `001` asserted domain purity by scanning source text for `import android.`.
 * That is now unnecessary: `:domain` is a plain Kotlin JVM module, so the
 * Android SDK is not on its classpath and such an import cannot compile.
 *
 * What this test guards instead is the guarantee itself — that nobody quietly
 * turns this module into an Android library and reintroduces the possibility.
 *
 * Gradle runs JVM unit tests with the module directory as the working
 * directory, so the build file is at a bare relative path.
 */
class ModuleBoundaryTest {

    private val buildFile = File("build.gradle.kts")

    @Test
    fun `build file is found at the module root`() {
        assertTrue(
            "expected domain/build.gradle.kts at ${buildFile.absolutePath}",
            buildFile.isFile,
        )
    }

    @Test
    fun `domain is a plain kotlin jvm module`() {
        assertTrue(
            "domain must apply the Kotlin JVM plugin",
            declarations().contains("kotlin.jvm"),
        )
    }

    /** Comment lines are stripped: this file explains what it forbids, and saying so is not doing so. */
    private fun declarations(): String =
        buildFile.readLines()
            .map { it.trim() }
            .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
            .joinToString("\n")

    @Test
    fun `domain applies no android or persistence plugin`() {
        val text = declarations()
        val forbidden = listOf(
            "com.android.library",
            "com.android.application",
            "android.library",
            "android.application",
            "androidx.room",
            "ksp",
        )
        val found = forbidden.filter { text.contains(it) }
        assertTrue(
            "domain/build.gradle.kts must not reference $found — the whole point of " +
                "this module is that Android and persistence cannot reach it",
            found.isEmpty(),
        )
    }
}
