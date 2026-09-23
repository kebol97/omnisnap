package com.cococue.omnisnap.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class LocationData(
    val address: String = "GPS Searching...",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Float = 0f
) {
    val fullFormattedLocation: String
        get() {
            if (latitude == 0.0 && longitude == 0.0) return address
            val accStr = if (accuracyMeters > 0f) String.format(Locale.US, " (±%.1fm)", accuracyMeters) else ""
            val coords = String.format(Locale.US, "Lat: %.5f, Lon: %.5f%s", latitude, longitude, accStr)
            return if (address.isNotBlank() && !address.startsWith("GPS")) {
                "$coords\n$address"
            } else {
                coords
            }
        }
}

object LocationUtils {

    /**
     * Real-time live GPS location tracking flow (Updates every second with HIGH ACCURACY)
     */
    @SuppressLint("MissingPermission")
    fun observeLiveLocation(context: Context): Flow<LocationData> = callbackFlow {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(800L)
            .setMinUpdateDistanceMeters(0.2f)
            .build()

        var lastGeocodedLat = 0.0
        var lastGeocodedLon = 0.0
        var cachedAddress = "GPS Searching..."

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val lat = location.latitude
                val lon = location.longitude
                val accuracy = location.accuracy

                // Calculate distance moved since last reverse-geocode
                val distanceMoved = FloatArray(1)
                if (lastGeocodedLat != 0.0 && lastGeocodedLon != 0.0) {
                    android.location.Location.distanceBetween(lat, lon, lastGeocodedLat, lastGeocodedLon, distanceMoved)
                } else {
                    distanceMoved[0] = 999f
                }

                if (cachedAddress.startsWith("GPS") || distanceMoved[0] > 15f) {
                    lastGeocodedLat = lat
                    lastGeocodedLon = lon
                    CoroutineScope(Dispatchers.IO).launch {
                        val addr = tryGeocode(context, lat, lon)
                        cachedAddress = addr
                        trySend(LocationData(cachedAddress, lat, lon, accuracy))
                    }
                }

                trySend(LocationData(cachedAddress, lat, lon, accuracy))
            }
        }

        // Try lastLocation immediately for instant initial coordinates while live GPS acquires
        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
            if (lastLoc != null) {
                val addr = tryGeocode(context, lastLoc.latitude, lastLoc.longitude)
                cachedAddress = addr
                lastGeocodedLat = lastLoc.latitude
                lastGeocodedLon = lastLoc.longitude
                trySend(LocationData(addr, lastLoc.latitude, lastLoc.longitude, lastLoc.accuracy))
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

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

            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null) {
                    val addr = tryGeocode(context, lastLoc.latitude, lastLoc.longitude)
                    finish(LocationData(addr, lastLoc.latitude, lastLoc.longitude, lastLoc.accuracy))
                } else {
                    val cancellationTokenSource = CancellationTokenSource()
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                val addr = tryGeocode(context, location.latitude, location.longitude)
                                finish(LocationData(addr, location.latitude, location.longitude, location.accuracy))
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

                if (parts.isNotBlank()) parts else String.format(Locale.US, "%.5f, %.5f", lat, lon)
            } else {
                String.format(Locale.US, "Lat: %.5f, Lon: %.5f", lat, lon)
            }
        } catch (e: Exception) {
            String.format(Locale.US, "Lat: %.5f, Lon: %.5f", lat, lon)
        }
    }

    suspend fun getCurrentLocationAddress(context: Context): String {
        return getCurrentLocationData(context).fullFormattedLocation
    }
}
