package com.cococue.omnisnap.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
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
import java.net.HttpURLConnection
import java.net.URL

data class AdsRemoteConfig(
    val showBanner: Boolean = false,
    val showInterstitial: Boolean = false,
    val showNative: Boolean = false,
    val showRewarded: Boolean = false,
    val showAppOpen: Boolean = false,
    val bannerAdUnitId: String = "",
    val interstitialAdUnitId: String = "",
    val rewardedAdUnitId: String = "",
    val nativeAdUnitId: String = "",
    val appOpenAdUnitId: String = "",
    val watermarkRemovalRewarded: Boolean = false
)

object AdManager {
    private const val TAG = "OmniSnapAdManager"
    private const val REMOTE_CONFIG_URL = "https://raw.githubusercontent.com/kebol97/omnisnap/refs/heads/master/ads_omnisnap.json"

    var config = AdsRemoteConfig()
        private set

    private var interstitialAd: InterstitialAd? = null
    private var interstitialAdLoadTime = 0L

    private var rewardedAd: RewardedAd? = null

    private var appOpenAd: AppOpenAd? = null
    private var appOpenAdLoadTime = 0L

    var isAdLoading = false
        private set

    var isAppOpenAdLoading = false
        private set

    var isInitialized = false
        private set

    private var lastAdShowTime = 0L
    private const val AD_COOLDOWN_MS = 25000L
    private const val FOUR_HOURS_MS = 4 * 3600 * 1000L // 4 hours frequency capping & ad expiration rule

    /**
     * Non-blocking AdMob SDK Initialization
     */
    fun init(context: Context) {
        if (isInitialized) return
        MobileAds.initialize(context) { status ->
            isInitialized = true
            Log.d(TAG, "AdMob MobileAds initialized successfully: ${status.adapterStatusMap}")
        }
    }

    /**
     * Fetch Remote Config JSON directly from GitHub.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun fetchRemoteConfig(context: Context? = null): AdsRemoteConfig = withContext(Dispatchers.IO) {
        try {
            val url = URL(REMOTE_CONFIG_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000 // 3 seconds connect timeout
                readTimeout = 3000    // 3 seconds read timeout
                requestMethod = "GET"
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonText)

                config = AdsRemoteConfig(
                    showBanner = json.optBoolean("show_banner", false),
                    showInterstitial = json.optBoolean("show_interstitial", false),
                    showNative = json.optBoolean("show_native", false),
                    showRewarded = json.optBoolean("show_rewarded", false),
                    showAppOpen = json.optBoolean("show_app_open", false),
                    bannerAdUnitId = json.optString("banner_id", "").cleanId(),
                    interstitialAdUnitId = json.optString("interstitial_id", "").cleanId(),
                    rewardedAdUnitId = json.optString("rewarded_id", "").cleanId(),
                    nativeAdUnitId = json.optString("native_id", "").cleanId(),
                    appOpenAdUnitId = json.optString("app_open_id", "").cleanId(),
                    watermarkRemovalRewarded = json.optBoolean("watermark_removal_rewarded", false)
                )
                Log.d(TAG, "Remote config fetched successfully from GitHub: $config")
            } else {
                Log.w(TAG, "Remote config fetch failed (HTTP ${connection.responseCode}). Disabling ads.")
                config = AdsRemoteConfig()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote config fetch failed or timed out (${e.message}). Disabling ads.")
            config = AdsRemoteConfig()
        }
        return@withContext config
    }

    private fun String.cleanId(): String {
        return this.trim().removePrefix("\"").removeSuffix("\"")
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
     * Helper to check if loaded App Open Ad is valid and not expired (< 4 hours old)
     */
    fun isAppOpenAdAvailable(): Boolean {
        return appOpenAd != null && (System.currentTimeMillis() - appOpenAdLoadTime) < FOUR_HOURS_MS
    }

    /**
     * Helper to check if loaded Interstitial Ad is valid and not expired (< 4 hours old)
     */
    fun isInterstitialAdAvailable(): Boolean {
        return interstitialAd != null && (System.currentTimeMillis() - interstitialAdLoadTime) < FOUR_HOURS_MS
    }

    /**
     * Preload all essential ads concurrently during startup / background warm-up
     */
    fun preloadStartupAds(context: Context) {
        loadAppOpenAd(context)
        loadInterstitialAd(context)
        loadRewardedAd(context)
    }

