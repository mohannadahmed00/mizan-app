package com.giraffe.mizanapp.domain.time

sealed interface BoundaryRegime {
    data object Maghrib : BoundaryRegime
    data class Fallback(val reason: FallbackReason) : BoundaryRegime
}

enum class FallbackReason { NEVER_HAD_LOCATION, ERASED, ZONE_CHANGED_AWAITING_FIX }
