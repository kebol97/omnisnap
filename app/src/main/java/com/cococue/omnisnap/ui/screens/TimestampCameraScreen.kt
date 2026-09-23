package com.cococue.omnisnap.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.FallbackStrategy
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.cococue.omnisnap.util.LocationData
import com.cococue.omnisnap.util.LocationUtils
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CameraMode {
    PHOTO,
    VIDEO
}

@Composable
fun TimestampCameraScreen(
    onCapturedPhoto: (imagePath: String, note: String, locationAddress: String, lat: Double, lon: Double) -> Unit,
    onCapturedVideo: (videoPath: String, note: String, locationAddress: String, lat: Double, lon: Double) -> Unit
) {
    val context = LocalContext.current

    var cameraMode by remember { mutableStateOf(CameraMode.PHOTO) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: false
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: false
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasLocationPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission is required for Timestamp Camera.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }) {
                    Text("Grant Permissions")
                }
            }
        }
        return
    }

    val fixedWatermarkNote = "Scanned with OmniSnap"
    var locationData by remember { mutableStateOf(LocationData("Detecting GPS...", 0.0, 0.0)) }
    var isLocationEnabled by remember { mutableStateOf(true) }
    var isCapturing by remember { mutableStateOf(false) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var recordingDurationSeconds by remember { mutableStateOf(0) }
    var isFlashOn by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    var liveTimestampText by remember { mutableStateOf("") }

    // Live clock ticker
    LaunchedEffect(Unit) {
        while (true) {
            liveTimestampText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            delay(1000)
        }
    }

    // Video recording duration ticker
    LaunchedEffect(isRecordingVideo) {
        if (isRecordingVideo) {
            recordingDurationSeconds = 0
            while (isRecordingVideo) {
                delay(1000)
                recordingDurationSeconds++
            }
        } else {
            recordingDurationSeconds = 0
        }
    }

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var currentVideoFile by remember { mutableStateOf<File?>(null) }

    // CameraX Lifecycle Binding (Runs ONLY when mode, lens, or permissions change)
    LaunchedEffect(cameraMode, lensFacing, hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val requestedSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            val cameraSelector = if (cameraProvider.hasCamera(requestedSelector)) {
                requestedSelector
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            try {
                cameraProvider.unbindAll()
                if (cameraMode == CameraMode.PHOTO) {
                    cameraProvider.bindToLifecycle(
                        context as LifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        context as LifecycleOwner,
                        cameraSelector,
                        preview,
                        videoCapture
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
        }
    }

    LaunchedEffect(isLocationEnabled, hasLocationPermission) {
        if (isLocationEnabled && hasLocationPermission) {
            LocationUtils.observeLiveLocation(context).collect { liveData ->
                locationData = liveData
            }
        } else {
            locationData = LocationData("", 0.0, 0.0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // CameraX Live Preview View
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Real-Time Timestamp Overlay Box (with GPS Coordinates & Address)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 150.dp, start = 16.dp, end = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(12.dp)
        ) {
            Column {
                Text("⏰ $liveTimestampText", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (isLocationEnabled && locationData.fullFormattedLocation.isNotBlank()) {
                    val locationLines = locationData.fullFormattedLocation.split("\n")
                    for (line in locationLines) {
                        Text("📍 $line", color = Color.White, fontSize = 12.sp)
                    }
                }
                Text("📝 $fixedWatermarkNote", color = Color.White, fontSize = 12.sp)
            }
        }

        // Top Control Bar & Recording Status
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { isFlashOn = !isFlashOn }) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = Color.White
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("GPS Stamp", color = Color.White, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = isLocationEnabled,
                            onCheckedChange = { isLocationEnabled = it }
                        )
                    }

                    IconButton(
                        onClick = {
                            if (!isRecordingVideo) {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                            } else {
                                Toast.makeText(context, "Cannot switch camera while recording", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White)
                    }
                }
            }

            // Video Recording Timer Banner
            AnimatedVisibility(visible = isRecordingVideo) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Red.copy(alpha = 0.85f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    val minutes = recordingDurationSeconds / 60
                    val seconds = recordingDurationSeconds % 60
                    val timeFormatted = String.format("%02d:%02d", minutes, seconds)
                    Text("🔴 REC $timeFormatted", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Bottom Controls Container (Capture Button + Mode Switch)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Mode Selector Tabs (PHOTO / VIDEO)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (cameraMode == CameraMode.PHOTO) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            if (!isRecordingVideo) cameraMode = CameraMode.PHOTO
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "Photo Mode",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PHOTO", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (cameraMode == CameraMode.VIDEO) Color.Red else Color.Transparent)
                        .clickable {
                            if (!isRecordingVideo) cameraMode = CameraMode.VIDEO
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = "Video Mode",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("VIDEO", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Capture / Record Button
            if (isCapturing) {
                CircularProgressIndicator(color = Color.White)
            } else {
                if (cameraMode == CameraMode.PHOTO) {
                    // Photo Capture Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable {
                                isCapturing = true
                                val tempFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

                                imageCapture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            isCapturing = false
                                            onCapturedPhoto(
                                                tempFile.absolutePath,
                                                fixedWatermarkNote,
                                                locationData.fullFormattedLocation,
                                                locationData.latitude,
                                                locationData.longitude
                                            )
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            isCapturing = false
                                            Toast.makeText(context, "Capture Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                    )
                } else {
                    // Video Record Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(if (isRecordingVideo) Color.Red else Color.White)
                            .border(4.dp, Color.Red, CircleShape)
                            .clickable {
                                if (isRecordingVideo) {
                                    // Stop Video Recording
                                    activeRecording?.stop()
                                    activeRecording = null
                                    isRecordingVideo = false
                                } else {
                                    // Start Video Recording
                                    val videoFile = File(context.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
                                    currentVideoFile = videoFile

                                    val outputOptions = FileOutputOptions.Builder(videoFile).build()
                                    var pendingRecording = videoCapture.output.prepareRecording(context, outputOptions)

                                    if (hasAudioPermission) {
                                        @Suppress("MissingPermission")
                                        pendingRecording = pendingRecording.withAudioEnabled()
                                    }

                                    isRecordingVideo = true
                                    activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                                        when (event) {
                                            is VideoRecordEvent.Start -> {
                                                isRecordingVideo = true
                                            }
                                            is VideoRecordEvent.Finalize -> {
                                                isRecordingVideo = false
                                                activeRecording = null

                                                if (!event.hasError()) {
                                                    onCapturedVideo(
                                                        videoFile.absolutePath,
                                                        fixedWatermarkNote,
                                                        locationData.fullFormattedLocation,
                                                        locationData.latitude,
                                                        locationData.longitude
                                                    )
                                                } else {
                                                    // Fallback check: If recorded file exists and size is valid (>10KB), accept as success
                                                    if (videoFile.exists() && videoFile.length() > 10000) {
                                                        onCapturedVideo(
                                                            videoFile.absolutePath,
                                                            fixedWatermarkNote,
                                                            locationData.fullFormattedLocation,
                                                            locationData.latitude,
                                                            locationData.longitude
                                                        )
                                                    } else {
                                                        Toast.makeText(context, "Video recording error: ${event.error}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRecordingVideo) {
                            // Stop icon inside recording circle
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                            )
                        } else {
                            // Red inner dot inside record circle
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                            )
                        }
                    }
                }
            }
        }
    }
}
