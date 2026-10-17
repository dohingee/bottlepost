package com.bottlepost.app.data.remote

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/** Region must match REGION in firebase/functions/src/config.ts. */
private const val REGION = "asia-northeast3"

data class Profile(
    val gender: String,
    val birthYear: Int,
    val lang: String,
    val receiveFrom: String,
    val nickname: String,
    val linked: Boolean,
    val status: String,
    val suspendedUntil: Long?,
)

data class SentLetter(val threadId: String, val deliveryId: String, val sentAt: Long, val sig: String)

data class OutgoingReportMessage(
    val deliveryId: String,
    val body: String,
    val sentAt: Long,
    val sig: String,
    val sent: Boolean,
)

/** Server error codes (HttpsError messages) mapped to string resources in ui/common/Errors.kt. */
class ApiException(val code: String, cause: Throwable?) : Exception(code, cause)

/** Thin wrapper over the callable Cloud Functions in firebase/functions/src. */
class BottlepostApi(private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(REGION)) {

    private suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Any? = try {
        functions.getHttpsCallable(name).call(data).await().getData()
    } catch (e: FirebaseFunctionsException) {
        throw ApiException(e.message ?: e.code.name.lowercase(), e)
    } catch (e: Exception) {
        throw ApiException("network", e)
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMap(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

    private fun Map<String, Any?>.toProfile() = Profile(
        gender = get("gender") as? String ?: "",
        birthYear = (get("birthYear") as? Number)?.toInt() ?: 0,
        lang = get("lang") as? String ?: "en",
        receiveFrom = get("receiveFrom") as? String ?: "ANY",
        nickname = get("nickname") as? String ?: "",
        linked = get("linked") as? Boolean ?: false,
        status = get("status") as? String ?: "active",
        suspendedUntil = (get("suspendedUntil") as? Number)?.toLong(),
    )

    suspend fun createProfile(
        gender: String,
        birthYear: Int,
        lang: String,
        country: String,
        receiveFrom: String,
        nickname: String,
    ): Profile = call(
        "createProfile",
        mapOf(
            "gender" to gender, "birthYear" to birthYear, "lang" to lang, "country" to country,
            "receiveFrom" to receiveFrom, "nickname" to nickname,
            "acceptedTerms" to true, "termsVersion" to "1",
        ),
    ).asMap().toProfile()

    /** Null when this account has no profile yet (fresh install, needs onboarding). */
    suspend fun getProfile(): Profile? = (call("getProfile") as? Map<*, *>)?.let {
        @Suppress("UNCHECKED_CAST")
        (it as Map<String, Any?>).toProfile()
    }

    suspend fun updateProfile(changes: Map<String, Any?>): Profile = call("updateProfile", changes).asMap().toProfile()

    suspend fun registerPushToken(token: String) {
        call("registerPushToken", mapOf("token" to token))
    }

    suspend fun confirmGoogleLink(): Profile = call("confirmGoogleLink").asMap().toProfile()

    suspend fun getBackupKey(): String = call("getBackupKey").asMap()["key"] as String

    suspend fun sendLetter(body: String, wantGender: String, sameLanguageOnly: Boolean): SentLetter =
        call("sendLetter", mapOf("body" to body, "wantGender" to wantGender, "sameLanguageOnly" to sameLanguageOnly))
            .asMap().toSent()

    suspend fun replyLetter(threadId: String, body: String): SentLetter =
        call("replyLetter", mapOf("threadId" to threadId, "body" to body)).asMap().toSent()

    suspend fun blockThread(threadId: String) {
        call("blockThread", mapOf("threadId" to threadId))
    }

    suspend fun submitReport(threadId: String, reason: String, note: String, messages: List<OutgoingReportMessage>) {
        call(
            "submitReport",
            mapOf(
                "threadId" to threadId,
                "reason" to reason,
                "note" to note,
                "messages" to messages.map {
                    mapOf(
                        "deliveryId" to it.deliveryId, "body" to it.body, "sentAt" to it.sentAt,
                        "sig" to it.sig, "direction" to if (it.sent) "sent" else "received",
                    )
                },
            ),
        )
    }

    suspend fun submitAppeal(message: String, kind: String) {
        call("submitAppeal", mapOf("message" to message, "kind" to kind))
    }

    suspend fun deleteAccount() {
        call("deleteAccount")
    }

    private fun Map<String, Any?>.toSent() = SentLetter(
        threadId = get("threadId") as String,
        deliveryId = get("deliveryId") as String,
        sentAt = (get("sentAt") as Number).toLong(),
        sig = get("sig") as String,
    )
}
