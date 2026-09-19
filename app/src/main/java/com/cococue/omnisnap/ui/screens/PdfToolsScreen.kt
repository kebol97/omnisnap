package com.cococue.omnisnap.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Compress
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cococue.omnisnap.ads.AdManager
import com.cococue.omnisnap.data.repository.DocumentRepository
import com.cococue.omnisnap.ui.components.AdBannerView
import com.cococue.omnisnap.util.PdfUtils
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

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
    val selectedPdfFiles = remember { mutableStateListOf<File>() }

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
                        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        }
                        bitmapList.add(bitmap)
                    }

                    val fileName = if (outputName.isNotBlank()) "${outputName.trim()}.pdf" else "ImageToPdf_${System.currentTimeMillis()}.pdf"
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

                    val destFile = File(repository.appDir, "Compressed_${System.currentTimeMillis()}.pdf")
                    PdfUtils.compressPdfFile(tempInput, destFile, scaleFactor = 0.6f)

                    Toast.makeText(context, "Compressed PDF Saved: ${destFile.name}", Toast.LENGTH_SHORT).show()
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

    // Multi-PDF Merge Launcher
    val mergePdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessing = true
                try {
                    val tempFileList = mutableListOf<File>()
                    for ((index, uri) in uris.withIndex()) {
                        val tempFile = File(context.cacheDir, "temp_merge_${index}_${System.currentTimeMillis()}.pdf")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { out -> input.copyTo(out) }
                        }
                        tempFileList.add(tempFile)
                    }

                    val fileName = if (outputName.isNotBlank()) "${outputName.trim()}.pdf" else "Merged_${System.currentTimeMillis()}.pdf"
                    val destFile = File(repository.appDir, fileName)

                    PdfUtils.mergePdfFiles(tempFileList, destFile)
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

            PdfToolActionCard(
                title = "Merge PDF Files",
                description = "Select & combine multiple PDF documents into one file",
                icon = Icons.Default.MergeType,
                buttonText = "Select PDFs to Merge",
                color = Color(0xFF0D9488),
                onClick = { mergePdfPickerLauncher.launch("application/pdf") }
            )

            PdfToolActionCard(
                title = "Compress PDF",
                description = "Reduce document file size for easy emailing & sharing",
                icon = Icons.Default.Compress,
                buttonText = "Select PDF to Compress",
                color = Color(0xFF0284C7),
                onClick = { compressPdfLauncher.launch("application/pdf") }
            )

            AdBannerView()
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
