package com.giraffe.mizanapp.data.time

import com.giraffe.mizanapp.data.db.daos.BoundaryStateDao
import com.giraffe.mizanapp.data.db.entities.BoundaryStateEntity
import com.giraffe.mizanapp.domain.prayer.Coordinates
import com.giraffe.mizanapp.domain.prayer.LocationSource
import com.giraffe.mizanapp.domain.prayer.PrayerTimesOutcome
import com.giraffe.mizanapp.domain.prayer.PrayerTimesProvider
import com.giraffe.mizanapp.domain.time.BoundaryRegime
import com.giraffe.mizanapp.domain.time.BoundaryState
import com.giraffe.mizanapp.domain.time.BoundaryStatus
import com.giraffe.mizanapp.domain.time.DayBoundary
import com.giraffe.mizanapp.domain.time.FallbackReason
import com.giraffe.mizanapp.domain.time.LocationRequestOutcome
import com.giraffe.mizanapp.domain.time.resolveBoundaryDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The app-facing authority on which boundary rule is in force (contracts/boundary-provider.md).
 *
 * [current] is a plain in-memory field read — never a disk read, never a clock read — so
 * `SystemTimeProvider.today()` can stay synchronous (research R3). [refresh] is where the real
 * work happens: it takes `now` and `zone` as parameters rather than reading them, because
 * `SystemTimeProvider` derives its own state from this store, and a clock read here would close
 * that cycle (Principle VII).
 *
 * A held row's `zoneIdWhenObtained` of `null` is a real state, not an oversight: it means
 * coordinates exist but have never been stamped with a device zone — the moment right after
 * [requestLocation] stores a fresh fix, before the next [refresh] call (which alone has a `zone`
 * to stamp with) resolves it. Until then the fix is trusted unconditionally.
 */
