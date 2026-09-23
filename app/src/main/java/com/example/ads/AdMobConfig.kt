package com.example.ads

/**
 * AdMob Configuration for Compress Image to 100KB.
 * Uses official Google Mobile Ads test IDs for development and verification.
 * Replace with production AdMob Ad Unit IDs prior to Google Play publishing.
 */
object AdMobConfig {
    const val IS_TEST_MODE = true

    // Official Google Mobile Ads Test App ID
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"

    // Official Google Mobile Ads Test Ad Unit IDs
    const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/9214589741"
    const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    // Production Placeholders (Replace when publishing to Google Play)
    private const val PROD_BANNER_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
    private const val PROD_INTERSTITIAL_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"

    val bannerAdUnitId: String
        get() = if (IS_TEST_MODE) TEST_BANNER_ID else PROD_BANNER_ID

    val interstitialAdUnitId: String
        get() = if (IS_TEST_MODE) TEST_INTERSTITIAL_ID else PROD_INTERSTITIAL_ID
}
