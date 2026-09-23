package com.cococue.omnisnap.ui.screens

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cococue.omnisnap.ads.AdManager
import com.cococue.omnisnap.data.repository.DocumentRepository
import com.cococue.omnisnap.ui.components.AdBannerView
import com.cococue.omnisnap.util.ImageProcessingUtils
import com.cococue.omnisnap.util.PdfUtils
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class SelectedPdfItem(
    val uri: Uri,
    val name: String
)

enum class PdfCompressLevel(
    val title: String,
    val description: String,
    val scaleFactor: Float,
    val quality: Int
) {
    MEDIUM(
        title = "Menengah (Rekomendasi)",
        description = "Keseimbangan terbaik antara ukuran hemat & ketajaman teks",
        scaleFactor = 0.65f,
        quality = 60
    ),
    MAXIMUM(
        title = "Paling Kecil (Kompresi Tinggi)",
        description = "Ukuran file paling hemat untuk dikirim via Email & WA",
        scaleFactor = 0.45f,
        quality = 35
    ),
    LOW(
        title = "Kompresi Ringan",
        description = "Kualitas cetak tetap tajam dengan sedikit kompresi",
        scaleFactor = 0.85f,
        quality = 80
    )
}

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = ""
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = cursor.getString(index) ?: ""
                }
            }
        }
    }
    if (name.isBlank()) {
        name = uri.lastPathSegment ?: "Document.pdf"
    }
    return name
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    onNavigateToSignature: () -> Unit,
    onActionComplete: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val repository = remember { DocumentRepository(context) }
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var outputName by remember { mutableStateOf("") }
    val selectedPdfList = remember { mutableStateListOf<SelectedPdfItem>() }
    var selectedCompressLevel by remember { mutableStateOf(PdfCompressLevel.MEDIUM) }

    // Multi-Image to PDF launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessing = true
                try {
                    val bitmapList = mutableListOf<Bitmap>()
                    for (uri in uris) {
                        val bitmap = ImageProcessingUtils.decodeBitmapFromUri(context, uri)
                        if (bitmap != null) {
                            bitmapList.add(bitmap)
                        }
                    }

                    if (bitmapList.isEmpty()) {
                        Toast.makeText(context, "Cannot load selected photo(s)", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val rawName = outputName.trim()
                    val fileName = when {
                        rawName.isBlank() -> "ImageToPdf_${System.currentTimeMillis()}.pdf"
                        rawName.endsWith(".pdf", ignoreCase = true) -> rawName
                        else -> "$rawName.pdf"
                    }
                    val destFile = File(repository.appDir, fileName)

                    PdfUtils.createPdfFromBitmaps(bitmapList, destFile)
                    Toast.makeText(context, "PDF Created Successfully: ${destFile.name}", Toast.LENGTH_SHORT).show()
                    if (activity != null) {
                        AdManager.showInterstitialAd(activity) {
                            onActionComplete()
                        }
                    } else {
                        onActionComplete()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error converting images to PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    // PDF Compress launcher
    val compressPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isProcessing = true
                try {
                    val tempInput = File(context.cacheDir, "temp_compress_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempInput).use { out -> input.copyTo(out) }
                    }

                    val rawName = outputName.trim()
                    val fileName = when {
                        rawName.isBlank() -> "Compressed_${System.currentTimeMillis()}.pdf"
                        rawName.endsWith(".pdf", ignoreCase = true) -> rawName
                        else -> "$rawName.pdf"
                    }
                    val destFile = File(repository.appDir, fileName)

                    PdfUtils.compressPdfFile(
                        inputFile = tempInput,
                        outputFile = destFile,
                        scaleFactor = selectedCompressLevel.scaleFactor,
                        quality = selectedCompressLevel.quality
                    )

                    val origSize = tempInput.length()
                    val compSize = destFile.length()
                    val sizeInfo = if (compSize < origSize) {
                        val savedPercent = ((origSize - compSize).toFloat() / origSize * 100).toInt()
                        "${formatFileSize(origSize)} → ${formatFileSize(compSize)} (Hemat $savedPercent%)"
                    } else {
                        "${formatFileSize(origSize)} (Sudah Optimal)"
                    }

                    Toast.makeText(context, "Kompresi Berhasil! $sizeInfo", Toast.LENGTH_LONG).show()
                    if (activity != null) {
                        AdManager.showInterstitialAd(activity) {
                            onActionComplete()
                        }
                    } else {
                        onActionComplete()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Compression error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    // Multi-PDF Merge Picker Launcher (Appends to selected list without auto-merging)
    val mergePdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            for (uri in uris) {
                val name = getFileNameFromUri(context, uri)
                selectedPdfList.add(SelectedPdfItem(uri, name))
            }
            Toast.makeText(context, "${uris.size} file(s) added to merge list", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Utilities Suite", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AdBannerView()

            OutlinedTextField(
                value = outputName,
                onValueChange = { outputName = it },
                label = { Text("Custom Output File Name (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (isProcessing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Processing document, please wait...", fontSize = 15.sp)
                    }
                }
            }

            PdfToolActionCard(
                title = "E-Signature PDF",
                description = "Sign any PDF document with your custom electronic signature",
                icon = Icons.Default.Draw,
                buttonText = "Open Signature Studio",
                color = Color(0xFF16A34A),
                onClick = onNavigateToSignature
            )

            PdfToolActionCard(
                title = "Image(s) to PDF",
                description = "Convert photos, scans & graphics into single clean PDF",
                icon = Icons.Default.PictureAsPdf,
                buttonText = "Select Photos",
                color = Color(0xFFE11D48),
                onClick = { imagePickerLauncher.launch("image/*") }
            )

            // Merge PDF Files Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D9488).copy(alpha = 0.1f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MergeType,
                            contentDescription = "Merge PDF Files",
                            tint = Color(0xFF0D9488),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Merge PDF Files",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Combine multiple PDF documents in your desired order",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedPdfList.isEmpty()) {
                        Button(
                            onClick = { mergePdfPickerLauncher.launch("application/pdf") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select PDFs to Merge")
                        }
                    } else {
                        Text(
                            text = "Selected Files (${selectedPdfList.size}):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedPdfList.forEachIndexed { index, item ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 2.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF0D9488).copy(alpha = 0.2f),
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = "#${index + 1}",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0D9488)
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(20.dp)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = item.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    val moved = selectedPdfList.removeAt(index)
                                                    selectedPdfList.add(index - 1, moved)
                                                }
                                            },
                                            enabled = index > 0,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = "Move Up",
                                                tint = if (index > 0) MaterialTheme.colorScheme.onSurface else Color.LightGray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                if (index < selectedPdfList.size - 1) {
                                                    val moved = selectedPdfList.removeAt(index)
                                                    selectedPdfList.add(index + 1, moved)
                                                }
                                            },
                                            enabled = index < selectedPdfList.size - 1,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = "Move Down",
                                                tint = if (index < selectedPdfList.size - 1) MaterialTheme.colorScheme.onSurface else Color.LightGray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { selectedPdfList.removeAt(index) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove",
                                                tint = Color.Red,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { mergePdfPickerLauncher.launch("application/pdf") }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add More PDFs")
                            }

                            TextButton(
                                onClick = { selectedPdfList.clear() }
                            ) {
                                Text("Clear List", color = Color.Red)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (selectedPdfList.size < 2) {
                                    Toast.makeText(context, "Please select at least 2 PDF files to merge", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch {
                                    isProcessing = true
                                    try {
                                        val tempFileList = mutableListOf<File>()
                                        for ((index, item) in selectedPdfList.withIndex()) {
                                            val tempFile = File(context.cacheDir, "temp_merge_${index}_${System.currentTimeMillis()}.pdf")
                                            context.contentResolver.openInputStream(item.uri)?.use { input ->
                                                FileOutputStream(tempFile).use { out -> input.copyTo(out) }
                                            }
                                            if (tempFile.exists() && tempFile.length() > 0) {
                                                tempFileList.add(tempFile)
                                            }
                                        }

                                        if (tempFileList.isEmpty()) {
                                            Toast.makeText(context, "Cannot read selected PDF files", Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }

                                        val rawName = outputName.trim()
                                        val fileName = when {
                                            rawName.isBlank() -> "Merged_${System.currentTimeMillis()}.pdf"
                                            rawName.endsWith(".pdf", ignoreCase = true) -> rawName
                                            else -> "$rawName.pdf"
                                        }
                                        val destFile = File(repository.appDir, fileName)

                                        PdfUtils.mergePdfFiles(tempFileList, destFile)
                                        selectedPdfList.clear()
                                        outputName = ""

                                        Toast.makeText(context, "PDF Files Merged Successfully: ${destFile.name}", Toast.LENGTH_SHORT).show()
                                        if (activity != null) {
                                            AdManager.showInterstitialAd(activity) {
                                                onActionComplete()
                                            }
                                        } else {
                                            onActionComplete()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Merge error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                        ) {
                            Icon(Icons.Default.MergeType, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Merge ${selectedPdfList.size} PDF Files Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Compress PDF Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0284C7).copy(alpha = 0.1f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Compress,
                            contentDescription = "Compress PDF",
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Compress PDF",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kecilkan ukuran file PDF untuk kemudahan kirim Email & WA",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Pilih Tingkat Kompresi:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PdfCompressLevel.entries.forEach { level ->
                            val isSelected = selectedCompressLevel == level
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCompressLevel = level },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF0284C7)) else null,
                                tonalElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedCompressLevel = level },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0284C7))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = level.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (isSelected) Color(0xFF0284C7) else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = level.description,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { compressPdfLauncher.launch("application/pdf") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(Icons.Default.Compress, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pilih PDF untuk Dikompresi", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PdfToolActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    buttonText: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = color)
                ) {
                    Text(buttonText)
                }
            }
        }
    }
}
