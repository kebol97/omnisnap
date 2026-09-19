package com.cococue.omnisnap.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class AdsRemoteConfig(
    val showBanner: Boolean = true,
    val showInterstitial: Boolean = true,
    val showNative: Boolean = true,
    val showRewarded: Boolean = true,
    val bannerAdUnitId: String = "ca-app-pub-3940256099942544/6300978111",
    val interstitialAdUnitId: String = "ca-app-pub-3940256099942544/1033173712",
    val rewardedAdUnitId: String = "ca-app-pub-3940256099942544/5224354917",
    val nativeAdUnitId: String = "ca-app-pub-3940256099942544/2247696110",
    val watermarkRemovalRewarded: Boolean = true,
    val nativeAdInterval: Int = 3,
    val googleMapsApiKey: String = ""
)

object AdManager {
    private const val TAG = "OmniSnapAdManager"
    private const val REMOTE_CONFIG_URL = "https://raw.githubusercontent.com/cococue/omnisnap-config/main/ads_config.json"

    var config = AdsRemoteConfig()
        private set

    // Test Ad Unit IDs recommended by AdMob
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    const val NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var isAdLoading = false
    private var lastAdShowTime = 0L
    private const val AD_COOLDOWN_MS = 25000L

    fun init(context: Context) {
        MobileAds.initialize(context) { status ->
            Log.d(TAG, "AdMob MobileAds initialized: ${status.adapterStatusMap}")
        }
    }

    /**
     * Dynamically update Native Ad interval at runtime
     */
    fun updateNativeAdInterval(newInterval: Int) {
        val sanitized = newInterval.coerceAtLeast(1)
        config = config.copy(nativeAdInterval = sanitized)
        Log.d(TAG, "Native Ad interval updated to: $sanitized")
    }

    /**
     * Dynamically update Google Maps API key at runtime
     */
    fun updateGoogleMapsApiKey(newKey: String) {
        config = config.copy(googleMapsApiKey = newKey.trim())
        Log.d(TAG, "Google Maps API Key updated")
    }

    /**
     * Fetch Remote Config JSON from GitHub URL with safe fallback
     */
    suspend fun fetchRemoteConfig(): AdsRemoteConfig = withContext(Dispatchers.IO) {
        try {
            val jsonText = URL(REMOTE_CONFIG_URL).readText()
            val json = JSONObject(jsonText)
            val parsedConfig = AdsRemoteConfig(
                showBanner = json.optBoolean("show_banner", true),
                showInterstitial = json.optBoolean("show_interstitial", true),
                showNative = json.optBoolean("show_native", true),
                showRewarded = json.optBoolean("show_rewarded", true),
                bannerAdUnitId = json.optString("banner_id", BANNER_AD_UNIT_ID),
                interstitialAdUnitId = json.optString("interstitial_id", INTERSTITIAL_AD_UNIT_ID),
                rewardedAdUnitId = json.optString("rewarded_id", REWARDED_AD_UNIT_ID),
                nativeAdUnitId = json.optString("native_id", NATIVE_AD_UNIT_ID),
                watermarkRemovalRewarded = json.optBoolean("watermark_removal_rewarded", true),
                nativeAdInterval = json.optInt("native_ad_interval", 3),
                googleMapsApiKey = json.optString("google_maps_api_key", "")
            )
            config = parsedConfig
            Log.d(TAG, "Remote config loaded from GitHub: $parsedConfig")
        } catch (e: Exception) {
            Log.w(TAG, "Remote config fetch failed, using default: ${e.message}")
        }
        return@withContext config
    }

    /**
     * Request UMP consent for GDPR / European Privacy Compliance
     */
    fun requestConsent(activity: Activity, onConsentGathered: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.errorCode}: ${formError.message}")
                    }
                    onConsentGathered()
                }
            },
            { requestConsentError ->
                Log.w(TAG, "Consent info update failed: ${requestConsentError.errorCode}: ${requestConsentError.message}")
                onConsentGathered()
            }
        )
    }

    /**
     * Trigger GDPR Privacy Settings Form manually for EU users
     */
    fun showPrivacyOptionsForm(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Error showing privacy options form: ${formError.message}")
            }
        }
    }

    /**
     * Preload Interstitial Ad
     */
    fun loadInterstitialAd(context: Context) {
        if (!config.showInterstitial || interstitialAd != null || isAdLoading) return
        isAdLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            config.interstitialAdUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isAdLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isAdLoading = false
                }
            }
        )
    }

    /**
     * Show Interstitial Ad safely
     */
    fun showInterstitialAd(activity: Activity, onDismiss: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (config.showInterstitial && interstitialAd != null && (currentTime - lastAdShowTime > AD_COOLDOWN_MS)) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    lastAdShowTime = System.currentTimeMillis()
                    loadInterstitialAd(activity)
                    onDismiss()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    onDismiss()
                }
            }
            interstitialAd?.show(activity)
        } else {
            onDismiss()
        }
    }

    /**
     * Load Rewarded Ad for Watermark Removal
     */
    fun loadRewardedAd(context: Context, onLoaded: (() -> Unit)? = null) {
        if (!config.showRewarded) return
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            config.rewardedAdUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    /**
     * Show Rewarded Ad and trigger callback when user earns reward
     */
    fun showRewardedAd(activity: Activity, onRewardEarned: () -> Unit, onFailed: () -> Unit) {
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewardedAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    onFailed()
                }
            }
            rewardedAd?.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onRewardEarned()
            }
        } else {
            // Preload and inform user
            loadRewardedAd(activity)
            onFailed()
        }
    }

    /**
     * Load Native Ad
     */
    fun loadNativeAd(context: Context, onNativeAdLoaded: (NativeAd) -> Unit) {
        if (!config.showNative) return
        val adLoader = AdLoader.Builder(context, config.nativeAdUnitId)
            .forNativeAd { nativeAd ->
                onNativeAdLoaded(nativeAd)
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.w(TAG, "Native ad failed to load: ${adError.message}")
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
    }
}
