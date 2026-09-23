package com.cococue.omnisnap.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.cococue.omnisnap.data.model.DocumentItem
import com.cococue.omnisnap.data.model.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentRepository(private val context: Context) {

    val appDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), "OmniSnap")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    suspend fun getAllDocuments(): List<DocumentItem> = withContext(Dispatchers.IO) {
        val files = appDir.listFiles() ?: emptyArray()
        val validExtensions = listOf("pdf", "jpg", "jpeg", "png", "webp", "mp4", "mkv", "3gp")
        return@withContext files
            .filter { it.isFile && (it.extension.lowercase() in validExtensions) }
            .map { file ->
                val ext = file.extension.lowercase()
                val type = when {
                    ext in listOf("mp4", "mkv", "3gp") -> {
                        if (file.name.contains("Timestamp", ignoreCase = true)) DocumentType.TIMESTAMP_VIDEO else DocumentType.VIDEO
                    }
                    file.name.contains("Timestamp", ignoreCase = true) -> DocumentType.TIMESTAMP_PHOTO
                    file.name.contains("Scan", ignoreCase = true) -> DocumentType.SCAN
                    ext == "pdf" -> DocumentType.PDF
                    else -> DocumentType.IMAGE
                }
                DocumentItem(
                    id = file.absolutePath,
                    name = file.name,
                    file = file,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    type = type,
                    thumbnailUri = file.absolutePath
                )
            }
            .sortedByDescending { it.lastModified }
    }

    suspend fun saveBitmapToFile(
        bitmap: Bitmap,
        prefix: String = "Scan",
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 90
    ): File = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val ext = when (format) {
            Bitmap.CompressFormat.PNG -> "png"
            Bitmap.CompressFormat.WEBP -> "webp"
            else -> "jpg"
        }
        val fileName = "${prefix}_${timeStamp}.${ext}"
        val destFile = File(appDir, fileName)

        FileOutputStream(destFile).use { out ->
            bitmap.compress(format, quality, out)
        }
        return@withContext destFile
    }

    suspend fun saveVideoFile(
        sourceFile: File,
        prefix: String = "Timestamp_Video"
    ): File = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${prefix}_${timeStamp}.mp4"
        val destFile = File(appDir, fileName)

        if (sourceFile.exists()) {
            sourceFile.copyTo(destFile, overwrite = true)
        }
        return@withContext destFile
    }

    suspend fun deleteDocument(item: DocumentItem): Boolean = withContext(Dispatchers.IO) {
        if (item.file.exists()) {
            item.file.delete()
        } else {
            false
        }
    }

    suspend fun renameDocument(item: DocumentItem, newName: String): DocumentItem? = withContext(Dispatchers.IO) {
        var formattedName = newName.trim()
        val ext = item.file.extension
        if (!formattedName.lowercase().endsWith(".$ext")) {
            formattedName = "$formattedName.$ext"
        }
        val targetFile = File(item.file.parentFile, formattedName)
        if (item.file.renameTo(targetFile)) {
            DocumentItem(
                id = targetFile.absolutePath,
                name = targetFile.name,
                file = targetFile,
                sizeBytes = targetFile.length(),
                lastModified = targetFile.lastModified(),
                type = item.type,
                thumbnailUri = targetFile.absolutePath
            )
        } else null
    }

    fun shareDocument(item: DocumentItem) {
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, item.file)
        val mimeType = when {
            item.isPdf -> "application/pdf"
            item.isVideo -> "video/*"
            else -> "image/*"
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share ${item.name}")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
