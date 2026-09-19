package com.cococue.omnisnap

import android.app.Application
import com.cococue.omnisnap.ads.AdManager

class OmniSnapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize AdMob Mobile Ads SDK and consent framework
        AdManager.init(this)
    }
}
