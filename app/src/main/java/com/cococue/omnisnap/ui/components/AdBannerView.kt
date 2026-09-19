package com.cococue.omnisnap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cococue.omnisnap.ads.AdManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

@Composable
fun AdBannerView(
    modifier: Modifier = Modifier,
    adUnitId: String = AdManager.config.bannerAdUnitId
) {
    if (!AdManager.config.showBanner) return

    var isAdLoaded by remember { mutableStateOf(false) }

    if (isAdLoaded) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                // AD Badge only visible when ad has successfully loaded
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "ADVERTISEMENT",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    factory = { context ->
                        AdView(context).apply {
                            setAdSize(AdSize.BANNER)
                            setAdUnitId(adUnitId)
                            adListener = object : AdListener() {
                                override fun onAdLoaded() {
                                    isAdLoaded = true
                                }

                                override fun onAdFailedToLoad(adError: LoadAdError) {
                                    isAdLoaded = false
                                }
                            }
                            loadAd(AdRequest.Builder().build())
                        }
                    }
                )
            }
        }
    } else {
        // Invisible off-screen preloader to trigger onAdLoaded without rendering "ADVERTISEMENT" badge prematurely
        AndroidView(
            modifier = Modifier.padding(0.dp),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    setAdUnitId(adUnitId)
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            isAdLoaded = true
                        }

                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            isAdLoaded = false
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}
