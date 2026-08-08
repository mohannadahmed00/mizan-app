package com.giraffe.presentation.dashboard

sealed interface DashboardViewEffect {
    data class ShowError(val message: String) : DashboardViewEffect
}
