package com.cococue.omnisnap.data.model

import java.io.File

enum class DocumentType {
    SCAN,
    TIMESTAMP_PHOTO,
    PDF,
    IMAGE,
    OTHER
}

data class DocumentItem(
    val id: String,
    val name: String,
    val file: File,
    val sizeBytes: Long,
    val lastModified: Long,
    val type: DocumentType,
    val pageCount: Int = 1,
    val thumbnailUri: String? = null
) {
    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> String.format("%.2f MB", mb)
                kb >= 1.0 -> String.format("%.1f KB", kb)
                else -> "$sizeBytes Bytes"
            }
        }

    val isPdf: Boolean
        get() = name.endsWith(".pdf", ignoreCase = true) || type == DocumentType.PDF
}
