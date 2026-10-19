import FirebaseFunctions
import XCTest
@testable import Bottlepost

final class RulesTests: XCTestCase {
    private struct T: ThreadFlags {
        var startedByMe = false
        var hasIncoming = true
        var hasOutgoing = false
        var unread = 0
        var closed = false
        var partnerLeft = false
        var lastOutgoing = false
        var lastAt: Int64 = 0
    }

    func testTabs() {
        XCTAssertTrue(MailTab.inbox.contains(T()))
        XCTAssertTrue(MailTab.connections.contains(T(hasOutgoing: true)))
        XCTAssertFalse(MailTab.inbox.contains(T(hasOutgoing: true)))
        XCTAssertEqual(MailTab.inbox.badge([T(unread: 2), T(unread: 3), T(hasOutgoing: true, unread: 4)]), 5)
    }

    func testStatusOrder() {
        XCTAssertEqual(ThreadStatus(T(unread: 1, closed: true, partnerLeft: true)), .partnerLeft)
        XCTAssertEqual(ThreadStatus(T(unread: 1, closed: true)), .closed)
        XCTAssertEqual(ThreadStatus(T(unread: 1, lastOutgoing: true, lastAt: 10), now: 20), .waiting)
        XCTAssertEqual(ThreadStatus(T(unread: 1)), .newLetter)
        XCTAssertEqual(ThreadStatus(T()), .yourTurn)
    }

    func testSealText() {
        XCTAssertEqual(sealText(3), "3")
        XCTAssertEqual(sealText(12), "9+")
    }

    func testAgeIsConservative() {
        XCTAssertTrue(AgeGate.isAdult(birthYear: 2007, currentYear: 2026))
        XCTAssertFalse(AgeGate.isAdult(birthYear: 2008, currentYear: 2026))
        XCTAssertFalse(AgeGate.isPlausible(birthYear: 1899, currentYear: 2026))
        XCTAssertFalse(AgeGate.isPlausible(birthYear: 2027, currentYear: 2026))
        XCTAssertTrue(AgeGate.isPlausible(birthYear: 1990, currentYear: 2026))
    }

    func testServerCodesUnderUnavailable() {
        let paused = NSError(domain: FunctionsErrorDomain, code: FunctionsErrorCode.unavailable.rawValue,
                             userInfo: [NSLocalizedDescriptionKey: "no_recipient_available"])
        XCTAssertEqual(errorMessageKey(ApiError.from(paused)), "error_no_recipient")
    }

    func testTranslationPlan() {
        XCTAssertEqual(LetterTranslation.plan(source: "ko", target: "ko"), .sameLanguage)
        XCTAssertEqual(LetterTranslation.plan(source: "pt-BR", target: "pt"), .sameLanguage)
        XCTAssertEqual(LetterTranslation.plan(source: "ja", target: "ko"), .translate(from: "ja"))
        XCTAssertEqual(LetterTranslation.baseCode("zh-Hant"), "zh-Hant")
    }

    func testDetectionBeatsHintWhenSure() {
        let korean = "오늘은 바닷가에 다녀왔어요. 파도 소리가 정말 좋았고 하늘도 맑았어요."
        XCTAssertEqual(LetterTranslation.detectLanguage(korean, hint: "en"), "ko")
        XCTAssertEqual(LetterTranslation.detectLanguage("ok", hint: "vi"), "vi")
    }

    func testStableSealColour() {
        XCTAssertEqual(stableHash("abc"), stableHash("abc"))
        XCTAssertNotEqual(stableHash("abc"), stableHash("abd"))
    }
}
