package com.giraffe.mizanapp.domain.prayer

enum class CalculationConvention { MUSLIM_WORLD_LEAGUE, UMM_AL_QURA, EGYPTIAN, ISNA, KARACHI }

enum class AsrMadhab { STANDARD, HANAFI }

data class SelectedConvention(
    val convention: CalculationConvention,
    val asr: AsrMadhab,
)
