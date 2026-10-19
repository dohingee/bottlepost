import SwiftData
import XCTest
import FirebaseFunctions
@testable import Bottlepost

final class ProfileTests: XCTestCase {
    func testDecodesServerView() throws {
        let data: [String: Any] = [
            "gender": "F", "birthYear": NSNumber(value: 1995), "lang": "ko", "country": "KR",
            "receiveFrom": "ANY", "nickname": "바다", "tags": ["travel"], "linked": true,
            "status": "suspended", "suspendedUntil": NSNumber(value: 1_900_000_000_000.0),
        ]
        let p = try XCTUnwrap(Profile(callable: data))
        XCTAssertEqual(p.gender, "F")
        XCTAssertEqual(p.birthYear, 1995)
        XCTAssertEqual(p.tags, ["travel"])
        XCTAssertTrue(p.linked)
        XCTAssertEqual(p.suspendedUntil, Date(timeIntervalSince1970: 1_900_000_000))
        XCTAssertTrue(p.isSuspended(now: Date(timeIntervalSince1970: 1_800_000_000)))
        XCTAssertFalse(p.isSuspended(now: Date(timeIntervalSince1970: 2_000_000_000)))
    }

    func testNullMeansOnboarding() {
        XCTAssertNil(Profile(callable: NSNull()))
        XCTAssertNil(Profile(callable: nil))
    }

    func testSuspendedWithoutEndIsUnderReview() throws {
        let p = try XCTUnwrap(Profile(callable: ["status": "suspended", "suspendedUntil": NSNull()]))
        XCTAssertNil(p.suspendedUntil)
        XCTAssertTrue(p.isSuspended())
    }

    func testServerErrorCodes() {
        let denied = NSError(domain: FunctionsErrorDomain, code: FunctionsErrorCode.permissionDenied.rawValue,
                             userInfo: [NSLocalizedDescriptionKey: "account_banned"])
        XCTAssertEqual(ApiError.from(denied).code, "account_banned")
        XCTAssertEqual(errorMessageKey(ApiError.from(denied)), "error_banned")

        let outage = NSError(domain: FunctionsErrorDomain, code: FunctionsErrorCode.unavailable.rawValue)
        XCTAssertEqual(ApiError.from(outage).code, "network")
        XCTAssertEqual(ApiError.from(URLError(.notConnectedToInternet)).code, "network")
        XCTAssertEqual(errorMessageKey(ApiError(code: "something_new")), "error_generic")
    }

    func testEveryMappedKeyIsLocalized() {
        let codes = ["daily_limit", "contact_info_not_allowed", "wait_for_reply", "profile_required", "network", "x"]
        for code in codes {
            let key = errorMessageKey(ApiError(code: code))
            XCTAssertNotEqual(L(key), key, "missing string \(key)")
        }
    }

    func testOwnGenderUsesSingularLabels() {
        XCTAssertEqual(selfGenderLabel("M"), L("gender_self_male"))
        XCTAssertEqual(selfGenderLabel("F"), L("gender_self_female"))
        XCTAssertNotEqual(L("gender_self_male"), "gender_self_male", "missing string gender_self_male")
    }
}

@MainActor
final class SessionTests: XCTestCase {
    private func session(_ fake: FakeService, lock: UnderageLock = MemoryUnderageLock()) -> Session {
        Session(service: fake, mail: nil, sync: nil, ageLock: lock)
    }

    func testNoProfileGoesToOnboarding() async {
        let s = session(FakeService())
        await s.start()
        XCTAssertEqual(s.state, .needsOnboarding)
    }

    func testProfileLoads() async throws {
        let profile = try XCTUnwrap(Profile(callable: ["gender": "M", "birthYear": 1990]))
        let s = session(FakeService(profile: profile))
        await s.start()
        XCTAssertEqual(s.state, .ready)
        XCTAssertEqual(s.profile, profile)
    }

    func testFailureIsShown() async {
        let s = session(FakeService(failure: ApiError(code: "network")))
        await s.start()
        XCTAssertEqual(s.state, .failed(ApiError(code: "network")))
    }

    func testLockedPhoneStaysBlocked() async {
        let lock = MemoryUnderageLock()
        lock.lock()
        let s = session(FakeService(), lock: lock)
        await s.start()
        XCTAssertEqual(s.state, .underage)
    }

    func testServerUnderageLocksThePhone() async {
        let lock = MemoryUnderageLock()
        let s = session(FakeService(createFailure: ApiError(code: "underage")), lock: lock)
        await s.start()
        let p = NewProfile(gender: "F", birthYear: 2000, lang: "ko", country: "KR", receiveFrom: "ANY", nickname: "")
        await XCTAssertThrowsErrorAsync(try await s.createProfile(p))
        XCTAssertTrue(lock.isLocked)
        XCTAssertEqual(s.state, .underage)
    }
}

@MainActor
final class AccountTests: XCTestCase {
    func testDeleteAccountWipesSignsOutAndRestarts() async throws {
        let container = try ModelContainer(for: LetterThread.self, Letter.self,
                                           configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        let fake = FakeService(profile: Profile(callable: ["gender": "F", "birthYear": 1990]))
        let mail = MailStore(context: container.mainContext, service: fake)
        await mail.ingest([IncomingDelivery(id: "d1", data: ["threadId": "t1", "body": "hi there", "sentAt": NSNumber(value: 1)],
                                            delete: {})])
        let lock = MemoryUnderageLock()
        let s = Session(service: fake, mail: mail, sync: nil, ageLock: lock)
        await s.start()
        try await s.deleteAccount()
        XCTAssertTrue(fake.deletedAccount)
        XCTAssertTrue(fake.signedOut)
        XCTAssertTrue(mail.isEmpty)
        XCTAssertNil(s.profile)
        XCTAssertEqual(s.state, .needsOnboarding)
    }

    func testFailedDeleteKeepsEverything() async throws {
        let fake = FakeService(profile: Profile(callable: ["gender": "F", "birthYear": 1990]))
        fake.deleteFailure = ApiError(code: "network")
        let s = Session(service: fake, mail: nil, sync: nil, ageLock: MemoryUnderageLock())
        await s.start()
        await XCTAssertThrowsErrorAsync(try await s.deleteAccount())
        XCTAssertFalse(fake.signedOut)
        XCTAssertEqual(s.state, .ready)
    }

    func testAppealErrorsReachTheScreen() async throws {
        let fake = FakeService(profile: Profile(callable: ["gender": "F", "birthYear": 1990, "status": "suspended"]))
        fake.appealFailure = ApiError(code: "appeal_pending")
        let s = Session(service: fake, mail: nil, sync: nil, ageLock: MemoryUnderageLock())
        await s.start()
        do {
            try await s.submitAppeal("please review this")
            XCTFail("expected appeal_pending")
        } catch {
            XCTAssertEqual(errorMessageKey(ApiError.from(error)), "error_appeal_pending")
        }
        XCTAssertEqual(errorMessageKey(ApiError(code: "report_limit")), "error_report_limit")
    }
}

func XCTAssertThrowsErrorAsync(_ expression: @autoclosure () async throws -> Void, file: StaticString = #filePath, line: UInt = #line) async {
    do {
        try await expression()
        XCTFail("expected an error", file: file, line: line)
    } catch {}
}
