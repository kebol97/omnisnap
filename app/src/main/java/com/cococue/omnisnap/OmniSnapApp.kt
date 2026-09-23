package com.cococue.omnisnap

import android.app.Application
import com.cococue.omnisnap.ads.AdManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OmniSnapApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Initialize AdMob Mobile Ads SDK asynchronously on background thread to prevent blocking app startup
        appScope.launch {
            AdManager.init(this@OmniSnapApp)
        }
    }
}
