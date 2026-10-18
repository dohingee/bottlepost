package com.bottlepost.app.data

import android.util.Log
import com.bottlepost.app.data.local.LetterEntity
import com.bottlepost.app.data.local.MailDao
import com.bottlepost.app.data.local.ThreadEntity
import com.bottlepost.app.data.remote.BottlepostApi
import com.bottlepost.app.data.remote.OutgoingReportMessage
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Letters live only on this device. The server relays each letter through `deliveries/{id}`;
 * this repository copies it into Room and immediately deletes the server copy.
 */
class MailRepository(
    private val dao: MailDao,
    private val api: BottlepostApi,
    private val firestore: FirebaseFirestore,
    private val scope: CoroutineScope,
) {
    private var listener: ListenerRegistration? = null
    private val ingestLock = Mutex()

    fun observeThreads(): Flow<List<ThreadEntity>> = dao.observeThreads()
    fun observeThread(threadId: String): Flow<ThreadEntity?> = dao.observeThread(threadId)
    fun observeLetters(threadId: String): Flow<List<LetterEntity>> = dao.observeLetters(threadId)

    /** Live pickup while the app is open. */
    fun startSync(uid: String) {
        listener?.remove()
        listener = deliveriesFor(uid).addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(TAG, "delivery listener failed", err)
                return@addSnapshotListener
            }
            val docs = snap?.documents.orEmpty()
            if (docs.isNotEmpty()) scope.launch { ingest(docs) }
        }
    }

    fun stopSync() {
        listener?.remove()
        listener = null
    }

    /** One-shot pickup, e.g. from a push message while the app is in the background. */
    suspend fun syncOnce(uid: String): Int {
        val docs = deliveriesFor(uid).get().await().documents
        ingest(docs)
        return docs.size
    }

    private fun deliveriesFor(uid: String) = firestore.collection("deliveries").whereEqualTo("to", uid)

    private suspend fun ingest(docs: List<DocumentSnapshot>) = ingestLock.withLock {
        // Letters first: a "left" notice can arrive in the same batch as the letter that starts the
        // thread (sender wrote, then left, while this phone was offline).
        for (doc in docs.sortedBy { it.getString("kind") == "left" }) {
            val threadId = doc.getString("threadId") ?: continue
            if (doc.getString("kind") == "left") {
                // The other person left: keep the history readable, close the thread.
                if (dao.thread(threadId) != null) dao.markPartnerLeft(threadId)
                runCatching { doc.reference.delete().await() }
                continue
            }
            val body = doc.getString("body") ?: continue
            val sentAt = doc.getLong("sentAt") ?: System.currentTimeMillis()
            val letter = LetterEntity(
                deliveryId = doc.id,
                threadId = threadId,
                outgoing = false,
                body = body,
                lang = doc.getString("lang") ?: "",
                sentAt = sentAt,
                sig = doc.getString("sig") ?: "",
            )
            if (dao.insertLetter(letter) != -1L) {
                val existing = dao.thread(threadId)
                dao.upsertThread(
                    (existing ?: ThreadEntity(
                        threadId = threadId,
                        startedByMe = false,
                        partnerNickname = "",
                        partnerGender = "",
                        partnerLang = "",
                        createdAt = sentAt,
                        lastAt = sentAt,
                        lastPreview = "",
                        hasIncoming = false,
                        hasOutgoing = false,
                        unread = 0,
                    )).copy(
                        partnerNickname = doc.getString("senderNickname").orEmpty(),
                        partnerGender = doc.getString("senderGender").orEmpty(),
                        partnerLang = letter.lang,
                        lastAt = maxOf(existing?.lastAt ?: 0, sentAt),
                        lastPreview = body.take(PREVIEW_CHARS),
                        hasIncoming = true,
                        lastOutgoing = false,
                        unread = (existing?.unread ?: 0) + 1,
                    ),
                )
            }
            // Stored locally (or already was): remove it from the relay.
            runCatching { doc.reference.delete().await() }
                .onFailure { Log.w(TAG, "could not delete delivery ${doc.id}", it) }
        }
    }

    suspend fun sendFirst(body: String, wantGender: String, sameLanguageOnly: Boolean, myLang: String): String {
        val sent = api.sendLetter(body, wantGender, sameLanguageOnly)
        dao.upsertThread(
            ThreadEntity(
                threadId = sent.threadId,
                startedByMe = true,
                partnerNickname = "",
                partnerGender = if (wantGender == "ANY") "" else wantGender,
                partnerLang = "",
                createdAt = sent.sentAt,
                lastAt = sent.sentAt,
                lastPreview = body.take(PREVIEW_CHARS),
                hasIncoming = false,
                hasOutgoing = true,
                unread = 0,
                lastOutgoing = true,
            ),
        )
        dao.insertLetter(LetterEntity(sent.deliveryId, sent.threadId, true, body, myLang, sent.sentAt, sent.sig))
        return sent.threadId
    }

    suspend fun reply(threadId: String, body: String, myLang: String) {
        val sent = api.replyLetter(threadId, body)
        dao.insertLetter(LetterEntity(sent.deliveryId, threadId, true, body, myLang, sent.sentAt, sent.sig))
        dao.thread(threadId)?.let {
            dao.upsertThread(it.copy(lastAt = sent.sentAt, lastPreview = body.take(PREVIEW_CHARS), hasOutgoing = true, lastOutgoing = true))
        }
    }

    suspend fun markRead(threadId: String) = dao.markRead(threadId)

    suspend fun block(threadId: String) {
        api.blockThread(threadId)
        dao.close(threadId)
    }

    /** Uploads the most recent letters of the thread (with consent shown in the UI). Also blocks. */
    suspend fun report(threadId: String, reason: String, note: String) {
        val recent = dao.recentLetters(threadId, REPORT_LETTERS).reversed()
        api.submitReport(
            threadId,
            reason,
            note,
            recent.map { OutgoingReportMessage(it.deliveryId, it.body, it.sentAt, it.sig, it.outgoing) },
        )
        dao.close(threadId)
    }

    /** Leaves on the server (the other side is told). The letters stay readable here until deleted. */
    suspend fun leave(threadId: String) {
        api.leaveThread(threadId)
        dao.close(threadId)
    }

    /**
     * Removes a conversation from this phone. If the other person is still in it, this also leaves
     * on the server so they are not left writing into the void.
     */
    suspend fun delete(thread: ThreadEntity) {
        if (!thread.closed) api.leaveThread(thread.threadId)
        dao.deleteThread(thread.threadId)
    }

    /** Called when the server says the thread no longer accepts replies. */
    suspend fun markClosed(threadId: String) = dao.close(threadId)

    companion object {
        private const val TAG = "MailRepository"
        private const val PREVIEW_CHARS = 80
        /** Must not exceed LIMITS.reportMaxMessages on the server. */
        const val REPORT_LETTERS = 6
    }
}
