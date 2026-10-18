package com.bottlepost.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.bottlepost.app.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Interstitial shown after the user taps Send, before the letter leaves. Rules (also in
 * .claude/skills/enable-admob): consent first (UMP), never on launch/exit or while reading,
 * reporting or deleting, frequency-capped, and never blocks sending — no ad within 2 s means no ad.
 *
 * Release builds stay ad-free until Remote Config `ads_enabled` is turned on (after AdMob approves
 * the app). Debug builds always run with Google's test ads.
 */
class AdMobAdGate(context: Context) : AdGate {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("ads", Context.MODE_PRIVATE)
    private val remote = FirebaseRemoteConfig.getInstance()
    private val initialized = AtomicBoolean(false)
    private var loading = false
    private var ad: InterstitialAd? = null
    private var consent: ConsentInformation? = null

    private val _privacyOptionsRequired = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired

    init {
        remote.setDefaultsAsync(
            mapOf(
                KEY_ENABLED to false,
                KEY_INTERVAL to 180L,
                KEY_SKIP_FIRST to 1L,
                KEY_MAX_PER_DAY to 5L,
            ),
        )
    }

    private val enabled get() = BuildConfig.DEBUG || remote.getBoolean(KEY_ENABLED)
    private val intervalMs get() = (if (BuildConfig.DEBUG) 60L else remote.getLong(KEY_INTERVAL).coerceAtLeast(60)) * 1000
    private val skipFirst get() = if (BuildConfig.DEBUG) 0L else remote.getLong(KEY_SKIP_FIRST)
    private val maxPerDay get() = if (BuildConfig.DEBUG) 50L else remote.getLong(KEY_MAX_PER_DAY)

    /** Fetches the switch, then asks for consent (EEA/UK/CH and US states) only if ads are on. */
    override fun start(activity: Activity) {
        remote.fetchAndActivate().addOnCompleteListener {
            if (!enabled || activity.isFinishing) return@addOnCompleteListener
            val info = UserMessagingPlatform.getConsentInformation(activity)
            consent = info
            info.requestConsentInfoUpdate(
                activity,
                ConsentRequestParameters.Builder().build(),
                {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                        if (error != null) Log.w(TAG, "consent form: ${error.message}")
                        afterConsent(info)
                    }
                },
                { error ->
                    Log.w(TAG, "consent update: ${error.message}")
                    afterConsent(info)
                },
            )
            // Consent from an earlier session is enough to start loading right away.
            if (info.canRequestAds()) initAds()
        }
    }

    private fun afterConsent(info: ConsentInformation) {
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (info.canRequestAds()) initAds()
    }

    override fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "privacy options: ${error.message}")
            consent?.let(::afterConsent)
        }
    }

    private fun initAds() {
        if (!initialized.compareAndSet(false, true)) return
        MobileAds.initialize(app) { preload() }
    }

    private fun preload() {
        if (ad != null || loading || !initialized.get()) return
        loading = true
        InterstitialAd.load(
            app,
            BuildConfig.ADMOB_INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    ad = loaded
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.i(TAG, "no interstitial: ${error.code} ${error.message}")
                    loading = false
                }
            },
        )
    }

    override suspend fun beforeSend(activity: Activity) {
        if (!enabled || consent?.canRequestAds() != true) return
        val sends = prefs.getLong(PREF_SENDS, 0)
        prefs.edit { putLong(PREF_SENDS, sends + 1) }
        if (sends < skipFirst) return

        val now = System.currentTimeMillis()
        if (now - prefs.getLong(PREF_LAST_SHOWN, 0) < intervalMs) return
        val today = now / DAY_MS
        val shownToday = if (prefs.getLong(PREF_DAY, -1) == today) prefs.getLong(PREF_DAY_COUNT, 0) else 0
        if (shownToday >= maxPerDay) return

        preload()
        val ready = ad ?: withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            while (ad == null) delay(100)
            ad
        } ?: return
        ad = null

        suspendCancellableCoroutine { cont ->
            ready.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            ready.show(activity)
        }
        prefs.edit {
            putLong(PREF_LAST_SHOWN, now)
            putLong(PREF_DAY, today)
            putLong(PREF_DAY_COUNT, shownToday + 1)
        }
        preload()
    }

    private companion object {
        const val TAG = "AdMobAdGate"
        const val LOAD_TIMEOUT_MS = 2_000L
        const val DAY_MS = 86_400_000L
        // Remote Config keys; tools/ad-config.ts writes the matching template.
        const val KEY_ENABLED = "ads_enabled"
        const val KEY_INTERVAL = "ads_interstitial_min_interval_sec"
        const val KEY_SKIP_FIRST = "ads_skip_first_letters"
        const val KEY_MAX_PER_DAY = "ads_max_per_day"
        const val PREF_SENDS = "sends"
        const val PREF_LAST_SHOWN = "last_shown"
        const val PREF_DAY = "day"
        const val PREF_DAY_COUNT = "day_count"
    }
}
