package com.cococue.omnisnap.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.util.Log
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
     * Apply image filter to bitmap (CamScanner Magic Color, Grayscale, High-Contrast Black&White)
     */
    suspend fun applyFilter(src: Bitmap, filter: ScanFilter): Bitmap = withContext(Dispatchers.Default) {
        when (filter) {
            ScanFilter.ORIGINAL -> src
            ScanFilter.MAGIC_ENHANCE -> applyCamScannerMagicFilter(src)
            ScanFilter.BLACK_WHITE -> applyBlackAndWhiteFilter(src)
            ScanFilter.GRAYSCALE -> applyGrayscaleFilter(src)
            ScanFilter.HIGH_CONTRAST -> applyHighContrastFilter(src)
        }
    }

    /**
     * CamScanner "Magic Color" Filter: Removes paper shadows, flattens background to pure white,
     * sharpens text and table lines while keeping colored stamps/signatures vibrant.
     */
    private fun applyCamScannerMagicFilter(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            // Calculate luminance
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            // CamScanner Whitening & Contrast Stretch
            val factor = when {
                lum > 155f -> {
                    // Paper background -> Whiten to pure white 255
                    1.9f
                }
                lum < 105f -> {
                    // Dark ink & table lines -> Deepen dark lines
                    0.65f
                }
                else -> {
                    // Smooth transition S-Curve
                    val norm = (lum - 105f) / 50f
                    0.65f + norm * 1.25f
                }
            }

            val newR = (r * factor).toInt().coerceIn(0, 255)
            val newG = (g * factor).toInt().coerceIn(0, 255)
            val newB = (b * factor).toInt().coerceIn(0, 255)

            pixels[i] = (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
        }

        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        dest.setPixels(pixels, 0, width, 0, 0, width, height)
        return dest
    }

    private fun applyGrayscaleFilter(src: Bitmap): Bitmap {
        val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return bmp
    }

    private fun applyBlackAndWhiteFilter(src: Bitmap): Bitmap {
        val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix(
            floatArrayOf(
                1.5f, 0f, 0f, 0f, -60f,
                0f, 1.5f, 0f, 0f, -60f,
                0f, 0f, 1.5f, 0f, -60f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return bmp
    }

    private fun applyHighContrastFilter(src: Bitmap): Bitmap {
        val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix(
            floatArrayOf(
                2.0f, 0f, 0f, 0f, -100f,
                0f, 2.0f, 0f, 0f, -100f,
                0f, 0f, 2.0f, 0f, -100f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return bmp
    }

    suspend fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap = withContext(Dispatchers.Default) {
        if (degrees == 0f) return@withContext src
        val matrix = Matrix().apply { postRotate(degrees) }
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
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

        // 1. Tile calculation for zoom 16
        val zoom = 16
        val n = 1 shl zoom
        val floatTileX = (targetLon + 180.0) / 360.0 * n
        val latRad = Math.toRadians(targetLat)
        val floatTileY = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n

        val centerTileX = Math.floor(floatTileX).toInt()
        val centerTileY = Math.floor(floatTileY).toInt()

        // Helper function for 3x3 tile stitching
        fun tryStitchTiles(getTileUrl: (tx: Int, ty: Int) -> String): Bitmap? {
            return try {
                val stitched = Bitmap.createBitmap(768, 768, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(stitched)
                var loadedCount = 0

                for (dx in -1..1) {
                    for (dy in -1..1) {
                        val tx = centerTileX + dx
                        val ty = centerTileY + dy
                        val tileUrl = getTileUrl(tx, ty)
                        val connection = URL(tileUrl).openConnection() as HttpURLConnection
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) OmniSnap/1.0 (com.cococue.omnisnap)")
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

                    Bitmap.createBitmap(stitched, cropLeft, cropTop, cropW, cropH)
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        // 2. OpenStreetMap (OSM) standard tiles (Free, public, no watermark, full POI/street names)
        val osmSubdomains = listOf("a", "b", "c")
        val osmBmp = tryStitchTiles { tx, ty ->
            val sub = osmSubdomains[Math.floorMod(tx + ty, 3)]
            "https://$sub.tile.openstreetmap.org/$zoom/$tx/$ty.png"
        }
        if (osmBmp != null) return@withContext osmBmp

        // 3. Esri World Street Map tiles as secondary tile source
        val esriBmp = tryStitchTiles { tx, ty ->
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/$zoom/$ty/$tx"
        }
        if (esriBmp != null) return@withContext esriBmp

        // 4. Fallback static map endpoints (Yandex Maps)
        val fallbackUrls = listOf(
            "https://static-maps.yandex.ru/1.x/?ll=$targetLon,$targetLat&z=16&l=map&lang=id_ID&size=650,420&pt=$targetLon,$targetLat,pm2rdm"
        )

        for (urlStr in fallbackUrls) {
            try {
                val connection = URL(urlStr).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) OmniSnap/1.0")
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
            isFakeBoldText = true
        }

        val lines = mutableListOf<String>()
        lines.add("⏰ $dateStr")

        val coordsText = if (latitude != 0.0 || longitude != 0.0) {
            String.format(Locale.US, "Lat: %.5f, Lon: %.5f", latitude, longitude)
        } else ""

        val fullLocStr = when {
            coordsText.isNotBlank() && cleanLocation.isNotBlank() && !cleanLocation.contains("Lat:") -> {
                "📍 $coordsText\n$cleanLocation"
            }
            coordsText.isNotBlank() && cleanLocation.isBlank() -> {
                "📍 $coordsText"
            }
            cleanLocation.isNotBlank() -> {
                if (cleanLocation.startsWith("📍")) cleanLocation else "📍 $cleanLocation"
            }
            else -> ""
        }

        if (fullLocStr.isNotBlank()) {
            for (subLine in fullLocStr.split("\n")) {
                val locationLines = wrapText(subLine, textPaint, boxWidth - padding * 2)
                lines.addAll(locationLines)
            }
        }

        if (customNote.isNotBlank()) {
            lines.add("📝 $customNote")
        }

        val lineSpacing = baseFontSize * 0.35f
        val totalTextHeight = lines.size * baseFontSize + (lines.size - 1) * lineSpacing

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
        for (line in lines) {
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

                // Draw Location Pulse Pin at Center
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

                // Maps Badge
                val googleBadgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(220, 255, 255, 255)
                    style = Paint.Style.FILL
                }
                val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(66, 133, 244)
                    textSize = (mapBoxHeight * 0.085f).coerceAtLeast(18f)
                    isFakeBoldText = true
                }

                val badgeText = "OpenStreetMap"
                val badgeTextWidth = badgeTextPaint.measureText(badgeText)
                val badgePaddingH = 14f
                val badgePaddingV = 8f
                val badgeWidth = badgeTextWidth + badgePaddingH * 2
                val badgeHeight = badgeTextPaint.textSize + badgePaddingV * 2

                val badgeLeft = mapLeft + 12f
                val badgeBottom = mapBottom - 12f
                val badgeTop = badgeBottom - badgeHeight
                val badgeRight = badgeLeft + badgeWidth

                canvas.drawRoundRect(RectF(badgeLeft, badgeTop, badgeRight, badgeBottom), 8f, 8f, googleBadgeBg)
                canvas.drawText(badgeText, badgeLeft + badgePaddingH, badgeBottom - badgePaddingV - 2f, badgeTextPaint)

                canvas.restore()
            }
        }

        mutableBitmap
    }

    /**
     * Recognized text extraction from Bitmap using ML Kit OCR
     */
    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    continuation.resume("OCR failed: ${e.message}")
                }
        } catch (e: Exception) {
            continuation.resume("Error processing image for OCR")
        }
    }

    suspend fun extractTextFromBitmap(bitmap: Bitmap): String = recognizeText(bitmap)

    /**
     * Decode Bitmap from Uri
     */
    fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? = getBitmapFromUri(context, uri)
}
