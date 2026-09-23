package com.cococue.omnisnap.ui.screens

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cococue.omnisnap.ads.AdManager
import com.cococue.omnisnap.data.repository.DocumentRepository
import com.cococue.omnisnap.util.PdfUtils
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Normalized point on a PDF page canvas (0.0f .. 1.0f relative to page dimensions)
 */
data class SignaturePoint(
    val normX: Float,
    val normY: Float
)

/**
 * A drawn signature stroke with normalized coordinates and proportional width
 */
data class SignatureStroke(
    val points: List<SignaturePoint>,
    val color: Color,
    val normStrokeWidth: Float, // strokeWidth relative to page width
    val pageIndex: Int
)

/**
 * Search result item matching a keyword in the PDF document
 */
data class PdfSearchResult(
    val pageIndex: Int,
    val lineText: String,
    val normBoundingBox: RectF?
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
    val keyboardController = LocalSoftwareKeyboardController.current

    var pdfPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var pdfFileName by remember { mutableStateOf("PDF Document") }
    var isProcessing by remember { mutableStateOf(false) }

    // Drawing & Signature State
    var selectedColor by remember { mutableStateOf(Color.Black) }
    var selectedStrokeWidthPx by remember { mutableFloatStateOf(8f) }
    var isDrawMode by remember { mutableStateOf(false) } // Default VIEW mode

    val strokes = remember { mutableStateListOf<SignatureStroke>() }
    val undoStack = remember { mutableStateListOf<SignatureStroke>() }

    // Search State
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<PdfSearchResult>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    // Zoom & Pan state for viewport
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offset += offsetChange
    }

    val lazyListState = rememberLazyListState()

    // Function to perform OCR search
    fun performTextSearch() {
        if (searchQuery.isBlank() || pdfPages.isEmpty()) {
            searchResults = emptyList()
            currentMatchIndex = 0
            return
        }
        scope.launch {
            isSearching = true
            keyboardController?.hide()
            try {
                val matches = searchPdfText(pdfPages, searchQuery)
                searchResults = matches
                currentMatchIndex = 0
                if (matches.isNotEmpty()) {
                    lazyListState.animateScrollToItem(matches[0].pageIndex)
                    Toast.makeText(context, "Found ${matches.size} match(es)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No text found matching \"$searchQuery\"", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Search error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isSearching = false
            }
        }
    }

    // PDF Picker Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isProcessing = true
                try {
                    val name = getFileNameFromUri(context, uri)
                    pdfFileName = name

                    val tempInput = File(context.cacheDir, "temp_sign_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempInput).use { out -> input.copyTo(out) }
                    }
                    val renderedPages = PdfUtils.renderPdfToBitmaps(tempInput, maxPages = 30)
                    pdfPages = renderedPages
                    strokes.clear()
                    undoStack.clear()
                    searchResults = emptyList()
                    searchQuery = ""
                    isSearchActive = false
                    scale = 1f
                    offset = Offset.Zero
                    isDrawMode = false
                    Toast.makeText(context, "Loaded $name (${renderedPages.size} pages)", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load PDF file", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search text (e.g. Signature)...", fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { performTextSearch() }),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        )
                    } else {
                        Column {
                            Text(
                                text = if (pdfPages.isNotEmpty()) pdfFileName else "E-Signature PDF Studio",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (pdfPages.isNotEmpty()) {
                                Text(
                                    text = if (isDrawMode) "Sign Mode Active" else "Preview Mode (Tap Sign PDF to draw)",
                                    fontSize = 11.sp,
                                    color = if (isDrawMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            searchQuery = ""
                            searchResults = emptyList()
                        } else {
                            onDone()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (pdfPages.isNotEmpty()) {
                        if (!isSearchActive) {
                            // Search Icon
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search text in PDF")
                            }

                            // Change File Icon
                            IconButton(onClick = { pdfPickerLauncher.launch("application/pdf") }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Open Another File")
                            }

                            // Save PDF Button
                            Button(
                                onClick = {
                                    scope.launch {
                                        isProcessing = true
                                        try {
                                            val signedPages = pdfPages.mapIndexed { pageIdx, origBmp ->
                                                val pageStrokes = strokes.filter { it.pageIndex == pageIdx }
                                                if (pageStrokes.isEmpty()) {
                                                    origBmp
                                                } else {
                                                    val mutableBmp = Bitmap.createBitmap(origBmp.width, origBmp.height, Bitmap.Config.ARGB_8888)
                                                    val bitmapCanvas = Canvas(mutableBmp)
                                                    bitmapCanvas.drawColor(android.graphics.Color.WHITE)
                                                    bitmapCanvas.drawBitmap(origBmp, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

                                                    for (stroke in pageStrokes) {
                                                        drawSmoothStrokeOnBitmap(
                                                            canvas = bitmapCanvas,
                                                            stroke = stroke,
                                                            bmpWidth = mutableBmp.width.toFloat(),
                                                            bmpHeight = mutableBmp.height.toFloat()
                                                        )
                                                    }
                                                    mutableBmp
                                                }
                                            }

                                            val safeName = pdfFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").removeSuffix(".pdf")
                                            val destFile = File(repository.appDir, "Signed_${safeName}_${System.currentTimeMillis()}.pdf")
                                            PdfUtils.createPdfFromBitmaps(signedPages, destFile)

                                            Toast.makeText(context, "Saved Signed PDF: ${destFile.name}", Toast.LENGTH_LONG).show()
                                            if (activity != null) {
                                                AdManager.showInterstitialAd(activity) {
                                                    onDone()
                                                }
                                            } else {
                                                onDone()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error saving PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.padding(end = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        } else {
                            // Execute Search Button
                            IconButton(onClick = { performTextSearch() }) {
                                Icon(Icons.Default.Search, contentDescription = "Run search", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF161618)) // Dark neutral canvas backdrop
        ) {
            if (pdfPages.isEmpty()) {
                // Empty State: Select PDF File Button
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Select PDF Document",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Open any PDF file, search keywords to find signature blocks fast, and draw precise signatures directly on the page.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { pdfPickerLauncher.launch("application/pdf") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Choose PDF File", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                if (isProcessing || isSearching) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isSearching) "Searching keywords in PDF..." else "Processing document...",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    // MAIN PDF VIEWPORT (FULL SCREEN)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                    ) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                                .transformable(state = transformState, enabled = !isDrawMode),
                            contentPadding = PaddingValues(
                                top = 16.dp,
                                bottom = 120.dp, // Generous bottom padding so signature area at page bottom can scroll completely up!
                                start = 12.dp,
                                end = 12.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(pdfPages) { pageIndex, bmp ->
                                val pageStrokes = strokes.filter { it.pageIndex == pageIndex }
                                val pageSearchResults = searchResults.filter { it.pageIndex == pageIndex }
                                var viewSize by remember { mutableStateOf(IntSize.Zero) }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Page Number Badge
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    ) {
                                        Text(
                                            text = "Page ${pageIndex + 1} of ${pdfPages.size}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    // Crisp PDF Page Card Container
                                    Card(
                                        shape = RoundedCornerShape(4.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.White)
                                                .onGloballyPositioned { layoutCoordinates ->
                                                    viewSize = layoutCoordinates.size
                                                }
                                        ) {
                                            // Base PDF Page Image
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "PDF Page ${pageIndex + 1}",
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            // Live Signature & Search Highlight Canvas
                                            var currentStrokePoints by remember { mutableStateOf<List<SignaturePoint>>(emptyList()) }

                                            Canvas(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .pointerInput(pageIndex, isDrawMode, selectedColor, selectedStrokeWidthPx) {
                                                        if (isDrawMode && viewSize.width > 0 && viewSize.height > 0) {
                                                            detectDragGestures(
                                                                onDragStart = { startPos ->
                                                                    val normX = (startPos.x / viewSize.width.toFloat()).coerceIn(0f, 1f)
                                                                    val normY = (startPos.y / viewSize.height.toFloat()).coerceIn(0f, 1f)
                                                                    val normWidth = selectedStrokeWidthPx / viewSize.width.toFloat()

                                                                    currentStrokePoints = listOf(SignaturePoint(normX, normY))
                                                                    val tempStroke = SignatureStroke(
                                                                        points = currentStrokePoints,
                                                                        color = selectedColor,
                                                                        normStrokeWidth = normWidth,
                                                                        pageIndex = pageIndex
                                                                    )
                                                                    strokes.add(tempStroke)
                                                                },
                                                                onDrag = { change, _ ->
                                                                    change.consume()
                                                                    val pos = change.position
                                                                    val normX = (pos.x / viewSize.width.toFloat()).coerceIn(0f, 1f)
                                                                    val normY = (pos.y / viewSize.height.toFloat()).coerceIn(0f, 1f)

                                                                    currentStrokePoints = currentStrokePoints + SignaturePoint(normX, normY)
                                                                    if (strokes.isNotEmpty()) {
                                                                        val last = strokes.last()
                                                                        if (last.pageIndex == pageIndex) {
                                                                            strokes[strokes.size - 1] = last.copy(points = currentStrokePoints)
                                                                        }
                                                                    }
                                                                },
                                                                onDragEnd = {
                                                                    currentStrokePoints = emptyList()
                                                                    undoStack.clear()
                                                                }
                                                            )
                                                        }
                                                    }
                                            ) {
                                                if (viewSize.width > 0 && viewSize.height > 0) {
                                                    // 1. Draw Search Highlight Rectangles
                                                    for (match in pageSearchResults) {
                                                        match.normBoundingBox?.let { normBox ->
                                                            val left = normBox.left * viewSize.width
                                                            val top = normBox.top * viewSize.height
                                                            val right = normBox.right * viewSize.width
                                                            val bottom = normBox.bottom * viewSize.height

                                                            drawRect(
                                                                color = Color(0xFFFFEB3B).copy(alpha = 0.45f), // Yellow highlight
                                                                topLeft = Offset(left, top),
                                                                size = Size(right - left, bottom - top)
                                                            )
                                                            drawRect(
                                                                color = Color(0xFFF59E0B),
                                                                topLeft = Offset(left, top),
                                                                size = Size(right - left, bottom - top),
                                                                style = Stroke(width = 2.dp.toPx())
                                                            )
                                                        }
                                                    }

                                                    // 2. Draw Smooth Signature Strokes
                                                    for (stroke in pageStrokes) {
                                                        drawSmoothStrokeOnUiCanvas(
                                                            stroke = stroke,
                                                            viewWidth = viewSize.width.toFloat(),
                                                            viewHeight = viewSize.height.toFloat()
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // FLOATING SIGNATURE TOOLBAR OVERLAY (FLOATS AT TOP INSIDE BOX, DOES NOT CHANGE SCAFFOLD TOPBAR HEIGHT OR RESET LIST SCROLL POSITION!)
                        AnimatedVisibility(
                            visible = isDrawMode,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                tonalElevation = 8.dp,
                                shadowElevation = 12.dp,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Row 1: Mode & Undo/Redo/Clear Controls
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilterChip(
                                                selected = isDrawMode,
                                                onClick = { isDrawMode = true },
                                                label = { Text("Pen / Draw", fontSize = 11.sp) },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            )
                                            FilterChip(
                                                selected = !isDrawMode,
                                                onClick = { isDrawMode = false },
                                                label = { Text("Pan & Zoom", fontSize = 11.sp) },
                                                leadingIcon = { Icon(Icons.Default.PanTool, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    if (strokes.isNotEmpty()) {
                                                        val last = strokes.removeAt(strokes.size - 1)
                                                        undoStack.add(last)
                                                    }
                                                },
                                                enabled = strokes.isNotEmpty()
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Undo,
                                                    contentDescription = "Undo",
                                                    tint = if (strokes.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    if (undoStack.isNotEmpty()) {
                                                        val restored = undoStack.removeAt(undoStack.size - 1)
                                                        strokes.add(restored)
                                                    }
                                                },
                                                enabled = undoStack.isNotEmpty()
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Redo,
                                                    contentDescription = "Redo",
                                                    tint = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    strokes.clear()
                                                    undoStack.clear()
                                                },
                                                enabled = strokes.isNotEmpty()
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Clear All Signatures",
                                                    tint = if (strokes.isNotEmpty()) Color(0xFFEF4444) else Color.Gray.copy(alpha = 0.4f)
                                                )
                                            }
                                        }
                                    }

                                    // Row 2: Ink Colors & Stroke Thickness Selection
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Color Dots
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            val colorsList = listOf(
                                                Color.Black,
                                                Color(0xFF1D4ED8), // Deep Blue
                                                Color(0xFFDC2626), // Red
                                                Color(0xFF16A34A)  // Green
                                            )
                                            colorsList.forEach { color ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                        .border(
                                                            width = if (selectedColor == color) 3.dp else 1.dp,
                                                            color = if (selectedColor == color) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                            shape = CircleShape
                                                        )
                                                        .clickable { selectedColor = color }
                                                )
                                            }
                                        }

                                        // Thickness Chips
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf(
                                                Pair("Fine", 5f),
                                                Pair("Medium", 9f),
                                                Pair("Thick", 15f)
                                            ).forEach { (label, widthPx) ->
                                                FilterChip(
                                                    selected = selectedStrokeWidthPx == widthPx,
                                                    onClick = { selectedStrokeWidthPx = widthPx },
                                                    label = { Text(label, fontSize = 10.sp) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // SEARCH MATCH NAVIGATOR BAR FLOATING OVERLAY
                        AnimatedVisibility(
                            visible = isSearchActive && searchResults.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                tonalElevation = 6.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Match ${currentMatchIndex + 1} of ${searchResults.size} (Page ${searchResults[currentMatchIndex].pageIndex + 1})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )

                                    Row {
                                        IconButton(
                                            onClick = {
                                                if (currentMatchIndex > 0) {
                                                    currentMatchIndex--
                                                    val pageIdx = searchResults[currentMatchIndex].pageIndex
                                                    scope.launch { lazyListState.animateScrollToItem(pageIdx) }
                                                }
                                            },
                                            enabled = currentMatchIndex > 0
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
                                        }

                                        IconButton(
                                            onClick = {
                                                if (currentMatchIndex < searchResults.size - 1) {
                                                    currentMatchIndex++
                                                    val pageIdx = searchResults[currentMatchIndex].pageIndex
                                                    scope.launch { lazyListState.animateScrollToItem(pageIdx) }
                                                }
                                            },
                                            enabled = currentMatchIndex < searchResults.size - 1
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
                                        }
                                    }
                                }
                            }
                        }

                        // FLOATING ACTION BUTTON (FAB) FOR EASY SIGN MODE TOGGLE
                        ExtendedFloatingActionButton(
                            onClick = { isDrawMode = !isDrawMode },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 24.dp)
                                .navigationBarsPadding(),
                            containerColor = if (isDrawMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                            contentColor = if (isDrawMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                            icon = {
                                Icon(
                                    imageVector = if (isDrawMode) Icons.Default.PanTool else Icons.Default.Edit,
                                    contentDescription = "Toggle Sign Mode"
                                )
                            },
                            text = {
                                Text(
                                    text = if (isDrawMode) "Done Drawing" else "Sign PDF",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draws smooth quadratic Bezier ink stroke on Compose UI Canvas
 */
private fun DrawScope.drawSmoothStrokeOnUiCanvas(
    stroke: SignatureStroke,
    viewWidth: Float,
    viewHeight: Float
) {
    val pts = stroke.points
    if (pts.isEmpty()) return

    val strokeWidthPx = stroke.normStrokeWidth * viewWidth
    val composePath = Path()

    val firstX = pts[0].normX * viewWidth
    val firstY = pts[0].normY * viewHeight
    composePath.moveTo(firstX, firstY)

    if (pts.size == 1) {
        composePath.lineTo(firstX + 0.5f, firstY + 0.5f)
    } else if (pts.size == 2) {
        composePath.lineTo(pts[1].normX * viewWidth, pts[1].normY * viewHeight)
    } else {
        for (i in 1 until pts.size) {
            val p0 = pts[i - 1]
            val p1 = pts[i]
            val p0X = p0.normX * viewWidth
            val p0Y = p0.normY * viewHeight
            val p1X = p1.normX * viewWidth
            val p1Y = p1.normY * viewHeight

            val midX = (p0X + p1X) / 2f
            val midY = (p0Y + p1Y) / 2f
            composePath.quadraticTo(p0X, p0Y, midX, midY)
        }
        val last = pts.last()
        composePath.lineTo(last.normX * viewWidth, last.normY * viewHeight)
    }

    drawPath(
        path = composePath,
        color = stroke.color,
        style = Stroke(
            width = strokeWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

/**
 * Draws smooth quadratic Bezier ink stroke on Android Bitmap Canvas
 */
private fun drawSmoothStrokeOnBitmap(
    canvas: Canvas,
    stroke: SignatureStroke,
    bmpWidth: Float,
    bmpHeight: Float
) {
    val pts = stroke.points
    if (pts.isEmpty()) return

    val strokeWidthPx = stroke.normStrokeWidth * bmpWidth

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = stroke.color.toArgb()
        this.strokeWidth = strokeWidthPx
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    val androidPath = AndroidPath()
    val firstX = pts[0].normX * bmpWidth
    val firstY = pts[0].normY * bmpHeight
    androidPath.moveTo(firstX, firstY)

    if (pts.size == 1) {
        androidPath.lineTo(firstX + 0.5f, firstY + 0.5f)
    } else if (pts.size == 2) {
        androidPath.lineTo(pts[1].normX * bmpWidth, pts[1].normY * bmpHeight)
    } else {
        for (i in 1 until pts.size) {
            val p0 = pts[i - 1]
            val p1 = pts[i]
            val p0X = p0.normX * bmpWidth
            val p0Y = p0.normY * bmpHeight
            val p1X = p1.normX * bmpWidth
            val p1Y = p1.normY * bmpHeight

            val midX = (p0X + p1X) / 2f
            val midY = (p0Y + p1Y) / 2f
            androidPath.quadTo(p0X, p0Y, midX, midY)
        }
        val last = pts.last()
        androidPath.lineTo(last.normX * bmpWidth, last.normY * bmpHeight)
    }

    canvas.drawPath(androidPath, paint)
}

/**
 * Perform text recognition on PDF page bitmaps to search for keywords
 */
private suspend fun searchPdfText(
    pdfPages: List<Bitmap>,
    query: String
): List<PdfSearchResult> = withContext(Dispatchers.Default) {
    if (query.isBlank() || pdfPages.isEmpty()) return@withContext emptyList()

    val results = mutableListOf<PdfSearchResult>()
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val lowerQuery = query.trim().lowercase()

    for ((pageIdx, bitmap) in pdfPages.withIndex()) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = Tasks.await(recognizer.process(image))

            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    if (line.text.lowercase().contains(lowerQuery)) {
                        val box = line.boundingBox
                        val normBox = if (box != null && bitmap.width > 0 && bitmap.height > 0) {
                            RectF(
                                box.left.toFloat() / bitmap.width,
                                box.top.toFloat() / bitmap.height,
                                box.right.toFloat() / bitmap.width,
                                box.bottom.toFloat() / bitmap.height
                            )
                        } else null

                        results.add(
                            PdfSearchResult(
                                pageIndex = pageIdx,
                                lineText = line.text,
                                normBoundingBox = normBox
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    results
}

/**
 * Extract filename from Content Uri
 */
private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var fileName = "Document.pdf"
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
    } else if (uri.path != null) {
        val file = File(uri.path!!)
        fileName = file.name
    }
    return fileName
}
