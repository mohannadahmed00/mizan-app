package com.giraffe.mizanapp.domain.prayer

/**
 * Test double for [LocationSource]. Duplicated in every consuming source set (see
 * contracts/prayer-times-provider.md); keep the copies behaviourally identical.
 */
class FakeLocationSource(
    private var coordinates: Coordinates? = null,
    private var permission: Boolean = true,
) : LocationSource {
    override suspend fun current(): Coordinates? = if (permission) coordinates else null
    override fun hasPermission(): Boolean = permission

    fun setCoordinates(value: Coordinates?) {
        coordinates = value
    }

    fun setPermission(value: Boolean) {
        permission = value
    }
}
