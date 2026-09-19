package com.cococue.omnisnap.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.cococue.omnisnap.data.model.DocumentItem
import com.cococue.omnisnap.data.repository.DocumentRepository
import com.cococue.omnisnap.ui.components.AdNativeView
import com.cococue.omnisnap.ui.components.FeatureCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToScan: () -> Unit,
    onNavigateToTimestamp: () -> Unit,
    onNavigateToPdfTools: () -> Unit,
    onNavigateToConverter: () -> Unit,
    onNavigateToFiles: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(context) }
    var recentDocs by remember { mutableStateOf<List<DocumentItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        recentDocs = repository.getAllDocuments().take(5)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "OmniSnap",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Text(
                            text = "CamScanner & Timestamp Suite",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Hero Quick Action Grid
            item {
                Text(
                    text = "Main Features",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureCard(
                        title = "CamScan Doc",
                        subtitle = "Precision Crop & Fit",
                        icon = Icons.Default.DocumentScanner,
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToScan
                    )

                    FeatureCard(
                        title = "Timestamp",
                        subtitle = "GPS & Location Map",
                        icon = Icons.Default.CameraAlt,
                        containerColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToTimestamp
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureCard(
                        title = "PDF Tools",
                        subtitle = "Sign, Merge & Compress",
                        icon = Icons.Default.PictureAsPdf,
                        containerColor = Color(0xFFE11D48),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToPdfTools
                    )

                    FeatureCard(
                        title = "Converter",
                        subtitle = "Compress & OCR Text",
                        icon = Icons.Default.Transform,
                        containerColor = Color(0xFFD97706),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToConverter
                    )
                }
            }

            // Native Ad Layout (Banner Ad on Home removed as requested)
            item {
                AdNativeView()
            }

            // Recent Documents Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Documents",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNavigateToFiles() }
                    )
                }
            }

            if (recentDocs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No documents scanned yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap 'CamScan Doc' or 'Timestamp' to start",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                items(recentDocs) { doc ->
                    DocumentRowItem(doc = doc, onClick = onNavigateToFiles)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun DocumentRowItem(
    doc: DocumentItem,
    onClick: () -> Unit
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
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (doc.isPdf) Color(0xFFFEE2E2) else Color(0xFFE0F2FE)
                    ),
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
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${doc.formattedSize} • ${doc.type.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
