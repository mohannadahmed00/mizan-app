package com.giraffe.mizanapp.domain.prayer

data class RegionConventionMapping(
    val version: Int,
    val default: SelectedConvention,
    val regions: List<RegionConventionEntry>,
)

data class RegionConventionEntry(
    val zoneIds: List<String>,
    val convention: CalculationConvention,
    val asr: AsrMadhab,
)

fun conventionFor(zoneId: String, mapping: RegionConventionMapping): SelectedConvention =
    mapping.regions.firstOrNull { zoneId in it.zoneIds }
        ?.let { SelectedConvention(it.convention, it.asr) }
        ?: mapping.default