    /**
     * Load App Open Ad with expiration tracking
     */
    fun loadAppOpenAd(context: Context, onLoaded: (() -> Unit)? = null) {
        if (!config.showAppOpen || config.appOpenAdUnitId.isBlank()) {
            onLoaded?.invoke()
            return
        }
        if (isAppOpenAdAvailable()) {
            Log.d(TAG, "App Open Ad is already available & cached")
            onLoaded?.invoke()
            return
        }
        if (isAppOpenAdLoading) {
            return
        }
        isAppOpenAdLoading = true

        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            config.appOpenAdUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    appOpenAdLoadTime = System.currentTimeMillis()
                    isAppOpenAdLoading = false
                    Log.d(TAG, "App Open Ad loaded successfully at $appOpenAdLoadTime")
                    onLoaded?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    appOpenAd = null
                    isAppOpenAdLoading = false
                    Log.w(TAG, "App Open Ad failed to load: ${loadAdError.message}")
                    onLoaded?.invoke()
                }
            }
        )
    }

    /**
     * Show App Open Ad with strict 4-hour frequency capping policy and expiration check
     */
    fun showAppOpenAdIfEligible(activity: Activity, onFinished: () -> Unit) {
        if (!config.showAppOpen || config.appOpenAdUnitId.isBlank()) {
            onFinished()
            return
        }

        val prefs = activity.getSharedPreferences("omnisnap_prefs", Context.MODE_PRIVATE)
        val lastAppOpenTime = prefs.getLong("last_app_open_ad_time", 0L)
        val currentTime = System.currentTimeMillis()

        // If using AdMob Test Ad Unit ID, allow testing without 4-hour delay
        val isTestAd = config.appOpenAdUnitId.contains("3940256099942544")
        val isEligible4Hours = isTestAd || (currentTime - lastAppOpenTime) >= FOUR_HOURS_MS

        if (isEligible4Hours && isAppOpenAdAvailable()) {
            appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    prefs.edit { putLong("last_app_open_ad_time", System.currentTimeMillis()) }
                    loadAppOpenAd(activity)
                    onFinished()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    appOpenAd = null
                    onFinished()
                }
            }
            appOpenAd?.show(activity)
        } else {
            if (!isEligible4Hours) {
                val remainingMins = (FOUR_HOURS_MS - (currentTime - lastAppOpenTime)) / 60000L
                Log.d(TAG, "App Open Ad skipped due to 4-hour frequency cap. Next in $remainingMins mins.")
            } else if (!isAppOpenAdAvailable() && appOpenAd != null) {
                Log.d(TAG, "App Open Ad discarded because it exceeded 4-hour expiration limit.")
                appOpenAd = null
                loadAppOpenAd(activity)
            }
            onFinished()
        }
    }

    /**
     * Preload Interstitial Ad with expiration tracking
     */
    fun loadInterstitialAd(context: Context) {
        if (!config.showInterstitial || config.interstitialAdUnitId.isBlank()) return
        if (isInterstitialAdAvailable()) {
            Log.d(TAG, "Interstitial Ad already available & cached")
            return
        }
        if (isAdLoading) return
        isAdLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            config.interstitialAdUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    interstitialAdLoadTime = System.currentTimeMillis()
                    isAdLoading = false
                    Log.d(TAG, "Interstitial Ad loaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isAdLoading = false
                    Log.w(TAG, "Interstitial Ad failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Show Interstitial Ad safely with cooldown and expiration check
     */
    fun showInterstitialAd(activity: Activity, onDismiss: () -> Unit) {
        if (!config.showInterstitial || config.interstitialAdUnitId.isBlank()) {
            onDismiss()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (!isInterstitialAdAvailable() && interstitialAd != null) {
            Log.d(TAG, "Interstitial Ad expired. Clearing and reloading.")
            interstitialAd = null
            loadInterstitialAd(activity)
        }

        if (isInterstitialAdAvailable() && (currentTime - lastAdShowTime > AD_COOLDOWN_MS)) {
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
        if (!config.showRewarded || config.rewardedAdUnitId.isBlank()) return
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
        if (!config.showRewarded || config.rewardedAdUnitId.isBlank()) {
            onFailed()
            return
        }

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
            loadRewardedAd(activity)
            onFailed()
        }
    }

    /**
     * Load Native Ad
     */
    fun loadNativeAd(context: Context, onNativeAdLoaded: (NativeAd) -> Unit) {
        if (!config.showNative || config.nativeAdUnitId.isBlank()) return
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
