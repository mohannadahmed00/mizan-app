package com.giraffe.mizanapp.data.prayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.giraffe.mizanapp.domain.prayer.Coordinates
import com.giraffe.mizanapp.domain.prayer.LocationSource
import java.util.concurrent.Executors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The only location reader in the app (Principle VII, SC-013). Uses the platform
 * [LocationManager] directly — not Play Services — and requests coarse accuracy only.
 * Never a continuous track: at most one opportunistic fix per call.
 */
class AndroidLocationSource(private val context: Context) : LocationSource {

    private val locationManager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    override suspend fun current(): Coordinates? {
        if (!hasPermission()) return null
        val manager = locationManager
        if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return awaitSingleFix(manager)

        val lastKnown = try {
            manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
        return lastKnown?.toCoordinates() ?: awaitSingleFix(manager)
    }

    private suspend fun awaitSingleFix(manager: LocationManager): Coordinates? {
        if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                awaitCurrentLocation(manager)
            } else {
                awaitLegacySingleUpdate(manager)
            }
        } catch (_: SecurityException) {
            null
        }
    }

    private suspend fun awaitCurrentLocation(manager: LocationManager): Coordinates? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            manager.getCurrentLocation(
                LocationManager.NETWORK_PROVIDER,
                cancellationSignal,
                Executors.newSingleThreadExecutor(),
            ) { location ->
                if (continuation.isActive) continuation.resume(location?.toCoordinates())
            }
        }

    private suspend fun awaitLegacySingleUpdate(manager: LocationManager): Coordinates? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location.toCoordinates())
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            manager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                0L,
                0f,
                listener,
            )
        }

    private fun Location.toCoordinates(): Coordinates = Coordinates(latitude, longitude)
}
