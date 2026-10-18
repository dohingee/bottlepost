package com.bottlepost.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A conversation with one stranger. The server only knows the [threadId]; the other person's
 * identity is never sent to this device.
 */
@Serializable
@Entity(tableName = "threads")
data class ThreadEntity(
    @PrimaryKey val threadId: String,
    val startedByMe: Boolean,
    val partnerNickname: String,
    /** "M" or "F"; empty for threads I started until the first reply arrives. */
    val partnerGender: String,
    val partnerLang: String,
    val createdAt: Long,
    val lastAt: Long,
    val lastPreview: String,
    val hasIncoming: Boolean,
    val hasOutgoing: Boolean,
    val unread: Int,
    val closed: Boolean = false,
    /** The other person left; the conversation stays readable until this user deletes it. */
    val partnerLeft: Boolean = false,
    /** The newest letter is mine, so it is the other person's turn. */
    val lastOutgoing: Boolean = false,
)

/** Both sides have written: shown under "Connections". */
val ThreadEntity.connected: Boolean get() = hasIncoming && hasOutgoing

/**
 * Letters alternate: after writing, wait for the other side. After [TURN_TIMEOUT_MS] without a reply
 * (the server's delivery TTL: the letter may have expired undelivered) either side may write again.
 * Same rule as replyLetter in firebase/functions/src/letters.ts.
 */
fun List<LetterEntity>.awaitingReply(now: Long = System.currentTimeMillis()): Boolean =
    lastOrNull()?.let { it.outgoing && now - it.sentAt < TURN_TIMEOUT_MS } == true

/** List view of the same rule, from the thread row alone. */
fun ThreadEntity.awaitingReply(now: Long = System.currentTimeMillis()): Boolean =
    lastOutgoing && now - lastAt < TURN_TIMEOUT_MS

/** Must match LIMITS.deliveryTtlDays in firebase/functions/src/config.ts. */
const val TURN_TIMEOUT_MS = 7L * 24 * 60 * 60 * 1000

/** One letter. [sig] is the server signature that lets a report prove the text is unaltered. */
@Serializable
@Entity(tableName = "letters", indices = [Index("threadId")])
data class LetterEntity(
    @PrimaryKey val deliveryId: String,
    val threadId: String,
    val outgoing: Boolean,
    val body: String,
    val lang: String,
    val sentAt: Long,
    val sig: String,
)
