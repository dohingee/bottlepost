package com.bottlepost.app.ads

import android.app.Activity

/**
 * Hook for the planned "ad before sending a letter". Until AdMob is approved the app uses
 * [NoOpAdGate]. An AdMob implementation must: preload an interstitial, show it only after the user
 * taps Send (never on launch or exit), cap frequency (Remote Config, see tools/ad-config.ts), and
 * collect UMP consent for EEA/UK users first. See .claude/skills/enable-admob.
 */
interface AdGate {
    /** Returns when the user may proceed. Never block sending if an ad fails to load. */
    suspend fun beforeSend(activity: Activity)
}

object NoOpAdGate : AdGate {
    override suspend fun beforeSend(activity: Activity) = Unit
}
