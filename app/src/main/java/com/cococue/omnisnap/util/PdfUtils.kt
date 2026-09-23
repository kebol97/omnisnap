package com.cococue.omnisnap.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object PdfUtils {

    /**
     * Create PDF document from a list of bitmaps
     */
    suspend fun createPdfFromBitmaps(
        bitmaps: List<Bitmap>,
        outputFile: File,
        defaultWidth: Int = 595,  // A4 standard width in points
        defaultHeight: Int = 842   // A4 standard height in points
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val document = PdfDocument()

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        try {
            var pageIndex = 1
            for (bitmap in bitmaps) {
                if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) continue

                // CRITICAL: Software rendering on PdfDocument canvas doesn't support HARDWARE bitmaps
                val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    bitmap
                } ?: continue

                // Rendered bitmaps at 2x density, so bitmap.width / 2 gives PDF points width
                val pWidth = maxOf(1, if (safeBitmap.width > 0) safeBitmap.width / 2 else defaultWidth)
                val pHeight = maxOf(1, if (safeBitmap.height > 0) safeBitmap.height / 2 else defaultHeight)

                val pageInfo = PdfDocument.PageInfo.Builder(pWidth, pHeight, pageIndex++).create()
                val page = document.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                // Fill page canvas with solid opaque white to preserve original document colors
                canvas.drawColor(Color.WHITE)

                val srcRect = Rect(0, 0, safeBitmap.width, safeBitmap.height)
                val destRect = Rect(0, 0, pWidth, pHeight)

                canvas.drawBitmap(safeBitmap, srcRect, destRect, bitmapPaint)
                document.finishPage(page)
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
        } finally {
            document.close()
        }
        return@withContext outputFile
    }

    /**
     * Extract PDF pages as bitmaps using native PdfRenderer with 100% true original color preservation
     */
    suspend fun renderPdfToBitmaps(
        pdfFile: File,
        maxPages: Int = 30
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val bitmapList = mutableListOf<Bitmap>()
        if (!pdfFile.exists()) return@withContext bitmapList

        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val count = Math.min(renderer.pageCount, maxPages)
                for (i in 0 until count) {
                    renderer.openPage(i).use { page ->
                        // Render at 2x density for crisp quality
                        val bitmap = Bitmap.createBitmap(
                            page.width * 2,
                            page.height * 2,
                            Bitmap.Config.ARGB_8888
                        )
                        // CRITICAL: Fill with solid white background BEFORE rendering!
                        // Prevents dark/transparent background bleeding and color alteration bug.
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)

                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmapList.add(bitmap)
                    }
                }
            }
        }
        return@withContext bitmapList
    }

    /**
     * Merge multiple PDF files into one output PDF file
     */
    suspend fun mergePdfFiles(
        pdfFiles: List<File>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val document = PdfDocument()
        var totalPageCounter = 1
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        try {
            for (pdfFile in pdfFiles) {
                if (!pdfFile.exists()) continue
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                val bitmap = Bitmap.createBitmap(
                                    page.width * 2,
                                    page.height * 2,
                                    Bitmap.Config.ARGB_8888
                                )
                                val bitmapCanvas = Canvas(bitmap)
                                bitmapCanvas.drawColor(Color.WHITE)

                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val pageInfo = PdfDocument.PageInfo.Builder(
                                    page.width * 2,
                                    page.height * 2,
                                    totalPageCounter++
                                ).create()

                                val pdfPage = document.startPage(pageInfo)
                                pdfPage.canvas.drawColor(Color.WHITE)
                                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)
                                document.finishPage(pdfPage)
                            }
                        }
                    }
                }
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
        } finally {
            document.close()
        }
        return@withContext outputFile
    }

    /**
     * Compress PDF by reducing page scale factor & JPEG quality compression
     */
    suspend fun compressPdfFile(
        inputFile: File,
        outputFile: File,
        scaleFactor: Float = 0.65f,
        quality: Int = 60
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val document = PdfDocument()
        var pageCounter = 1
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        try {
            ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val targetW = (page.width * scaleFactor).toInt().coerceAtLeast(1)
                            val targetH = (page.height * scaleFactor).toInt().coerceAtLeast(1)

                            val rawBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            val bitmapCanvas = Canvas(rawBitmap)
                            bitmapCanvas.drawColor(Color.WHITE)

                            page.render(rawBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            // Compress bitmap to JPEG stream to apply quality compression
                            val bos = ByteArrayOutputStream()
                            rawBitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), bos)
                            rawBitmap.recycle()

                            val compressedBytes = bos.toByteArray()
                            val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                            val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, pageCounter++).create()
                            val pdfPage = document.startPage(pageInfo)
                            pdfPage.canvas.drawColor(Color.WHITE)
                            pdfPage.canvas.drawBitmap(compressedBitmap ?: rawBitmap, 0f, 0f, bitmapPaint)
                            document.finishPage(pdfPage)

                            compressedBitmap?.recycle()
                        }
                    }
                }
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
        } finally {
            document.close()
        }

        // Guarantee compressed result is never larger than original file
        if (outputFile.exists() && inputFile.exists() && outputFile.length() > inputFile.length()) {
            inputFile.copyTo(outputFile, overwrite = true)
        }

        return@withContext outputFile
    }
}