class BoundaryStateStore(
    private val dao: BoundaryStateDao,
    private val locationSource: LocationSource,
    private val prayerTimesProvider: PrayerTimesProvider,
) : BoundaryStatus {

    private val mutex = Mutex()
    private val stateFlow = MutableStateFlow(TRANSIENT_STARTUP_STATE)
    private val promptShownFlow = MutableStateFlow(false)

    override fun current(): BoundaryState = stateFlow.value

    override fun observe(): Flow<BoundaryState> = stateFlow.asStateFlow()

    override fun promptShown(): Boolean = promptShownFlow.value

    override suspend fun markPromptShown(): Unit = mutex.withLock {
        val entity = dao.get()
        dao.upsert(
            (entity ?: emptyEntity()).copy(promptShown = true),
        )
        promptShownFlow.value = true
    }

    override suspend fun refresh(now: Instant, zone: ZoneId): Unit = mutex.withLock {
        val entity = dao.get()
        promptShownFlow.value = entity?.promptShown ?: false
        val lastResolvedDate = entity?.lastResolvedDate?.let(LocalDate::parse)
        val lastResolvedRegime = entity?.lastResolvedRegime?.let(::regimeFromLabel)

        var coordinates = entity?.let(::toCoordinates)
        var zoneIdWhenObtained = entity?.zoneIdWhenObtained
        var obtainedAt = entity?.obtainedAt

        val regime: BoundaryRegime
        val maghribToday: Instant?

        when {
            coordinates == null -> {
                regime = BoundaryRegime.Fallback(erasedOrNeverHad(lastResolvedRegime))
                maghribToday = null
            }
            zoneIdWhenObtained != null && zoneIdWhenObtained != zone.id -> {
                // FR-012b: the device moved. Attempt one opportunistic fresh fix.
                val fix = if (locationSource.hasPermission()) locationSource.current() else null
                if (fix != null) {
                    coordinates = fix
                    zoneIdWhenObtained = zone.id
                    obtainedAt = now.toEpochMilli()
                    val outcome = prayerTimesProvider.timesFor(now.atZone(zone).toLocalDate(), fix, zone)
                    if (outcome is PrayerTimesOutcome.Calculated) {
                        regime = BoundaryRegime.Maghrib
                        maghribToday = outcome.times.maghrib
                    } else {
                        regime = BoundaryRegime.Fallback(FallbackReason.NEVER_HAD_LOCATION)
                        maghribToday = null
                    }
                } else {
                    regime = BoundaryRegime.Fallback(FallbackReason.ZONE_CHANGED_AWAITING_FIX)
                    maghribToday = null
                }
            }
            else -> {
                // Trusted: zone matches, or the fix was never yet stamped (fresh from requestLocation).
                zoneIdWhenObtained = zone.id
                val outcome = prayerTimesProvider.timesFor(now.atZone(zone).toLocalDate(), coordinates, zone)
                if (outcome is PrayerTimesOutcome.Calculated) {
                    regime = BoundaryRegime.Maghrib
                    maghribToday = outcome.times.maghrib
                } else {
                    regime = BoundaryRegime.Fallback(FallbackReason.NEVER_HAD_LOCATION)
                    maghribToday = null
                }
            }
        }

        val computedDate = DayBoundary.dateAt(now, zone, maghribToday)
        val regimeChanged = regime != lastResolvedRegime
        val resolvedDate = resolveBoundaryDate(computedDate, lastResolvedDate, regimeChanged)

        val heldCoordinates = if (regime is BoundaryRegime.Maghrib) coordinates else null
        val expiresAt = computeExpiresAt(regime, resolvedDate, now, zone, heldCoordinates)

        stateFlow.value = BoundaryState(
            regime = regime,
            coordinates = heldCoordinates,
            zoneIdWhenObtained = if (heldCoordinates != null) zoneIdWhenObtained else entity?.zoneIdWhenObtained,
            resolvedDate = resolvedDate,
            expiresAt = expiresAt,
            lastResolvedDate = resolvedDate,
            lastResolvedRegime = regime,
        )

        dao.upsert(
            BoundaryStateEntity(
                latitude = coordinates?.latitude,
                longitude = coordinates?.longitude,
                zoneIdWhenObtained = zoneIdWhenObtained,
                obtainedAt = obtainedAt,
                lastResolvedDate = resolvedDate.toString(),
                lastResolvedRegime = regimeLabel(regime),
                promptShown = entity?.promptShown ?: false,
            ),
        )
    }

    override suspend fun requestLocation(): LocationRequestOutcome {
        if (!locationSource.hasPermission()) return LocationRequestOutcome.PermissionDenied
        val fix = locationSource.current() ?: return LocationRequestOutcome.NoFixAvailable
        mutex.withLock {
            val entity = dao.get()
            dao.upsert(
                BoundaryStateEntity(
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    zoneIdWhenObtained = null,
                    obtainedAt = null,
                    lastResolvedDate = entity?.lastResolvedDate,
                    lastResolvedRegime = entity?.lastResolvedRegime,
                    promptShown = entity?.promptShown ?: false,
                ),
            )
        }
        return LocationRequestOutcome.Obtained
    }

    override suspend fun eraseLocation(): Unit = mutex.withLock {
        val entity = dao.get()
        val eraseLabel = regimeLabel(BoundaryRegime.Fallback(FallbackReason.ERASED))
        dao.upsert(
            BoundaryStateEntity(
                latitude = null,
                longitude = null,
                zoneIdWhenObtained = null,
                obtainedAt = null,
                lastResolvedDate = entity?.lastResolvedDate,
                lastResolvedRegime = eraseLabel,
                promptShown = entity?.promptShown ?: false,
            ),
        )
        val current = stateFlow.value
        stateFlow.value = current.copy(
            regime = BoundaryRegime.Fallback(FallbackReason.ERASED),
            coordinates = null,
            zoneIdWhenObtained = null,
            lastResolvedRegime = BoundaryRegime.Fallback(FallbackReason.ERASED),
        )
    }

    private fun erasedOrNeverHad(lastResolvedRegime: BoundaryRegime?): FallbackReason =
        if (lastResolvedRegime is BoundaryRegime.Fallback && lastResolvedRegime.reason == FallbackReason.ERASED) {
            FallbackReason.ERASED
        } else {
            FallbackReason.NEVER_HAD_LOCATION
        }

    private suspend fun computeExpiresAt(
        regime: BoundaryRegime,
        resolvedDate: LocalDate,
        now: Instant,
        zone: ZoneId,
        coordinates: Coordinates?,
    ): Instant {
        if (regime !is BoundaryRegime.Maghrib || coordinates == null) {
            return resolvedDate.plusDays(1).atStartOfDay(zone).toInstant()
        }
        val todayOutcome = prayerTimesProvider.timesFor(resolvedDate, coordinates, zone)
        val todayMaghrib = (todayOutcome as? PrayerTimesOutcome.Calculated)?.times?.maghrib
        if (todayMaghrib != null && todayMaghrib.isAfter(now)) return todayMaghrib

        val tomorrowOutcome = prayerTimesProvider.timesFor(resolvedDate.plusDays(1), coordinates, zone)
        val tomorrowMaghrib = (tomorrowOutcome as? PrayerTimesOutcome.Calculated)?.times?.maghrib
        return tomorrowMaghrib ?: resolvedDate.plusDays(1).atStartOfDay(zone).toInstant()
    }

    private fun toCoordinates(entity: BoundaryStateEntity): Coordinates? {
        val lat = entity.latitude ?: return null
        val lon = entity.longitude ?: return null
        return Coordinates(lat, lon)
    }

    private fun regimeLabel(regime: BoundaryRegime): String = when (regime) {
        is BoundaryRegime.Maghrib -> "MAGHRIB"
        is BoundaryRegime.Fallback -> "FALLBACK:${regime.reason.name}"
    }

    private fun regimeFromLabel(label: String): BoundaryRegime =
        if (label == "MAGHRIB") {
            BoundaryRegime.Maghrib
        } else {
            BoundaryRegime.Fallback(FallbackReason.valueOf(label.removePrefix("FALLBACK:")))
        }

    private fun emptyEntity() = BoundaryStateEntity(
        latitude = null,
        longitude = null,
        zoneIdWhenObtained = null,
        obtainedAt = null,
        lastResolvedDate = null,
        lastResolvedRegime = null,
    )

    private companion object {
        val TRANSIENT_STARTUP_STATE = BoundaryState(
            regime = BoundaryRegime.Fallback(FallbackReason.NEVER_HAD_LOCATION),
            coordinates = null,
            zoneIdWhenObtained = null,
            resolvedDate = LocalDate.MIN,
            expiresAt = Instant.MAX,
            lastResolvedDate = null,
            lastResolvedRegime = null,
        )
    }
}
