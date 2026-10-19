import Foundation
import SwiftData

/** One relay document from deliveries/{id} (letters.ts newDelivery or a "left" notice). */
struct IncomingDelivery {
    let id: String
    let data: [String: Any]
    /** Removes the document from the relay once it is stored on this phone. */
    let delete: () async throws -> Void

    var kind: String { data["kind"] as? String ?? "" }
    var isLeft: Bool { kind == "left" }
}

/**
 * Conversations on this phone, the iOS twin of MailRepository.kt. Main-actor bound: every check
 * and write happens without suspending in between, so overlapping ingests (listener + push fetch)
 * cannot store a letter twice or count it twice.
 */
@MainActor
final class MailStore {
    let context: ModelContext
    private let service: BottlepostService?

    init(context: ModelContext, service: BottlepostService?) {
        self.context = context
        self.service = service
    }

    // MARK: Receiving

    func ingest(_ docs: [IncomingDelivery]) async {
        // Letters first: a "left" notice can arrive in the same batch as the letter that starts the
        // thread (sender wrote, then left, while this phone was offline).
        let ordered = docs.filter { !$0.isLeft } + docs.filter(\.isLeft)
        for doc in ordered {
            guard let threadId = doc.data["threadId"] as? String else { continue }
            if doc.isLeft {
                // The other person left: keep the history readable, close the thread.
                if let t = thread(threadId) {
                    t.closed = true
                    t.partnerLeft = true
                    save()
                }
                try? await doc.delete()
                continue
            }
            guard let body = doc.data["body"] as? String else { continue }
            if letter(doc.id) == nil {
                store(doc, threadId: threadId, body: body)
            }
            // Stored here (or already was): remove it from the relay.
            try? await doc.delete()
        }
    }

    private func store(_ doc: IncomingDelivery, threadId: String, body: String) {
        let sentAt = (doc.data["sentAt"] as? NSNumber)?.int64Value ?? nowMs()
        let lang = doc.data["lang"] as? String ?? ""
        context.insert(Letter(deliveryId: doc.id, threadId: threadId, outgoing: false, body: body, lang: lang,
                              sentAt: sentAt, sig: doc.data["sig"] as? String ?? ""))
        let t = thread(threadId) ?? {
            let t = LetterThread(threadId: threadId, startedByMe: false, createdAt: sentAt, lastAt: sentAt)
            context.insert(t)
            return t
        }()
        t.partnerNickname = doc.data["senderNickname"] as? String ?? ""
        t.partnerGender = doc.data["senderGender"] as? String ?? ""
        t.partnerLang = lang
        t.lastAt = max(t.lastAt, sentAt)
        t.lastPreview = String(body.prefix(previewChars))
        t.hasIncoming = true
        t.lastOutgoing = false
        t.unread += 1
        save()
    }

    // MARK: Writing

    /** Starts a new conversation. Returns its threadId. */
    func sendFirst(body: String, wantGender: String, sameLanguageOnly: Bool, myLang: String) async throws -> String {
        guard let service else { throw ApiError(code: "unknown") }
        let text = body.trimmingCharacters(in: .whitespacesAndNewlines)
        let sent = try await service.sendLetter(body: text, wantGender: wantGender, sameLanguageOnly: sameLanguageOnly)
        let t = thread(sent.threadId) ?? {
            let t = LetterThread(threadId: sent.threadId, startedByMe: true, createdAt: sent.sentAt, lastAt: sent.sentAt)
            context.insert(t)
            return t
        }()
        t.partnerGender = wantGender == "ANY" ? "" : wantGender
        t.lastAt = sent.sentAt
        t.lastPreview = String(text.prefix(previewChars))
        t.hasOutgoing = true
        t.lastOutgoing = true
        insertOutgoing(sent, body: text, lang: myLang)
        return sent.threadId
    }

    func reply(threadId: String, body: String, myLang: String) async throws {
        guard let service else { throw ApiError(code: "unknown") }
        let text = body.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            let sent = try await service.replyLetter(threadId: threadId, body: text)
            if let t = thread(threadId) {
                t.lastAt = sent.sentAt
                t.lastPreview = String(text.prefix(previewChars))
                t.hasOutgoing = true
                t.lastOutgoing = true
            }
            insertOutgoing(sent, body: text, lang: myLang)
        } catch {
            // The server no longer accepts replies here: show it as ended.
            if ApiError.from(error).code == "thread_closed" { markClosed(threadId) }
            throw error
        }
    }

    private func insertOutgoing(_ sent: SentLetter, body: String, lang: String) {
        if letter(sent.deliveryId) == nil {
            context.insert(Letter(deliveryId: sent.deliveryId, threadId: sent.threadId, outgoing: true, body: body,
                                  lang: lang, sentAt: sent.sentAt, sig: sent.sig))
        }
        save()
    }

    /** Blocks on the server (the other side is not told) and closes the conversation here. */
    func block(threadId: String) async throws {
        try await service?.blockThread(threadId)
        markClosed(threadId)
    }

    /**
     * Uploads the most recent letters of the conversation, unchanged so their signatures verify
     * (the user agreed to this in the report sheet). The server also blocks the other person.
     */
    func report(threadId: String, reason: String, note: String) async throws {
        guard let service else { throw ApiError(code: "unknown") }
        let recent = letters(threadId).suffix(reportLetters).map {
            ReportMessage(deliveryId: $0.deliveryId, body: $0.body, sentAt: $0.sentAt, sig: $0.sig,
                          direction: $0.outgoing ? "sent" : "received")
        }
        try await service.submitReport(threadId: threadId, reason: reason, note: note, messages: Array(recent))
        markClosed(threadId)
    }

    /** Leaves on the server (the other side is told). The letters stay readable here until deleted. */
    func leave(threadId: String) async throws {
        try await service?.leaveThread(threadId)
        markClosed(threadId)
    }

    /**
     * Removes a conversation from this phone. If the other person is still in it, this also leaves
     * on the server so they are not left writing into the void.
     */
    func delete(threadId: String) async throws {
        if let t = thread(threadId), !t.closed { try await service?.leaveThread(threadId) }
        for l in letters(threadId) { context.delete(l) }
        if let t = thread(threadId) { context.delete(t) }
        save()
    }

    func markRead(threadId: String) {
        guard let t = thread(threadId), t.unread != 0 else { return }
        t.unread = 0
        save()
    }

    func markClosed(_ threadId: String) {
        guard let t = thread(threadId) else { return }
        t.closed = true
        save()
    }

    /** Account gone (deleted elsewhere): nothing of it may stay on the phone. */
    func wipe() {
        try? context.delete(model: Letter.self)
        try? context.delete(model: LetterThread.self)
        save()
    }

    // MARK: Queries

    func thread(_ id: String) -> LetterThread? {
        try? context.fetch(FetchDescriptor<LetterThread>(predicate: #Predicate { $0.threadId == id })).first
    }

    func letter(_ id: String) -> Letter? {
        try? context.fetch(FetchDescriptor<Letter>(predicate: #Predicate { $0.deliveryId == id })).first
    }

    func letters(_ threadId: String) -> [Letter] {
        let d = FetchDescriptor<Letter>(predicate: #Predicate { $0.threadId == threadId }, sortBy: [SortDescriptor(\.sentAt)])
        return (try? context.fetch(d)) ?? []
    }

    var isEmpty: Bool { ((try? context.fetchCount(FetchDescriptor<LetterThread>())) ?? 0) == 0 }

    private func save() {
        try? context.save()
    }
}
