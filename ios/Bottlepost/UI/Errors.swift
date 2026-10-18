import Foundation

/** Maps server error codes (HttpsError messages in firebase/functions) to string keys, like Errors.kt. */
func errorMessageKey(_ error: ApiError) -> String {
    switch error.code {
    case "daily_limit": "error_daily_limit"
    case "contact_info_not_allowed", "crypto_not_allowed": "error_contact_info"
    case "duplicate_letter": "error_duplicate"
    case "no_recipient_available": "error_no_recipient"
    case "service_paused": "error_service_paused"
    case "body_too_short": "error_too_short"
    case "body_too_long": "error_too_long"
    case "account_suspended": "error_suspended"
    case "account_banned": "error_banned"
    case "underage": "error_underage"
    case "thread_closed", "thread_not_found", "not_a_participant": "error_thread_closed"
    case "wait_for_reply": "error_wait_for_reply"
    case "report_limit": "error_report_limit"
    case "appeal_pending": "error_appeal_pending"
    case "google_link_required", "google_not_linked", "email_unverified": "error_google_required"
    case "no_google_account": "error_no_google_account"
    case "profile_required": "error_profile_required"
    case "network": "error_network"
    default: "error_generic"
    }
}

func errorMessage(_ error: ApiError) -> String { L(errorMessageKey(error)) }
