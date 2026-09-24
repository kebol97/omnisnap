package com.cococue.omnisnap.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cococue.omnisnap.ads.AdManager
import com.cococue.omnisnap.data.repository.DocumentRepository
import com.cococue.omnisnap.ui.components.AdNativeView
import com.cococue.omnisnap.ui.components.DocumentCropView
import com.cococue.omnisnap.ui.components.PerspectiveTransformUtils
import com.cococue.omnisnap.ui.components.QuadCorners
import com.cococue.omnisnap.util.ImageProcessingUtils
import com.cococue.omnisnap.util.ScanFilter
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerScreen(
    onScanSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val repository = remember { DocumentRepository(context) }
    val scope = rememberCoroutineScope()

    var rawOriginalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var quadCornersState by remember { mutableStateOf<QuadCorners?>(null) }
    var currentFilter by remember { mutableStateOf(ScanFilter.MAGIC_ENHANCE) }
    var isCropped by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    // ML Kit Native Document Scanner Launcher
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pdf?.let { pdfResult ->
                val pdfUri = pdfResult.uri
                scope.launch {
                    isProcessing = true
                    try {
                        val inputStream = context.contentResolver.openInputStream(pdfUri)
                        val targetFile = File(repository.appDir, "Scan_${System.currentTimeMillis()}.pdf")
                        FileOutputStream(targetFile).use { out ->
                            inputStream?.copyTo(out)
                        }
                        Toast.makeText(context, "Dokumen PDF Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
                        if (activity != null) {
                            AdManager.showInterstitialAd(activity) {
                                onScanSuccess()
                            }
                        } else {
                            onScanSuccess()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error saving document: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }
    }

    // Gallery Import Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isProcessing = true
                try {
                    val bitmap = ImageProcessingUtils.decodeBitmapFromUri(context, it)
                    if (bitmap != null) {
                        rawOriginalBitmap = bitmap
                        selectedBitmap = bitmap
                        croppedBitmap = null
                        isCropped = false
                        quadCornersState = QuadCorners()
                        currentFilter = ScanFilter.MAGIC_ENHANCE
                    } else {
                        Toast.makeText(context, "Gagal memuat gambar", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Gagal memuat gambar", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    fun startNativeDocScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        val scanner: GmsDocumentScanner = GmsDocumentScanning.getClient(options)
        if (activity != null) {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Auto scanner fallback: ${e.message}", Toast.LENGTH_SHORT).show()
                    galleryLauncher.launch("image/*")
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Document Scanner", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AdNativeView()

            // Document Scanner Mode Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "CamScanner Precision Scan",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Auto edge detection, 4-corner perspective crop & magic color enhancement",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { startNativeDocScanner() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Camera, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Smart Scan", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Gallery Import Option
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Photo for CamScanner Quad Crop")
                }
            }

            // Custom CamScanner Crop Studio Canvas
            rawOriginalBitmap?.let { rawBitmap ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isCropped) "Hasil Scan Pro (CamScanner Quality)" else "CamScanner 4-Point Precision Crop",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isCropped) "Dokumen telah diluruskan & diperjelas seperti CamScanner" else "Geser 4 titik sudut untuk menyesuaikan batas dokumen secara presisi",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Interactive Crop View or Cropped Preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator()
                            } else if (!isCropped) {
                                DocumentCropView(
                                    bitmap = rawBitmap,
                                    modifier = Modifier.fillMaxSize(),
                                    onCornersChanged = { corners ->
                                        quadCornersState = corners
                                    }
                                )
                            } else {
                                selectedBitmap?.let { displayBmp ->
                                    Image(
                                        bitmap = displayBmp.asImageBitmap(),
                                        contentDescription = "Cropped Scan Result",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Filter Selectors (Visible when cropped)
                        if (isCropped) {
                            Text(
                                text = "Filter Efek Dokumen:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(ScanFilter.entries) { filter ->
                                    FilterChip(
                                        selected = currentFilter == filter,
                                        onClick = {
                                            currentFilter = filter
                                            scope.launch {
                                                val baseBmp = croppedBitmap ?: rawBitmap
                                                isProcessing = true
                                                selectedBitmap = ImageProcessingUtils.applyFilter(baseBmp, filter)
                                                isProcessing = false
                                            }
                                        },
                                        label = { Text(filter.name.replace("_", " ")) },
                                        leadingIcon = if (currentFilter == filter) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (!isCropped) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val corners = quadCornersState ?: QuadCorners()
                                            isProcessing = true
                                            val cropped = PerspectiveTransformUtils.cropPerspective(rawBitmap, corners)
                                            croppedBitmap = cropped
                                            selectedBitmap = ImageProcessingUtils.applyFilter(cropped, currentFilter)
                                            isCropped = true
                                            isProcessing = false
                                            Toast.makeText(context, "Dokumen Berhasil Diluruskan & Dipertajam!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.Crop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pangkas & Luruskan Dokumen", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        isCropped = false
                                        selectedBitmap = rawBitmap
                                    }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Atur Ulang")
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            val saveBmp = selectedBitmap ?: rawBitmap
                                            isProcessing = true
                                            try {
                                                val file = repository.saveBitmapToFile(
                                                    bitmap = saveBmp,
                                                    prefix = "Scan"
                                                )
                                                Toast.makeText(context, "Tersimpan: ${file.name}", Toast.LENGTH_SHORT).show()
                                                if (activity != null) {
                                                    AdManager.showInterstitialAd(activity) {
                                                        onScanSuccess()
                                                    }
                                                } else {
                                                    onScanSuccess()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isProcessing = false
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Simpan Hasil Scan")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
