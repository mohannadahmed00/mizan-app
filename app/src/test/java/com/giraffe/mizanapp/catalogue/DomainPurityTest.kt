package com.giraffe.mizanapp.catalogue

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Principle II, enforced mechanically.
 *
 * The catalogue model and validator move into `:domain` in Phase 2. That move
 * stays a file move only if no Android dependency creeps in meanwhile, so this
 * test reads the sources as text and refuses any `android.` import.
 */
class DomainPurityTest {

    private val sourceRoot = File("src/test/java/com/giraffe/mizanapp/catalogue")

    private fun kotlinSources(): List<File> =
        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `source root is found`() {
        assertTrue(
            "expected sources at ${sourceRoot.absolutePath}",
            sourceRoot.isDirectory,
        )
        assertTrue("expected at least one Kotlin source", kotlinSources().isNotEmpty())
    }

    @Test
    fun `no android imports anywhere in the catalogue package`() {
        val offenders = kotlinSources().filter { file ->
            file.readLines().any { it.trimStart().startsWith("import android.") }
        }

        assertTrue(
            "these files import Android and would block the move to :domain: " +
                offenders.joinToString { it.name },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `no framework imports that would tie the model to a platform`() {
        val forbidden = listOf(
            "import androidx.",
            "import android.",
            "import org.koin.",
            "import androidx.room.",
            "import retrofit2.",
        )

        val offenders = kotlinSources().flatMap { file ->
            file.readLines()
                .map { it.trimStart() }
                .filter { line -> forbidden.any { line.startsWith(it) } }
                .map { "${file.name}: $it" }
        }

        assertTrue("framework imports found: $offenders", offenders.isEmpty())
    }
}
