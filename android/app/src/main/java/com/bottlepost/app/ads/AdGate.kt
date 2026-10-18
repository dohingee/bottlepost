package com.bottlepost.app.ads

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Ad before sending a letter". [AdMobAdGate] is the real one: it preloads an interstitial, shows it
 * only after the user taps Send (never on launch or exit), caps frequency (Remote Config, see
 * tools/ad-config.ts) and collects UMP consent first. See .claude/skills/enable-admob.
 */
interface AdGate {
    /** Once the user is in the app: fetch the ads switch and ask for consent where required. */
    fun start(activity: Activity) {}

    /** Returns when the user may proceed. Never block sending if an ad fails to load. */
    suspend fun beforeSend(activity: Activity)

    /** True when the user must be able to reopen the consent choices (Settings shows a button). */
    val privacyOptionsRequired: StateFlow<Boolean>

    fun showPrivacyOptions(activity: Activity) {}

    /** True once ads may be requested (consent given, ads switched on); the main screen then shows its banner. */
    val bannersAllowed: StateFlow<Boolean>
}

object NoOpAdGate : AdGate {
    override suspend fun beforeSend(activity: Activity) = Unit
    override val privacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(false)
    override val bannersAllowed: StateFlow<Boolean> = MutableStateFlow(false)
}
