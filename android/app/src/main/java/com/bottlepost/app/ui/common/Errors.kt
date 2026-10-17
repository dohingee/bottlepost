package com.bottlepost.app.ui.common

import androidx.annotation.StringRes
import com.bottlepost.app.R
import com.bottlepost.app.data.remote.ApiException

/** Maps server error codes (HttpsError messages in firebase/functions) to user-facing text. */
@StringRes
fun errorMessage(t: Throwable): Int = when ((t as? ApiException)?.code) {
    "daily_limit" -> R.string.error_daily_limit
    "contact_info_not_allowed" -> R.string.error_contact_info
    "crypto_not_allowed" -> R.string.error_contact_info
    "duplicate_letter" -> R.string.error_duplicate
    "no_recipient_available" -> R.string.error_no_recipient
    "service_paused" -> R.string.error_service_paused
    "body_too_short" -> R.string.error_too_short
    "body_too_long" -> R.string.error_too_long
    "account_suspended" -> R.string.error_suspended
    "account_banned" -> R.string.error_banned
    "underage" -> R.string.error_underage
    "thread_closed" -> R.string.error_thread_closed
    "wait_for_reply" -> R.string.error_wait_for_reply
    "report_limit" -> R.string.error_report_limit
    "appeal_pending" -> R.string.error_appeal_pending
    "google_link_required" -> R.string.error_google_required
    "no_google_account" -> R.string.error_no_google_account
    "network" -> R.string.error_network
    else -> R.string.error_generic
}
