import SwiftData
import XCTest
@testable import Bottlepost

@MainActor
final class AccountLinkTests: XCTestCase {
    private var container: ModelContainer!
    private var fake: FakeService!
    private var mail: MailStore!
    private var storage: MemoryBackupStorage!
    private var backup: BackupManager!
    private var defaults: UserDefaults!

    private let credential = AppleCredential(idToken: "id", authorizationCode: "code1", rawNonce: "n")
    private let adult = Profile(callable: ["gender": "F", "birthYear": 1990])!

    override func setUp() async throws {
        container = try ModelContainer(for: LetterThread.self, Letter.self,
                                       configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        fake = FakeService()
        mail = MailStore(context: container.mainContext, service: fake)
        storage = MemoryBackupStorage()
        defaults = UserDefaults(suiteName: "AccountLinkTests")!
        defaults.removePersistentDomain(forName: "AccountLinkTests")
        backup = BackupManager(mail: mail, service: fake, storage: storage, defaults: defaults)
    }

    private func session() -> Session {
        Session(service: fake, mail: mail, sync: nil, ageLock: MemoryUnderageLock(), backup: backup)
    }

    private func letter(_ id: String) -> IncomingDelivery {
        IncomingDelivery(id: id, data: ["threadId": "t1", "body": "hello \(id)", "sentAt": NSNumber(value: 10), "sig": "s"],
                         delete: {})
    }

    func testBackupRoundTripIsEncrypted() async throws {
        await mail.ingest([letter("d1"), letter("d2")])
        try await backup.backup()
        let sealed = try XCTUnwrap(storage.data)
        XCTAssertNil(String(data: sealed, encoding: .utf8)?.range(of: "hello"), "backup must not be plain text")
        mail.wipe()
        try await backup.restore()
        XCTAssertEqual(mail.letters("t1").map(\.body), ["hello d1", "hello d2"])
        XCTAssertEqual(mail.thread("t1")?.unread, 2)
        XCTAssertNotNil(backup.lastBackupAt)
    }

    func testWrongKeyIsRejected() throws {
        let sealed = try BackupCrypto.seal(Data("x".utf8), key: Data(repeating: 1, count: 32))
        XCTAssertThrowsError(try BackupCrypto.open(sealed, key: Data(repeating: 2, count: 32)))
    }

    func testRestoreWithoutBackup() async {
        do {
            try await backup.restore()
            XCTFail("expected backup_none")
        } catch {
            XCTAssertEqual(errorMessageKey(ApiError.from(error)), "ios_backup_none")
        }
    }

    func testBackupIfDueOncePerDay() async throws {
        let now = Date()
        await backup.backupIfDue(now: now)
        let first = try XCTUnwrap(storage.data)
        await mail.ingest([letter("d9")])
        await backup.backupIfDue(now: now.addingTimeInterval(60))
        XCTAssertEqual(storage.data, first, "no second backup within a day")
    }

    func testSettingsLinkRecordsAndBacksUp() async throws {
        fake.profile = adult
        let s = session()
        await s.start()
        let result = try await s.linkApple(credential)
        XCTAssertEqual(result, .linked)
        XCTAssertEqual(fake.calls, ["linkApple", "confirmAccountLink"])
        XCTAssertEqual(s.profile?.linked, true)
        XCTAssertNotNil(storage.data)
    }

    func testSwitchDeletesThisAccountThenRestoresTheOther() async throws {
        fake.profile = adult
        fake.linkOutcome = .belongsToAnother
        fake.otherAccountProfile = Profile(callable: ["gender": "M", "birthYear": 1985, "linked": true])
        // The other account's backup.
        await mail.ingest([letter("old")])
        try await backup.backup()
        mail.wipe()
        await mail.ingest([letter("current")])

        let s = session()
        await s.start()
        let outcome = try await s.linkApple(credential)
        XCTAssertEqual(outcome, .needsSwitchConfirm)
        try await s.switchToAppleAccount()
        XCTAssertEqual(fake.calls, ["linkApple", "deleteAccount", "switch"])
        XCTAssertEqual(s.profile?.gender, "M")
        XCTAssertEqual(s.state, .ready)
        XCTAssertEqual(mail.letters("t1").map(\.body), ["hello old"])
    }

    func testOnboardingRestoreDropsTheEmptyAnonymousUser() async throws {
        fake.linkOutcome = .belongsToAnother
        fake.otherAccountProfile = adult
        let s = session()
        await s.start()
        XCTAssertEqual(s.state, .needsOnboarding)
        let restored = try await s.restoreWithApple(credential)
        XCTAssertTrue(restored)
        XCTAssertEqual(fake.calls, ["linkApple", "deleteCurrentUser", "switch"])
        XCTAssertEqual(s.state, .ready)
    }

    func testOnboardingNewAppleIdIsRecordedAfterProfile() async throws {
        let s = session()
        await s.start()
        let restored = try await s.restoreWithApple(credential)
        XCTAssertFalse(restored)
        try await s.createProfile(NewProfile(gender: "F", birthYear: 1990, lang: "ko", country: "KR",
                                             receiveFrom: "ANY", nickname: ""))
        XCTAssertEqual(fake.calls, ["linkApple", "confirmAccountLink"])
        XCTAssertEqual(s.profile?.linked, true)
    }

    func testAppleAccountDeletionRevokesAndRemovesBackup() async throws {
        fake.profile = adult
        fake.appleLinked = true
        try await backup.backup()
        let s = session()
        await s.start()
        try await s.deleteAccount(appleReauth: credential)
        XCTAssertEqual(fake.calls, ["revoke:code1", "deleteAccount"])
        XCTAssertNil(storage.data)
        XCTAssertEqual(s.state, .needsOnboarding)
    }

    func testNonceIsHashedForApple() {
        XCTAssertEqual(AppleSignIn.sha256("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        XCTAssertEqual(AppleSignIn.randomNonce().count, 32)
        XCTAssertEqual(errorMessageKey(ApiError(code: "google_link_required")), "ios_link_required")
    }
}
