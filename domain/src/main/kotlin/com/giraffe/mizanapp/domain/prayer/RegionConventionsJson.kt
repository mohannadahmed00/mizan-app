package com.giraffe.mizanapp.domain.prayer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ConventionFile(
    val version: Int,
    val default: ConventionValue,
    val regions: List<ConventionRegion>,
)

@Serializable
private data class ConventionValue(
    val convention: CalculationConvention,
    val asr: AsrMadhab,
)

@Serializable
private data class ConventionRegion(
    val zoneIds: List<String>,
    val convention: CalculationConvention,
    val asr: AsrMadhab,
)

/** Loads the administrator-defined, strict region-convention seed. */
fun loadRegionConventionMapping(): RegionConventionMapping {
    val raw = checkNotNull(
        object {}.javaClass.classLoader.getResourceAsStream("prayer/region-conventions.json"),
    ) { "Missing region convention seed" }
        .bufferedReader()
        .use { it.readText() }
    val file = Json { ignoreUnknownKeys = false }.decodeFromString<ConventionFile>(raw)
    return RegionConventionMapping(
        version = file.version,
        default = SelectedConvention(file.default.convention, file.default.asr),
        regions = file.regions.map { RegionConventionEntry(it.zoneIds, it.convention, it.asr) },
    )
}
