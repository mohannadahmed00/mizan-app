package com.giraffe.mizanapp.domain.time

import java.time.LocalDate

/**
 * Resolves a boundary date across a regime seam. Clamping is intentionally
 * limited to a rule change: clamping every resolution would make the result
 * depend on launch frequency and could credit a returning person to a closed day.
 */
fun resolveBoundaryDate(
    computed: LocalDate,
    lastResolved: LocalDate?,
    regimeChanged: Boolean,
): LocalDate {
    if (lastResolved == null || !regimeChanged) return computed
    return computed.coerceIn(lastResolved, lastResolved.plusDays(1))
}
