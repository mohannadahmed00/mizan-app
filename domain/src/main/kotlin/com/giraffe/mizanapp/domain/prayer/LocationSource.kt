package com.giraffe.mizanapp.domain.prayer

/** The only location reader in the app; no available fix is returned as null. */
interface LocationSource {
    suspend fun current(): Coordinates?
    fun hasPermission(): Boolean
}
