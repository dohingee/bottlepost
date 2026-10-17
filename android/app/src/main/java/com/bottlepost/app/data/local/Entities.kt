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
)

/** Both sides have written: shown under "Connections". */
val ThreadEntity.connected: Boolean get() = hasIncoming && hasOutgoing

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
