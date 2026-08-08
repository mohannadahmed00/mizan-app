package com.giraffe.presentation.stats

sealed interface StatsViewEffect {
    data class ShowError(val message: String) : StatsViewEffect
}
