package com.bottlepost.app.backup

import android.app.PendingIntent
import android.content.Context
import android.util.Base64
import com.bottlepost.app.data.local.LetterEntity
import com.bottlepost.app.data.local.MailDao
import com.bottlepost.app.data.local.ThreadEntity
import com.bottlepost.app.data.remote.BottlepostApi
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val createdAt: Long,
    val threads: List<ThreadEntity>,
    val letters: List<LetterEntity>,
)

sealed interface DriveAccess {
    data class Granted(val accessToken: String) : DriveAccess
    /** The user must approve Drive access once; launch with an IntentSenderRequest. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAccess
}

/**
 * Backs up the local mailbox to the user's own Google Drive "appDataFolder" (hidden, app-only).
 * The file is AES-GCM encrypted with a per-user key held by our server, so neither Google Drive
 * nor our server can read conversations on its own.
 */
class DriveBackupManager(
    private val context: Context,
    private val dao: MailDao,
    private val api: BottlepostApi,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun requestAccess(): DriveAccess {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE))).build()
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        val pending = result.pendingIntent
        return if (result.hasResolution() && pending != null) DriveAccess.NeedsConsent(pending)
        else DriveAccess.Granted(requireNotNull(result.accessToken) { "No Drive access token" })
    }

    /** Token after the consent screen returns; pass the activity result data. */
    fun tokenFromConsent(data: android.content.Intent?): String? {
        if (data == null) return null
        return Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken
    }

    suspend fun backup(accessToken: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val payload = BackupPayload(createdAt = now, threads = dao.allThreads(), letters = dao.allLetters())
        val plain = gzip(json.encodeToString(BackupPayload.serializer(), payload).toByteArray())
        val encrypted = encrypt(key(), plain)
        val existing = findBackupFileId(accessToken)
        if (existing == null) createFile(accessToken, encrypted) else updateFile(accessToken, existing, encrypted)
        now
    }

    /** Returns the number of letters restored, or null if there is no backup in this Google account. */
    suspend fun restore(accessToken: String): Int? = withContext(Dispatchers.IO) {
        val fileId = findBackupFileId(accessToken) ?: return@withContext null
        val bytes = http("GET", "$FILES_URL/$fileId?alt=media", accessToken)
        val payload = json.decodeFromString(BackupPayload.serializer(), String(gunzip(decrypt(key(), bytes))))
        dao.replaceAll(payload.threads, payload.letters)
        payload.letters.size
    }

    private suspend fun key(): SecretKeySpec = SecretKeySpec(Base64.decode(api.getBackupKey(), Base64.NO_WRAP), "AES")

    private fun findBackupFileId(token: String): String? {
        val q = URLEncoder.encode("name = '$FILE_NAME'", "UTF-8")
        val body = String(http("GET", "$FILES_URL?spaces=appDataFolder&fields=files(id)&q=$q", token))
        return Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
    }

    private fun createFile(token: String, content: ByteArray) {
        val boundary = "bottlepost${System.nanoTime()}"
        val meta = """{"name":"$FILE_NAME","parents":["appDataFolder"]}"""
        val out = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n".toByteArray())
            write("--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
            write(content)
            write("\r\n--$boundary--".toByteArray())
        }
        http("POST", "$UPLOAD_URL?uploadType=multipart", token, out.toByteArray(), "multipart/related; boundary=$boundary")
    }

    private fun updateFile(token: String, fileId: String, content: ByteArray) {
        http("PATCH", "$UPLOAD_URL/$fileId?uploadType=media", token, content, "application/octet-stream")
    }

    private fun http(method: String, url: String, token: String, body: ByteArray? = null, type: String? = null): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            // HttpURLConnection has no PATCH; Drive accepts the override header.
            conn.requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", type)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.use { String(it.readBytes()) }.orEmpty()
                throw IllegalStateException("Drive $method failed: $code $err")
            }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val FILE_NAME = "bottlepost-backup.bin"
        private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val IV_BYTES = 12

        internal fun encrypt(key: SecretKeySpec, plain: ByteArray): ByteArray {
            val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
            return iv + cipher.doFinal(plain)
        }

        internal fun decrypt(key: SecretKeySpec, data: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, IV_BYTES))
            }
            return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
        }

        internal fun gzip(bytes: ByteArray): ByteArray =
            ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

        internal fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
    }
}
