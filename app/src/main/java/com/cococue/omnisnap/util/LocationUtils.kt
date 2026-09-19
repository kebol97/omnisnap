package com.cococue.omnisnap.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class LocationData(
    val address: String = "GPS Searching...",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

object LocationUtils {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationData(context: Context): LocationData = withContext(Dispatchers.IO) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        return@withContext suspendCancellableCoroutine { continuation ->
            var resumed = false

            fun finish(data: LocationData) {
                if (!resumed) {
                    resumed = true
                    continuation.resume(data)
                }
            }

            // Check last location first for instant coordinates
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null) {
                    val addr = tryGeocode(context, lastLoc.latitude, lastLoc.longitude)
                    finish(LocationData(addr, lastLoc.latitude, lastLoc.longitude))
                } else {
                    // Fetch high accuracy location if lastLocation is null
                    val cancellationTokenSource = CancellationTokenSource()
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                val addr = tryGeocode(context, location.latitude, location.longitude)
                                finish(LocationData(addr, location.latitude, location.longitude))
                            } else {
                                finish(LocationData("GPS Active - Searching Location", 0.0, 0.0))
                            }
                        }
                        .addOnFailureListener {
                            finish(LocationData("Location Unavailable", 0.0, 0.0))
                        }
                }
            }.addOnFailureListener {
                finish(LocationData("Location Unavailable", 0.0, 0.0))
            }
        }
    }

    private fun tryGeocode(context: Context, lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val feature = address.featureName ?: ""
                val thoroughfare = address.thoroughfare ?: ""
                val subLocality = address.subLocality ?: address.locality ?: ""
                val adminArea = address.adminArea ?: ""
                val country = address.countryName ?: ""

                val parts = listOf(feature, thoroughfare, subLocality, adminArea, country)
                    .filter { it.isNotBlank() && !it.matches(Regex("^-?\\d+(\\.\\d+)?$")) }
                    .distinct()
                    .joinToString(", ")

                if (parts.isNotBlank()) parts else String.format(Locale.US, "%.4f, %.4f", lat, lon)
            } else {
                String.format(Locale.US, "Lat: %.4f, Lon: %.4f", lat, lon)
            }
        } catch (e: Exception) {
            String.format(Locale.US, "Lat: %.4f, Lon: %.4f", lat, lon)
        }
    }

    suspend fun getCurrentLocationAddress(context: Context): String {
        return getCurrentLocationData(context).address
    }
}
