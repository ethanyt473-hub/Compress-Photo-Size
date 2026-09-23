package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {
    private const val TAG = "AdManager"
    private var interstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    private var lastInterstitialShownTime = 0L
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 45_000L // 45-second frequency cap

    fun initialize(context: Context) {
        try {
            MobileAds.initialize(context) { status ->
                Log.d(TAG, "MobileAds initialized: $status")
                loadInterstitial(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MobileAds", e)
        }
    }

    fun loadInterstitial(context: Context) {
        if (interstitialAd != null || isAdLoading) return

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            AdMobConfig.interstitialAdUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isAdLoading = false
                    Log.d(TAG, "Interstitial ad successfully loaded")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isAdLoading = false
                    Log.w(TAG, "Interstitial ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Shows an interstitial ad at natural breaks (e.g. after compression workflow),
     * respecting the frequency cap. Calls [onDismiss] whether shown, failed, or skipped.
     */
    fun showInterstitial(activity: Activity, onDismiss: () -> Unit) {
        val now = System.currentTimeMillis()
        val ad = interstitialAd

        if (ad != null && (now - lastInterstitialShownTime > MIN_INTERSTITIAL_INTERVAL_MS)) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    lastInterstitialShownTime = System.currentTimeMillis()
                    loadInterstitial(activity)
                    onDismiss()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    loadInterstitial(activity)
                    onDismiss()
                }
            }
            ad.show(activity)
        } else {
            // Frequency capped or ad not ready yet - continue seamlessly without blocking
            onDismiss()
        }
    }
}
