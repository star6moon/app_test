package com.plantdex.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.plantdex.app.data.model.CaptureLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** 촬영 위치를 구합니다. 권한이 없거나 실패하면 null 을 돌려주며, 촬영 자체를 막지는 않습니다. */
class LocationProvider(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // hasPermission() 으로 확인
    suspend fun currentLocation(): CaptureLocation? {
        if (!hasPermission()) return null
        val location = runCatching {
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                val tokenSource = CancellationTokenSource()
                try {
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                        .await()
                } finally {
                    tokenSource.cancel()
                }
            } ?: fusedClient.lastLocation.await()
        }.getOrNull() ?: return null

        return CaptureLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            placeName = placeName(location.latitude, location.longitude),
        )
    }

    private suspend fun placeName(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = runCatching {
            withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine<Address?> { cont ->
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                cont.resume(null)
                            }
                        })
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                    }
                }
            }
        }.getOrNull() ?: return null

        return listOfNotNull(address.adminArea, address.locality ?: address.subAdminArea, address.subLocality)
            .distinct()
            .joinToString(" ")
            .ifBlank { null }
    }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 8_000L
        const val GEOCODE_TIMEOUT_MS = 5_000L
    }
}
