package com.cococue.omnisnap.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cococue.omnisnap.ads.AdManager
import com.cococue.omnisnap.ui.components.AdBannerView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val activity = context as? Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Privacy", fontWeight = FontWeight.Bold) }
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
            // Banner Ad placed at top (AdMob policy compliant)
            AdBannerView()

            Text("Ad & Privacy Preferences", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            SettingsItem(
                title = "European GDPR Privacy Options",
                subtitle = "Manage or revoke consent preferences for EU/EEA users",
                icon = Icons.Default.Gavel,
                onClick = {
                    if (activity != null) {
                        AdManager.showPrivacyOptionsForm(activity)
                    }
                }
            )

            SettingsItem(
                title = "Reset Consent Form",
                subtitle = "Re-open Google UMP Consent form for ads",
                icon = Icons.Default.PrivacyTip,
                onClick = {
                    if (activity != null) {
                        AdManager.requestConsent(activity) {
                            Toast.makeText(context, "Consent preferences updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            SettingsItem(
                title = "Privacy Policy",
                subtitle = "Read OmniSnap privacy policy & data compliance guidelines",
                icon = Icons.Default.Security,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://berkahabadigame.blogspot.com/p/privacy-policy.html"))
                    context.startActivity(intent)
                }
            )

            Text("Storage & Maintenance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            SettingsItem(
                title = "Clear Cache",
                subtitle = "Free up temporary cache storage used by scanner",
                icon = Icons.Default.CleaningServices,
                onClick = {
                    try {
                        context.cacheDir.deleteRecursively()
                        Toast.makeText(context, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error clearing cache", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Text("About OmniSnap", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("OmniSnap v1.0", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Smart Document Scanner & Utility Suite", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Compliant with GDPR & Google Play Policies", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
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
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
