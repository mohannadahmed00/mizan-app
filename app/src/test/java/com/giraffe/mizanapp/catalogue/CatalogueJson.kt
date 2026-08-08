package com.giraffe.mizanapp.catalogue

import com.giraffe.mizanapp.catalogue.model.Catalogue
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
 * Full load path for a catalogue on the test classpath.
 *
 * Keeps three outcomes apart, which FR-011 requires:
 *  - resource absent         -> [CatalogueDefect.NoCatalogue]
 *  - forbidden field present -> [CatalogueDefect.UserAuthoringAffordance]
 *  - unparseable             -> [CatalogueDefect.MalformedCatalogue]
 *
 * Never throws. An empty list is the only success signal.
 */
fun loadAndValidate(
    resourcePath: String,
    validator: CatalogueValidator = CatalogueValidator(),
): List<CatalogueDefect> {
    val raw = Fixtures.readOrNull(resourcePath)
        ?: return listOf(CatalogueDefect.NoCatalogue(resourcePath))

    val authoringDefects = scanForAuthoringAffordances(raw)
    if (authoringDefects.isNotEmpty()) return authoringDefects

    val parsed = parseCatalogue(raw)
    parsed.exceptionOrNull()?.let { failure ->
        return listOf(CatalogueDefect.MalformedCatalogue(failure.message ?: failure.toString()))
    }

    return validator.validate(parsed.getOrThrow())
}
