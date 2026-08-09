package com.giraffe.mizanapp.domain.catalogue

import com.giraffe.mizanapp.domain.catalogue.Catalogue
import kotlinx.serialization.json.Json

/**
 * Field names implying a user may author the catalogue. Forbidden by FR-019 /
 * Principle VI: the catalogue is administrator content.
 */
val FORBIDDEN_AUTHORING_FIELDS: List<String> = listOf(
    "editable",
    "userCreated",
    "custom",
    "deletable",
    "sortable",
    "reorderable",
    "ownerId",
    "userId",
)

private val json = Json {
    ignoreUnknownKeys = false
    classDiscriminator = "type"
}

/**
 * Stage 1: scan the raw text for forbidden authoring fields.
 *
 * This MUST run before [parseCatalogue]. With `ignoreUnknownKeys = false` every
 * unexpected key is a parse failure, so a forbidden field would otherwise
 * surface as a generic [CatalogueDefect.MalformedCatalogue] and FR-019 would
 * lose its name among ordinary typos. "Rejected for some reason" is not the
 * same as "rejected because it lets a user edit tasks".
 */
fun scanForAuthoringAffordances(raw: String): List<CatalogueDefect> =
    FORBIDDEN_AUTHORING_FIELDS
        .filter { field -> Regex("\"${Regex.escape(field)}\"\\s*:").containsMatchIn(raw) }
        .map { CatalogueDefect.UserAuthoringAffordance(it) }

/**
 * Stage 2: strict parse. Unknown keys are rejected, never ignored — a typo'd
 * key in a hand-authored 40-record file would otherwise silently default a task
 * to zero points.
 *
 * Never throws; failures come back as a failed [Result].
 */
fun parseCatalogue(raw: String): Result<Catalogue> = runCatching { json.decodeFromString(raw) }

/**
 * Full validation path for catalogue content that has already been read.
 *
 * Keeps three outcomes apart, which FR-011 requires:
 *  - content absent          -> [CatalogueDefect.NoCatalogue]
 *  - forbidden field present -> [CatalogueDefect.UserAuthoringAffordance]
 *  - unparseable             -> [CatalogueDefect.MalformedCatalogue]
 *
 * Takes the raw text rather than a path: reading is I/O, and this module does
 * none. Callers supply the bytes — the seeder in `:data` from the classpath,
 * tests from a fixture. `source` is carried only so [CatalogueDefect.NoCatalogue]
 * can name what was missing.
 *
 * Never throws. An empty list is the only success signal.
 */
fun validateCatalogueContent(
    raw: String?,
    source: String,
    validator: CatalogueValidator = CatalogueValidator(),
): List<CatalogueDefect> {
    if (raw == null) return listOf(CatalogueDefect.NoCatalogue(source))

    val authoringDefects = scanForAuthoringAffordances(raw)
    if (authoringDefects.isNotEmpty()) return authoringDefects

    val parsed = parseCatalogue(raw)
    parsed.exceptionOrNull()?.let { failure ->
        return listOf(CatalogueDefect.MalformedCatalogue(failure.message ?: failure.toString()))
    }

    return validator.validate(parsed.getOrThrow())
}
