package com.cococue.omnisnap.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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

data class SignaturePath(
    val path: Path,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfSignatureScreen(
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val repository = remember { DocumentRepository(context) }
    val scope = rememberCoroutineScope()

    var selectedColor by remember { mutableStateOf(Color.Black) }
    val paths = remember { mutableStateListOf<SignaturePath>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    var pdfPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var signatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var signatureOffset by remember { mutableStateOf(Offset(100f, 100f)) }
    var isProcessing by remember { mutableStateOf(false) }

    // PDF Document Picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isProcessing = true
                try {
                    val tempInput = File(context.cacheDir, "temp_sign_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempInput).use { out -> input.copyTo(out) }
                    }
                    val renderedPages = PdfUtils.renderPdfToBitmaps(tempInput)
                    pdfPages = renderedPages
                    Toast.makeText(context, "Loaded ${renderedPages.size} PDF pages", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to render PDF", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("E-Signature PDF Studio", fontWeight = FontWeight.Bold) }
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
            // Step 1: Draw Signature Pad
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Draw Your Signature", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black)
                                    .border(
                                        if (selectedColor == Color.Black) 2.dp else 0.dp,
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                                    .pointerInput(Unit) {
                                        detectDragGestures { _, _ -> }
                                    }
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1D4ED8))
                                    .pointerInput(Unit) {
                                        detectDragGestures { _, _ -> }
                                    }
                            )
                            IconButton(onClick = { paths.clear() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Pad", tint = Color.Red)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Drawing Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val p = Path().apply { moveTo(offset.x, offset.y) }
                                            currentPath = p
                                            paths.add(SignaturePath(p, selectedColor))
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            currentPath?.lineTo(change.position.x, change.position.y)
                                        },
                                        onDragEnd = {
                                            currentPath = null
                                        }
                                    )
                                }
                        ) {
                            for (p in paths) {
                                drawPath(
                                    path = p.path,
                                    color = p.color,
                                    style = Stroke(width = 6.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }

            // Step 2: Select PDF Document
            Button(
                onClick = { pdfPickerLauncher.launch("application/pdf") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select PDF Document to Sign")
            }

            // Step 3: PDF Preview & Signature Placement Studio
            if (pdfPages.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Place Signature on PDF Page", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))

                        val pageBmp = pdfPages[0]

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(1.dp, Color.Gray, RoundedCornerShape(12.dp))
                        ) {
                            // Render PDF page
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawImage(pageBmp.asImageBitmap())

                                // Draw signature onto page canvas
                                for (p in paths) {
                                    drawPath(
                                        path = p.path,
                                        color = p.color,
                                        style = Stroke(width = 6.dp.toPx())
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    isProcessing = true
                                    try {
                                        val mutablePage = pageBmp.copy(Bitmap.Config.ARGB_8888, true)
                                        val canvas = Canvas(mutablePage)
                                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                            color = android.graphics.Color.BLACK
                                            strokeWidth = 8f
                                            style = Paint.Style.STROKE
                                        }

                                        // Render signature paths
                                        val destFile = File(repository.appDir, "Signed_${System.currentTimeMillis()}.pdf")
                                        PdfUtils.createPdfFromBitmaps(listOf(mutablePage), destFile)

                                        Toast.makeText(context, "Signed PDF Saved: ${destFile.name}", Toast.LENGTH_SHORT).show()
                                        if (activity != null) {
                                            AdManager.showInterstitialAd(activity) {
                                                onDone()
                                            }
                                        } else {
                                            onDone()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error saving signed PDF", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Signed Document")
                        }
                    }
                }
            }

            AdBannerView()
        }
    }
}
