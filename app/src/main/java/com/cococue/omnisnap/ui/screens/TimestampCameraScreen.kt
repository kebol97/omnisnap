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
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.cococue.omnisnap.util.LocationData
import com.cococue.omnisnap.util.LocationUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimestampCameraScreen(
    onCaptured: (imagePath: String, note: String, locationAddress: String, lat: Double, lon: Double) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: false
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
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
    var isFlashOn by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(isLocationEnabled, hasLocationPermission) {
        if (isLocationEnabled && hasLocationPermission) {
            locationData = LocationUtils.getCurrentLocationData(context)
        } else {
            locationData = LocationData("", 0.0, 0.0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // CameraX Live Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            ctx as androidx.lifecycle.LifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            previewView.context as androidx.lifecycle.LifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(previewView.context))
            }
        )

        // Real-Time Overlay Box
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(12.dp)
        ) {
            Column {
                val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                Text("⏰ $timeFormat", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (isLocationEnabled && locationData.address.isNotBlank()) {
                    Text("📍 ${locationData.address}", color = Color.White, fontSize = 12.sp)
                }
                Text("📝 $fixedWatermarkNote", color = Color.White, fontSize = 12.sp)
            }
        }

        // Top Control Bar
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
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

                IconButton(onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                }) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White)
                }
            }
        }

        // Capture Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            if (isCapturing) {
                CircularProgressIndicator(color = Color.White)
            } else {
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
                                        onCaptured(
                                            tempFile.absolutePath,
                                            fixedWatermarkNote,
                                            locationData.address,
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
            }
        }
    }
}
