package com.cococue.omnisnap.ui.screens

import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cococue.omnisnap.data.repository.DocumentRepository
import com.cococue.omnisnap.ui.components.AdNativeView
import com.cococue.omnisnap.util.VideoProcessingUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimestampVideoPreviewScreen(
    videoPath: String,
    initialNote: String,
    locationAddress: String,
    latitude: Double = 0.0,
    longitude: Double = 0.0,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(context) }
    val scope = rememberCoroutineScope()

    var isWatermarkEnabled by remember { mutableStateOf(true) }
    val fixedWatermarkText = "Scanned with OmniSnap"
    var includeMapGraphic by remember { mutableStateOf(true) }

    var isProcessing by remember { mutableStateOf(false) }

    val videoFile = remember { File(videoPath) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timestamp Video Preview", fontWeight = FontWeight.Bold) },
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

            // Video Player Container with Live Timestamp Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (videoFile.exists()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(Uri.fromFile(videoFile))
                                val mediaController = MediaController(ctx)
                                mediaController.setAnchorView(this)
                                setMediaController(mediaController)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                            }
                        }
                    )
                } else {
                    Text("Video file not found", color = Color.White)
                }

                // Live Timestamp Card Overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(10.dp)
                ) {
                    Column {
                        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        Text("⏰ $timeFormat", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (locationAddress.isNotBlank()) {
                            Text("📍 $locationAddress", color = Color.White, fontSize = 11.sp)
                        }
                        if (isWatermarkEnabled) {
                            Text("📝 $fixedWatermarkText", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Options Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Stamp Overlay Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("OmniSnap Watermark")
                        Switch(
                            checked = isWatermarkEnabled,
                            onCheckedChange = { isWatermarkEnabled = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("GPS Map Thumbnail Graphic")
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
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing
                ) {
                    Text("Retake")
                }

                Button(
                    onClick = {
                        scope.launch {
                            isProcessing = true
                            val finalNote = if (isWatermarkEnabled) fixedWatermarkText else ""
                            val tempOutput = File(context.cacheDir, "processed_video_${System.currentTimeMillis()}.mp4")

                            val processedVideo = VideoProcessingUtils.applyTimestampToVideo(
                                context = context,
                                inputVideoFile = videoFile,
                                outputVideoFile = tempOutput,
                                customNote = finalNote,
                                locationAddress = locationAddress,
                                includeMapGraphic = includeMapGraphic,
                                latitude = latitude,
                                longitude = longitude
                            )

                            val savedFile = repository.saveVideoFile(processedVideo, prefix = "Timestamp_Video")
                            isProcessing = false

                            Toast.makeText(context, "Saved video to OmniSnap: ${savedFile.name}", Toast.LENGTH_SHORT).show()
                            onDone()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving...", fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Video")
                    }
                }
            }
        }
    }
}
