package com.cococue.omnisnap.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfUtils {

    /**
     * Create PDF document from a list of bitmaps
     */
    suspend fun createPdfFromBitmaps(
        bitmaps: List<Bitmap>,
        outputFile: File,
        pageWidth: Int = 595,  // A4 standard width in points
        pageHeight: Int = 842   // A4 standard height in points
    ): File = withContext(Dispatchers.IO) {
        val document = PdfDocument()

        for ((index, bitmap) in bitmaps.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            // Scale bitmap maintaining aspect ratio to fit inside page
            val srcWidth = bitmap.width
            val srcHeight = bitmap.height
            val scale = Math.min(pageWidth.toFloat() / srcWidth, pageHeight.toFloat() / srcHeight)

            val destWidth = (srcWidth * scale).toInt()
            val destHeight = (srcHeight * scale).toInt()

            val left = (pageWidth - destWidth) / 2
            val top = (pageHeight - destHeight) / 2

            val srcRect = Rect(0, 0, srcWidth, srcHeight)
            val destRect = Rect(left, top, left + destWidth, top + destHeight)

            canvas.drawBitmap(bitmap, srcRect, destRect, null)
            document.finishPage(page)
        }

        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()
        return@withContext outputFile
    }

    /**
     * Extract PDF pages as bitmaps using native PdfRenderer
     */
    suspend fun renderPdfToBitmaps(
        pdfFile: File,
        maxPages: Int = 10
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val bitmapList = mutableListOf<Bitmap>()
        if (!pdfFile.exists()) return@withContext bitmapList

        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val count = Math.min(renderer.pageCount, maxPages)
                for (i in 0 until count) {
                    renderer.openPage(i).use { page ->
                        // Render at 2x density for clear crisp quality
                        val bitmap = Bitmap.createBitmap(
                            page.width * 2,
                            page.height * 2,
                            Bitmap.Config.ARGB_8888
                        )
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
        val document = PdfDocument()
        var totalPageCounter = 1

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
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val pageInfo = PdfDocument.PageInfo.Builder(
                                page.width * 2,
                                page.height * 2,
                                totalPageCounter++
                            ).create()

                            val pdfPage = document.startPage(pageInfo)
                            pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                            document.finishPage(pdfPage)
                        }
                    }
                }
            }
        }

        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()
        return@withContext outputFile
    }

    /**
     * Compress PDF by reducing page DPI / quality
     */
    suspend fun compressPdfFile(
        inputFile: File,
        outputFile: File,
        scaleFactor: Float = 0.7f
    ): File = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        var pageCounter = 1

        ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val targetW = (page.width * scaleFactor).toInt()
                        val targetH = (page.height * scaleFactor).toInt()
                        val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, pageCounter++).create()
                        val pdfPage = document.startPage(pageInfo)
                        pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        document.finishPage(pdfPage)
                    }
                }
            }
        }

        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()
        return@withContext outputFile
    }
}
