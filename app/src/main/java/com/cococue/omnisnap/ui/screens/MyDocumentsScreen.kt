package com.cococue.omnisnap.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.cococue.omnisnap.data.model.DocumentItem
import com.cococue.omnisnap.data.model.DocumentType
import com.cococue.omnisnap.data.repository.DocumentRepository
import com.cococue.omnisnap.ui.components.AdNativeView
import com.cococue.omnisnap.util.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDocumentsScreen() {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(context) }
    val scope = rememberCoroutineScope()

    var allDocs by remember { mutableStateOf<List<DocumentItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<DocumentType?>(null) }

    var selectedDocForMenu by remember { mutableStateOf<DocumentItem?>(null) }
    var docToPreview by remember { mutableStateOf<DocumentItem?>(null) }
    var docToRename by remember { mutableStateOf<DocumentItem?>(null) }
    var newRenameValue by remember { mutableStateOf("") }

    fun loadDocs() {
        scope.launch {
            allDocs = repository.getAllDocuments()
        }
    }

    LaunchedEffect(Unit) {
        loadDocs()
    }

    val filteredDocs = allDocs.filter { doc ->
        val matchesType = selectedFilter == null || doc.type == selectedFilter
        val matchesQuery = searchQuery.isBlank() || doc.name.contains(searchQuery, ignoreCase = true)
        matchesType && matchesQuery
    }

    val nativeInterval = AdManager.config.nativeAdInterval.coerceAtLeast(1)

    // Full Screen Overlay Preview Mode
    if (docToPreview != null) {
        val doc = docToPreview!!
        BackHandler { docToPreview = null }

        var previewBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
        var isLoadingPreview by remember { mutableStateOf(true) }

        LaunchedEffect(doc) {
            isLoadingPreview = true
            withContext(Dispatchers.IO) {
                try {
                    if (doc.isPdf) {
                        previewBitmaps = PdfUtils.renderPdfToBitmaps(doc.file, maxPages = 20)
                    } else {
                        val bitmap = BitmapFactory.decodeFile(doc.file.absolutePath)
                        if (bitmap != null) {
                            previewBitmaps = listOf(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoadingPreview = false
                }
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = doc.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "${doc.formattedSize} • ${doc.type.name}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { docToPreview = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close Preview")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                Surface(
                    tonalElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { repository.shareDocument(doc) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                docToRename = doc
                                newRenameValue = doc.name.substringBeforeLast(".")
                                docToPreview = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rename", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    repository.deleteDocument(doc)
                                    loadDocs()
                                    docToPreview = null
                                    Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingPreview) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading document preview...", color = Color.White, fontSize = 14.sp)
                    }
                } else if (previewBitmaps.isEmpty()) {
                    Text("Preview unavailable for this file format", color = Color.LightGray)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(previewBitmaps) { bmp ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page Preview",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Files & Documents", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search files by name...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Category Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All Files") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == DocumentType.SCAN,
                        onClick = { selectedFilter = DocumentType.SCAN },
                        label = { Text("Scans") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == DocumentType.TIMESTAMP_PHOTO,
                        onClick = { selectedFilter = DocumentType.TIMESTAMP_PHOTO },
                        label = { Text("Timestamp Photos") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == DocumentType.PDF,
                        onClick = { selectedFilter = DocumentType.PDF },
                        label = { Text("PDFs") }
                    )
                }
            }

            if (filteredDocs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matching files found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(filteredDocs) { index, doc ->
                        DocumentListItem(
                            doc = doc,
                            onClick = { docToPreview = doc },
                            onOptionClick = { selectedDocForMenu = doc },
                            onShareClick = { repository.shareDocument(doc) }
                        )

                        // Insert Native Ad at configured interval between items
                        if ((index + 1) % nativeInterval == 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            AdNativeView()
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }

    // Bottom Sheet Options Menu for Document
    selectedDocForMenu?.let { doc ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { selectedDocForMenu = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = doc.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Size: ${doc.formattedSize} • Path: ${doc.file.parent}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            docToPreview = doc
                            selectedDocForMenu = null
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Preview File", fontSize = 16.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            repository.shareDocument(doc)
                            selectedDocForMenu = null
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Share Document", fontSize = 16.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            docToRename = doc
                            newRenameValue = doc.name.substringBeforeLast(".")
                            selectedDocForMenu = null
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Rename File", fontSize = 16.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                repository.deleteDocument(doc)
                                loadDocs()
                                selectedDocForMenu = null
                                Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Delete File", fontSize = 16.sp, color = Color.Red)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Rename Dialog
    docToRename?.let { doc ->
        AlertDialog(
            onDismissRequest = { docToRename = null },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = newRenameValue,
                    onValueChange = { newRenameValue = it },
                    label = { Text("New file name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val renamed = repository.renameDocument(doc, newRenameValue)
                            if (renamed != null) {
                                Toast.makeText(context, "Renamed to ${renamed.name}", Toast.LENGTH_SHORT).show()
                                loadDocs()
                            } else {
                                Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                            }
                            docToRename = null
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { docToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DocumentListItem(
    doc: DocumentItem,
    onClick: () -> Unit,
    onOptionClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (doc.isPdf) Color(0xFFFEE2E2) else Color(0xFFE0F2FE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (doc.isPdf) Icons.Default.PictureAsPdf else Icons.Outlined.Description,
                    contentDescription = null,
                    tint = if (doc.isPdf) Color(0xFFDC2626) else Color(0xFF0284C7)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${doc.formattedSize} • ${doc.type.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onShareClick) {
                Icon(Icons.Default.Share, contentDescription = "Share")
            }

            IconButton(onClick = onOptionClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
        }
    }
}
