package com.cococue.omnisnap.ui.components

import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cococue.omnisnap.ads.AdManager
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

@Composable
fun AdNativeView(
    modifier: Modifier = Modifier
) {
    if (!AdManager.config.showNative) return

    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(Unit) {
        AdManager.loadNativeAd(context) { ad ->
            nativeAd = ad
        }
        onDispose {
            nativeAd?.destroy()
        }
    }

    // Badge and Card ONLY shown if nativeAd has loaded successfully and is non-null
    nativeAd?.let { ad ->
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Sponsored Badge
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SPONSORED",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        val adView = NativeAdView(ctx)

                        val container = android.widget.LinearLayout(ctx).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            setPadding(8, 8, 8, 8)
                        }

                        val iconView = ImageView(ctx).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(120, 120)
                        }

                        val textLayout = android.widget.LinearLayout(ctx).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                0,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { setMargins(16, 0, 16, 0) }
                        }

                        val headlineView = TextView(ctx).apply {
                            textSize = 15f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        }

                        val bodyView = TextView(ctx).apply {
                            textSize = 12f
                            maxLines = 2
                        }

                        val callToActionView = Button(ctx).apply {
                            textSize = 12f
                        }

                        textLayout.addView(headlineView)
                        textLayout.addView(bodyView)

                        container.addView(iconView)
                        container.addView(textLayout)
                        container.addView(callToActionView)

                        adView.addView(container)

                        adView.iconView = iconView
                        adView.headlineView = headlineView
                        adView.bodyView = bodyView
                        adView.callToActionView = callToActionView

                        adView
                    },
                    update = { adView ->
                        adView.setNativeAd(ad)
                        (adView.headlineView as? TextView)?.text = ad.headline
                        (adView.bodyView as? TextView)?.text = ad.body
                        (adView.callToActionView as? Button)?.text = ad.callToAction ?: "Install"

                        ad.icon?.drawable?.let { drawable ->
                            (adView.iconView as? ImageView)?.setImageDrawable(drawable)
                        }
                    }
                )
            }
        }
    }
}
