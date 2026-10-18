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
}

@MainActor
final class SessionTests: XCTestCase {
    private struct FakeService: BottlepostService {
        var profile: Profile?
        var failure: ApiError?
        func signInIfNeeded() async throws { if let failure { throw failure } }
        func getProfile() async throws -> Profile? { profile }
    }

    func testNoProfileGoesToOnboarding() async {
        let session = Session(service: FakeService())
        await session.start()
        XCTAssertEqual(session.state, .needsOnboarding)
    }

    func testProfileLoads() async throws {
        let profile = try XCTUnwrap(Profile(callable: ["gender": "M", "birthYear": 1990]))
        let session = Session(service: FakeService(profile: profile))
        await session.start()
        XCTAssertEqual(session.state, .ready(profile))
    }

    func testFailureIsShown() async {
        let session = Session(service: FakeService(failure: ApiError(code: "network")))
        await session.start()
        XCTAssertEqual(session.state, .failed(ApiError(code: "network")))
    }
}
