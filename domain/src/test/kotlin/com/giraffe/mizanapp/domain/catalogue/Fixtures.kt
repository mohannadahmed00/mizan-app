package com.giraffe.mizanapp.domain.catalogue

/**
 * Loads a catalogue fixture from the test classpath.
 *
 * Returns null when the resource does not exist, so callers can distinguish
 * "no catalogue present" from "catalogue is invalid" (FR-011).
 */
object Fixtures {

    fun readOrNull(path: String): String? =
        Fixtures::class.java.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }

    fun read(path: String): String =
        readOrNull(path) ?: error("fixture not found on classpath: $path")

    fun good(): String = read(GOOD)

    fun bad(name: String): String = read("/catalogue/bad/$name")

    fun badFixtureNames(): List<String> = listOf(
        "duplicate-slug.json",
        "malformed-slug.json",
        "zero-points.json",
        "negative-points.json",
        "zero-occurrences.json",
        "missing-section.json",
        "duplicate-position-in-section.json",
        "duplicate-section-order.json",
        "unreachable-schedule.json",
        "duplicate-version-number.json",
        "version-order-mismatch.json",
        "duplicate-effective-from.json",
        "wrong-weekday-total.json",
        "wrong-week-total.json",
        "wrong-section-composition.json",
        "user-editable-flag.json",
        "malformed.json",
        "two-defects.json",
    )

    const val GOOD = "/catalogue/valid-catalogue.json"
    const val MISSING = "/catalogue/does-not-exist.json"
}

/**
 * Test-side convenience: read a classpath resource, then validate it.
 *
 * The reading half lives here rather than in `:domain`'s main sources because
 * reading is I/O and the domain module does none — production loading is the
 * seeder's job. Tests keep the one-call ergonomics.
 */
fun loadAndValidate(
    resourcePath: String,
    validator: CatalogueValidator = CatalogueValidator(),
): List<CatalogueDefect> = validateCatalogueContent(
    raw = Fixtures.readOrNull(resourcePath),
    source = resourcePath,
    validator = validator,
)
