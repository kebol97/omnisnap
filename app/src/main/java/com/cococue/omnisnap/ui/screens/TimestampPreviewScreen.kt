package com.cococue.omnisnap.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cococue.omnisnap.ads.AdManager
import com.cococue.omnisnap.data.repository.DocumentRepository
import com.cococue.omnisnap.ui.components.AdNativeView
import com.cococue.omnisnap.util.ImageProcessingUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimestampPreviewScreen(
    imagePath: String,
    initialNote: String,
    locationAddress: String,
    latitude: Double = 0.0,
    longitude: Double = 0.0,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val repository = remember { DocumentRepository(context) }
    val scope = rememberCoroutineScope()

    var isWatermarkEnabled by remember { mutableStateOf(true) }
    val fixedWatermarkText = "Scanned with OmniSnap"
    var includeMapGraphic by remember { mutableStateOf(true) }

    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var stampedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(true) }

    LaunchedEffect(imagePath) {
        val file = File(imagePath)
        if (file.exists()) {
            rawBitmap = BitmapFactory.decodeFile(file.absolutePath)
        }
    }

    fun updateOverlay() {
        val raw = rawBitmap ?: return
        scope.launch {
            isProcessing = true
            val text = if (isWatermarkEnabled) fixedWatermarkText else ""
            stampedBitmap = ImageProcessingUtils.drawTimestampOverlay(
                src = raw,
                customNote = text,
                locationText = locationAddress,
                includeMapGraphic = includeMapGraphic,
                latitude = latitude,
                longitude = longitude,
                context = context
            )
            isProcessing = false
        }
    }

    LaunchedEffect(rawBitmap, isWatermarkEnabled, includeMapGraphic) {
        updateOverlay()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timestamp Photo Preview", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
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

            // Preview Image Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing || stampedBitmap == null) {
                    CircularProgressIndicator()
                } else {
                    Image(
                        bitmap = stampedBitmap!!.asImageBitmap(),
                        contentDescription = "Stamped Photo Preview",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Watermark & Map Overlay Options Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Watermark & Location Options", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isWatermarkEnabled) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Watermark Branding", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("📝 $fixedWatermarkText", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = {
                                        if (activity != null && AdManager.config.watermarkRemovalRewarded) {
                                            Toast.makeText(context, "Watch a short ad to remove watermark", Toast.LENGTH_SHORT).show()
                                            AdManager.showRewardedAd(
                                                activity = activity,
                                                onRewardEarned = {
                                                    isWatermarkEnabled = false
                                                    Toast.makeText(context, "Watermark removed successfully!", Toast.LENGTH_SHORT).show()
                                                },
                                                onFailed = {
                                                    Toast.makeText(context, "Rewarded ad unavailable. Try again.", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        } else {
                                            isWatermarkEnabled = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Remove Watermark (Watch Ad)", fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFF15803D))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Watermark Removed", color = Color(0xFF15803D), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("Unlocked via Rewarded Ad", color = Color(0xFF166534), fontSize = 12.sp)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GPS Map Graphic", fontWeight = FontWeight.SemiBold)
                            Text("Embed location map & coordinates overlay", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = includeMapGraphic,
                            onCheckedChange = { includeMapGraphic = it }
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val finalBmp = stampedBitmap ?: return@launch
                            val savedFile = repository.saveBitmapToFile(
                                bitmap = finalBmp,
                                prefix = "Timestamp"
                            )
                            Toast.makeText(context, "Saved to ${savedFile.name}", Toast.LENGTH_SHORT).show()
                            if (activity != null) {
                                AdManager.showInterstitialAd(activity) {
                                    onDone()
                                }
                            } else {
                                onDone()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Photo")
                }

                Button(
                    onClick = {
                        scope.launch {
                            val finalBmp = stampedBitmap ?: return@launch
                            val savedFile = repository.saveBitmapToFile(
                                bitmap = finalBmp,
                                prefix = "Timestamp"
                            )
                            val item = com.cococue.omnisnap.data.model.DocumentItem(
                                id = savedFile.absolutePath,
                                name = savedFile.name,
                                file = savedFile,
                                sizeBytes = savedFile.length(),
                                lastModified = savedFile.lastModified(),
                                type = com.cococue.omnisnap.data.model.DocumentType.TIMESTAMP_PHOTO
                            )
                            repository.shareDocument(item)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }
            }
        }
    }
}
