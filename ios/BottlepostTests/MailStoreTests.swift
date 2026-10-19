import SwiftData
import XCTest
@testable import Bottlepost

@MainActor
final class MailStoreTests: XCTestCase {
    private var container: ModelContainer!
    private var service: FakeService!
    private var store: MailStore!
    private var log = DeleteLog()
    private var deleted: [String] { log.ids }

    override func setUp() async throws {
        container = try ModelContainer(for: LetterThread.self, Letter.self,
                                       configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        service = FakeService()
        store = MailStore(context: container.mainContext, service: service)
        log = DeleteLog()
    }

    private func letterDoc(_ id: String, thread: String = "t1", body: String = "hello there", sentAt: Int64 = 100,
                           nickname: String = "Sea", gender: String = "F") -> IncomingDelivery {
        IncomingDelivery(id: id, data: ["to": "me", "threadId": thread, "kind": "first", "body": body, "lang": "ko",
                                        "senderNickname": nickname, "senderGender": gender,
                                        "sentAt": NSNumber(value: sentAt), "sig": "s-\(id)"],
                         delete: { [log] in log.append(id) })
    }

    private func leftDoc(_ id: String, thread: String = "t1") -> IncomingDelivery {
        IncomingDelivery(id: id, data: ["to": "me", "threadId": thread, "kind": "left", "sentAt": NSNumber(value: 500)],
                         delete: { [log] in log.append(id) })
    }

    func testStoresLetterAndRemovesItFromRelay() async throws {
        await store.ingest([letterDoc("d1")])
        let t = try XCTUnwrap(store.thread("t1"))
        XCTAssertFalse(t.startedByMe)
        XCTAssertEqual(t.unread, 1)
        XCTAssertEqual(t.partnerNickname, "Sea")
        XCTAssertTrue(t.hasIncoming)
        let l = try XCTUnwrap(store.letter("d1"))
        XCTAssertEqual(l.sig, "s-d1")
        XCTAssertEqual(l.sentAt, 100)
        XCTAssertEqual(deleted, ["d1"])
    }

    func testDuplicateIsIgnoredButStillDeleted() async throws {
        await store.ingest([letterDoc("d1")])
        await store.ingest([letterDoc("d1")])
        XCTAssertEqual(store.letters("t1").count, 1)
        XCTAssertEqual(store.thread("t1")?.unread, 1)
        XCTAssertEqual(deleted, ["d1", "d1"])
    }

    func testLeftNoticeAfterLetterInSameBatch() async throws {
        // Left notice listed first: letters must still be stored before it is applied.
        await store.ingest([leftDoc("n1"), letterDoc("d1")])
        let t = try XCTUnwrap(store.thread("t1"))
        XCTAssertTrue(t.partnerLeft)
        XCTAssertTrue(t.closed)
        XCTAssertEqual(store.letters("t1").count, 1)
    }

    func testLateLetterDoesNotReopenClosedThread() async throws {
        await store.ingest([letterDoc("d1"), leftDoc("n1")])
        await store.ingest([letterDoc("d2", body: "late one", sentAt: 300, nickname: "New")])
        let t = try XCTUnwrap(store.thread("t1"))
        XCTAssertTrue(t.closed)
        XCTAssertEqual(t.partnerNickname, "New")
        XCTAssertEqual(t.unread, 2)
    }

    func testSendFirstKeepsSignedTrimmedText() async throws {
        let id = try await store.sendFirst(body: "  hi stranger \n", wantGender: "F", sameLanguageOnly: false, myLang: "ko")
        XCTAssertEqual(service.sentBodies, ["hi stranger"])
        let t = try XCTUnwrap(store.thread(id))
        XCTAssertTrue(t.startedByMe)
        XCTAssertTrue(t.lastOutgoing)
        XCTAssertEqual(t.partnerGender, "F")
        let l = try XCTUnwrap(store.letters(id).first)
        XCTAssertEqual(l.body, "hi stranger")
        XCTAssertEqual(l.sig, "sig1")
        XCTAssertTrue(l.outgoing)
        // Not in a tab until someone replies.
        XCTAssertFalse(MailTab.inbox.contains(t))
        XCTAssertFalse(MailTab.connections.contains(t))
    }

    func testReplyMakesConnectionAndWaits() async throws {
        await store.ingest([letterDoc("d1")])
        try await store.reply(threadId: "t1", body: "hello back", myLang: "en")
        let t = try XCTUnwrap(store.thread("t1"))
        XCTAssertTrue(MailTab.connections.contains(t))
        XCTAssertEqual(ThreadStatus(t, now: t.lastAt + 1), .waiting)
        // After 7 days without an answer either side may write again; their letter is still unread.
        XCTAssertEqual(ThreadStatus(t, now: t.lastAt + turnTimeoutMs), .newLetter)
        store.markRead(threadId: "t1")
        XCTAssertEqual(ThreadStatus(t, now: t.lastAt + turnTimeoutMs), .yourTurn)
    }

    func testClosedReplyMarksThreadClosed() async throws {
        await store.ingest([letterDoc("d1")])
        service.replyFailure = ApiError(code: "thread_closed")
        await XCTAssertThrowsErrorAsync(try await store.reply(threadId: "t1", body: "hello", myLang: "en"))
        XCTAssertEqual(store.thread("t1")?.closed, true)
    }

    func testDeleteLeavesOpenThreadFirst() async throws {
        await store.ingest([letterDoc("d1"), letterDoc("d2", thread: "t2")])
        store.markClosed("t2")
        try await store.delete(threadId: "t1")
        try await store.delete(threadId: "t2")
        XCTAssertEqual(service.left, ["t1"])
        XCTAssertNil(store.thread("t1"))
        XCTAssertTrue(store.letters("t1").isEmpty)
    }

    func testMarkReadAndWipe() async throws {
        await store.ingest([letterDoc("d1")])
        store.markRead(threadId: "t1")
        XCTAssertEqual(store.thread("t1")?.unread, 0)
        store.wipe()
        XCTAssertTrue(store.isEmpty)
    }
}

/** Records relay deletions; plain class so the non-isolated delete closures can write to it. */
final class DeleteLog: @unchecked Sendable {
    private(set) var ids: [String] = []
    func append(_ id: String) { ids.append(id) }
}
