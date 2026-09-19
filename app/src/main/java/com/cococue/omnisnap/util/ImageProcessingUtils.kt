package com.cococue.omnisnap.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.location.Geocoder
import com.cococue.omnisnap.ads.AdManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

enum class ScanFilter {
    ORIGINAL,
    GRAYSCALE,
    BLACK_WHITE,
    MAGIC_ENHANCE,
    HIGH_CONTRAST
}

object ImageProcessingUtils {

    /**
     * Apply image filter to bitmap
     */
    suspend fun applyFilter(src: Bitmap, filter: ScanFilter): Bitmap = withContext(Dispatchers.Default) {
        if (filter == ScanFilter.ORIGINAL) return@withContext src

        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()

        val cm = ColorMatrix()
        when (filter) {
            ScanFilter.GRAYSCALE -> {
                cm.setSaturation(0f)
            }
            ScanFilter.BLACK_WHITE -> {
                cm.set(floatArrayOf(
                    1.5f, 1.5f, 1.5f, 0f, -200f,
                    1.5f, 1.5f, 1.5f, 0f, -200f,
                    1.5f, 1.5f, 1.5f, 0f, -200f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            ScanFilter.MAGIC_ENHANCE -> {
                cm.set(floatArrayOf(
                    1.2f, 0f, 0f, 0f, 10f,
                    0f, 1.25f, 0f, 0f, 10f,
                    0f, 0f, 1.2f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            ScanFilter.HIGH_CONTRAST -> {
                val contrast = 1.4f
                val translate = (-0.5f * contrast + 0.5f) * 255f
                cm.set(floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            ScanFilter.ORIGINAL -> {}
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return@withContext dest
    }

    /**
     * Rotate bitmap
     */
    suspend fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap = withContext(Dispatchers.Default) {
        if (degrees % 360 == 0f) return@withContext src
        val matrix = Matrix().apply { postRotate(degrees) }
        return@withContext Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /**
     * Helper to wrap long text lines to fit inside specified pixel width
     */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = ""
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines.ifEmpty { listOf(text) }
    }

    /**
     * Fetch real map tile matching exact GPS coordinates with localized street names & POIs (3x3 grid stitching)
     */
    private suspend fun fetchRealMapTile(context: Context?, lat: Double, lon: Double, locationText: String): Bitmap? = withContext(Dispatchers.IO) {
        var targetLat = lat
        var targetLon = lon

        // Fallback: Geocode locationText if lat/lon are 0.0
        if (targetLat == 0.0 && targetLon == 0.0 && context != null && locationText.isNotBlank()) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName(locationText.replace("+", " "), 1)
                if (!results.isNullOrEmpty()) {
                    targetLat = results[0].latitude
                    targetLon = results[0].longitude
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (targetLat == 0.0 && targetLon == 0.0) return@withContext null

        val apiKey = AdManager.config.googleMapsApiKey.trim()

        val mapUrls = mutableListOf<String>()

        // 1. Official Google Maps Static API if API key is provided
        if (apiKey.isNotBlank()) {
            mapUrls.add("https://maps.googleapis.com/maps/api/staticmap?center=$targetLat,$targetLon&zoom=16&size=650x420&scale=2&maptype=roadmap&markers=color:red%7C$targetLat,$targetLon&key=$apiKey")
        }

        // 2. High-res tile URLs with full street names & local POI labels
        val zoom = 16
        val n = 1 shl zoom
        val floatTileX = (targetLon + 180.0) / 360.0 * n
        val latRad = Math.toRadians(targetLat)
        val floatTileY = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n

        val centerTileX = Math.floor(floatTileX).toInt()
        val centerTileY = Math.floor(floatTileY).toInt()

        // 3. Try 3x3 Carto Voyager Tile Stitching (Includes full street labels & POIs)
        try {
            val stitched = Bitmap.createBitmap(768, 768, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(stitched)
            var loadedCount = 0

            for (dx in -1..1) {
                for (dy in -1..1) {
                    val tx = centerTileX + dx
                    val ty = centerTileY + dy
                    val tileUrl = "https://cartodb-basemaps-a.global.ssl.fastly.net/rastertiles/voyager/$zoom/$tx/$ty.png"
                    val connection = URL(tileUrl).openConnection() as HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) OmniSnap/1.0")
                    connection.connectTimeout = 2500
                    connection.readTimeout = 2500
                    if (connection.responseCode == 200) {
                        connection.inputStream.use { input ->
                            val tileBmp = BitmapFactory.decodeStream(input)
                            if (tileBmp != null) {
                                canvas.drawBitmap(tileBmp, (dx + 1) * 256f, (dy + 1) * 256f, null)
                                loadedCount++
                            }
                        }
                    }
                }
            }

            if (loadedCount >= 4) {
                val pixelOffsetX = ((floatTileX - centerTileX) * 256.0).toInt()
                val pixelOffsetY = ((floatTileY - centerTileY) * 256.0).toInt()

                val userCenterX = 256 + pixelOffsetX
                val userCenterY = 256 + pixelOffsetY

                val cropW = 600
                val cropH = 380

                val cropLeft = (userCenterX - cropW / 2).coerceIn(0, 768 - cropW)
                val cropTop = (userCenterY - cropH / 2).coerceIn(0, 768 - cropH)

                return@withContext Bitmap.createBitmap(stitched, cropLeft, cropTop, cropW, cropH)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Add fallback static map endpoints
        mapUrls.add("https://static-maps.yandex.ru/1.x/?ll=$targetLon,$targetLat&z=16&l=map&lang=id_ID&size=650,420&pt=$targetLon,$targetLat,pm2rdm")

        for (urlStr in mapUrls) {
            try {
                val connection = URL(urlStr).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) OmniSnap/1.0")
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.connect()
                if (connection.responseCode == 200) {
                    connection.inputStream.use { input ->
                        val bmp = BitmapFactory.decodeStream(input)
                        if (bmp != null) return@withContext bmp
                    }
                }
            } catch (e: Exception) {
                // Try fallback URL
            }
        }

        return@withContext null
    }

    /**
     * Overlay Timestamp, Location text & Real Large Google Maps Tile Graphic onto photo bitmap
     */
    suspend fun drawTimestampOverlay(
        src: Bitmap,
        customNote: String = "OmniSnap Capture",
        locationText: String = "",
        dateTimePattern: String = "yyyy-MM-dd HH:mm:ss",
        includeMapGraphic: Boolean = true,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        context: Context? = null
    ): Bitmap = withContext(Dispatchers.Default) {
        val mutableBitmap = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val width = mutableBitmap.width
        val height = mutableBitmap.height

        val dateStr = SimpleDateFormat(dateTimePattern, Locale.getDefault()).format(Date())
        val cleanLocation = locationText.replace("+", " ").trim()

        val baseFontSize = (height * 0.024f).coerceAtLeast(26f)
        val padding = baseFontSize * 0.8f
        val boxWidth = (width * 0.82f).coerceAtMost(width - padding * 2)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = baseFontSize
            setShadowLayer(4f, 2f, 2f, Color.argb(180, 0, 0, 0))
        }

        // Process wrapped text lines
        val wrappedLines = mutableListOf<String>()
        wrappedLines.addAll(wrapText("⏰ $dateStr", textPaint, boxWidth - padding * 2))
        if (cleanLocation.isNotBlank()) {
            wrappedLines.addAll(wrapText("📍 $cleanLocation", textPaint, boxWidth - padding * 2))
        }
        if (customNote.isNotBlank()) {
            wrappedLines.addAll(wrapText("📝 $customNote", textPaint, boxWidth - padding * 2))
        }

        val lineSpacing = baseFontSize * 0.35f
        val totalTextHeight = wrappedLines.size * baseFontSize + (wrappedLines.size - 1) * lineSpacing + padding * 2

        // Large Map Graphic size spanning card width
        val mapBoxWidth = boxWidth - padding * 2
        val mapBoxHeight = (mapBoxWidth * 0.58f).coerceIn(220f, 500f)

        val totalBoxHeight = if (includeMapGraphic) totalTextHeight + mapBoxHeight + padding else totalTextHeight

        val boxLeft = padding
        val boxBottom = height - padding
        val boxTop = boxBottom - totalBoxHeight
        val boxRight = boxLeft + boxWidth

        // Draw Dark Translucent Card
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(195, 15, 23, 42)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(boxLeft, boxTop, boxRight, boxBottom), 20f, 20f, bgPaint)

        // Draw Text Lines
        var currentY = boxTop + padding + baseFontSize * 0.8f
        for (line in wrappedLines) {
            canvas.drawText(line, boxLeft + padding, currentY, textPaint)
            currentY += baseFontSize + lineSpacing
        }

        // Fetch & Draw Large Real GPS Map Tile Image BELOW Watermark
        if (includeMapGraphic) {
            val mapLeft = boxLeft + padding
            val mapTop = currentY + padding * 0.5f
            val mapRight = mapLeft + mapBoxWidth
            val mapBottom = mapTop + mapBoxHeight

            val realMapBmp = fetchRealMapTile(context, latitude, longitude, cleanLocation)

            if (realMapBmp != null) {
                // Save canvas state for rounded clip
                canvas.save()
                val clipPath = Path().apply {
                    addRoundRect(RectF(mapLeft, mapTop, mapRight, mapBottom), 14f, 14f, Path.Direction.CW)
                }
                canvas.clipPath(clipPath)

                // Draw Real Map Tile Image
                val srcRect = Rect(0, 0, realMapBmp.width, realMapBmp.height)
                val dstRect = RectF(mapLeft, mapTop, mapRight, mapBottom)
                val mapImagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(realMapBmp, srcRect, dstRect, mapImagePaint)

                // Draw Google Maps Blue Location Pulse Pin at Center
                val pinX = mapLeft + mapBoxWidth * 0.5f
                val pinY = mapTop + mapBoxHeight * 0.5f

                val blueDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(66, 133, 244)
                    style = Paint.Style.FILL
                }
                val whiteHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                val pulseRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(90, 66, 133, 244)
                    style = Paint.Style.FILL
                }

                val dotRadius = mapBoxHeight * 0.07f
                canvas.drawCircle(pinX, pinY, dotRadius * 2.5f, pulseRingPaint)
                canvas.drawCircle(pinX, pinY, dotRadius * 1.4f, whiteHaloPaint)
                canvas.drawCircle(pinX, pinY, dotRadius, blueDotPaint)

                // Google Maps Badge
                val googleBadgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(220, 255, 255, 255)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(
                    RectF(
                        mapLeft + mapBoxWidth * 0.03f,
                        mapBottom - mapBoxHeight * 0.22f,
                        mapLeft + mapBoxWidth * 0.38f,
                        mapBottom - mapBoxHeight * 0.04f
                    ),
                    8f, 8f, googleBadgeBg
                )

                val googleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(66, 133, 244)
                    textSize = mapBoxHeight * 0.13f
                    isFakeBoldText = true
                }
                canvas.drawText("Google", mapLeft + mapBoxWidth * 0.06f, mapBottom - mapBoxHeight * 0.08f, googleTextPaint)

                canvas.restore()
            } else {
                // Minimal Clean Vector Map Fallback without fake text/labels when network tile unavailable
                val mapLandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(241, 243, 244)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(RectF(mapLeft, mapTop, mapRight, mapBottom), 14f, 14f, mapLandPaint)

                val parkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(206, 234, 214)
                    style = Paint.Style.FILL
                }
                val parkPath = Path().apply {
                    moveTo(mapLeft + mapBoxWidth * 0.1f, mapTop + mapBoxHeight * 0.1f)
                    lineTo(mapLeft + mapBoxWidth * 0.45f, mapTop + mapBoxHeight * 0.05f)
                    lineTo(mapLeft + mapBoxWidth * 0.35f, mapTop + mapBoxHeight * 0.5f)
                    close()
                }
                canvas.drawPath(parkPath, parkPaint)

                val roadYellow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(254, 217, 155)
                    strokeWidth = mapBoxHeight * 0.12f
                    style = Paint.Style.STROKE
                }
                canvas.drawLine(mapLeft + mapBoxWidth * 0.15f, mapTop, mapLeft + mapBoxWidth * 0.85f, mapBottom, roadYellow)

                val pinX = mapLeft + mapBoxWidth * 0.5f
                val pinY = mapTop + mapBoxHeight * 0.45f
                val pinRadius = mapBoxHeight * 0.18f

                val redPinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(234, 67, 53)
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(pinX, pinY, pinRadius, redPinPaint)

                val innerPin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(pinX, pinY, pinRadius * 0.45f, innerPin)
            }
        }

        return@withContext mutableBitmap
    }

    /**
     * Extract text using ML Kit OCR
     */
    suspend fun extractTextFromBitmap(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        return@withContext suspendCancellableCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener {
                    continuation.resume("Failed to recognize text: ${it.localizedMessage}")
                }
        }
    }
}
