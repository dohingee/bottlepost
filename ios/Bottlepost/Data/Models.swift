import Foundation
import SwiftData

/**
 * Local conversation store, the iOS twin of the Room entities in android/.../data/local/Entities.kt.
 * Letter text lives only here (and in the user's own encrypted backup later), never on our server.
 */

/** Undelivered letters expire after 7 days; then either side may write again (replyLetter in letters.ts). */
let turnTimeoutMs: Int64 = 7 * 24 * 60 * 60 * 1000
let previewChars = 80
/** Must not exceed LIMITS.reportMaxMessages on the server. */
let reportLetters = 6

func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

@Model
final class LetterThread {
    @Attribute(.unique) var threadId: String
    var startedByMe: Bool
    var partnerNickname: String
    /** "M", "F" or "" (unknown until the first reply on threads I started with "Anyone"). */
    var partnerGender: String
    var partnerLang: String
    var createdAt: Int64
    var lastAt: Int64
    var lastPreview: String
    var hasIncoming: Bool
    var hasOutgoing: Bool
    var unread: Int
    var closed: Bool
    var partnerLeft: Bool
    var lastOutgoing: Bool

    init(threadId: String, startedByMe: Bool, partnerNickname: String = "", partnerGender: String = "",
         partnerLang: String = "", createdAt: Int64, lastAt: Int64, lastPreview: String = "",
         hasIncoming: Bool = false, hasOutgoing: Bool = false, unread: Int = 0, closed: Bool = false,
         partnerLeft: Bool = false, lastOutgoing: Bool = false) {
        self.threadId = threadId
        self.startedByMe = startedByMe
        self.partnerNickname = partnerNickname
        self.partnerGender = partnerGender
        self.partnerLang = partnerLang
        self.createdAt = createdAt
        self.lastAt = lastAt
        self.lastPreview = lastPreview
        self.hasIncoming = hasIncoming
        self.hasOutgoing = hasOutgoing
        self.unread = unread
        self.closed = closed
        self.partnerLeft = partnerLeft
        self.lastOutgoing = lastOutgoing
    }
}

@Model
final class Letter {
    /** The relay document id; part of the signed payload, so it must be kept as is. */
    @Attribute(.unique) var deliveryId: String
    var threadId: String
    var outgoing: Bool
    /** Exactly the (trimmed) text the server signed. */
    var body: String
    var lang: String
    /** Epoch milliseconds, exactly as signed. */
    var sentAt: Int64
    /** Server HMAC; sent back unchanged with reports (M4). */
    var sig: String

    init(deliveryId: String, threadId: String, outgoing: Bool, body: String, lang: String, sentAt: Int64, sig: String) {
        self.deliveryId = deliveryId
        self.threadId = threadId
        self.outgoing = outgoing
        self.body = body
        self.lang = lang
        self.sentAt = sentAt
        self.sig = sig
    }
}

extension LetterThread: ThreadFlags {}
