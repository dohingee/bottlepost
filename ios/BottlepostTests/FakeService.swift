import Foundation
@testable import Bottlepost

/** In-memory stand-in for the callables; records what was sent. */
final class FakeService: BottlepostService, @unchecked Sendable {
    var profile: Profile?
    var failure: ApiError?
    var createFailure: ApiError?
    var replyFailure: ApiError?
    var uid: String? = "me"
    private(set) var left: [String] = []
    private(set) var sentBodies: [String] = []
    private(set) var blocked: [String] = []
    private(set) var reports: [(threadId: String, reason: String, note: String, messages: [ReportMessage])] = []
    private(set) var appeals: [String] = []
    private(set) var deletedAccount = false
    private(set) var signedOut = false
    var appealFailure: ApiError?
    /** Ordered log of account calls, to check flows. */
    private(set) var calls: [String] = []
    var linkOutcome: LinkOutcome = .linked
    var appleLinked = false
    /** Profile of the account the Apple ID belongs to (after a switch). */
    var otherAccountProfile: Profile?
    var backupKey = Data(repeating: 7, count: 32)
    var deleteFailure: ApiError?
    private var counter = 0

    init(profile: Profile? = nil, failure: ApiError? = nil, createFailure: ApiError? = nil) {
        self.profile = profile
        self.failure = failure
        self.createFailure = createFailure
    }

    func signInIfNeeded() async throws { if let failure { throw failure } }
    func getProfile() async throws -> Profile? { profile }

    func createProfile(_ p: NewProfile) async throws -> Profile {
        if let createFailure { throw createFailure }
        let made = Profile(callable: ["gender": p.gender, "birthYear": p.birthYear, "lang": p.lang])!
        profile = made
        return made
    }

    func updateProfile(_ u: ProfileUpdate) async throws -> Profile { profile! }
    func registerPushToken(_ token: String) async throws {}

    func sendLetter(body: String, wantGender: String, sameLanguageOnly: Bool) async throws -> SentLetter {
        sentBodies.append(body)
        counter += 1
        return SentLetter(threadId: "t\(counter)", deliveryId: "d\(counter)", sentAt: 1_000 + Int64(counter), sig: "sig\(counter)")
    }

    func replyLetter(threadId: String, body: String) async throws -> SentLetter {
        if let replyFailure { throw replyFailure }
        sentBodies.append(body)
        counter += 1
        return SentLetter(threadId: threadId, deliveryId: "r\(counter)", sentAt: 2_000 + Int64(counter), sig: "rsig\(counter)")
    }

    func leaveThread(_ threadId: String) async throws { left.append(threadId) }
    func blockThread(_ threadId: String) async throws { blocked.append(threadId) }

    func submitReport(threadId: String, reason: String, note: String, messages: [ReportMessage]) async throws {
        reports.append((threadId, reason, note, messages))
    }

    func submitAppeal(_ message: String) async throws {
        if let appealFailure { throw appealFailure }
        appeals.append(message)
    }

    func deleteAccount() async throws {
        if let deleteFailure { throw deleteFailure }
        calls.append("deleteAccount")
        deletedAccount = true
    }

    func signOut() { signedOut = true }

    var isAppleLinked: Bool { appleLinked }

    func linkApple(_ credential: AppleCredential) async throws -> LinkOutcome {
        calls.append("linkApple")
        if linkOutcome == .linked { appleLinked = true }
        return linkOutcome
    }

    func switchToLinkedApple() async throws {
        calls.append("switch")
        appleLinked = true
        profile = otherAccountProfile
    }

    func deleteCurrentUser() async throws { calls.append("deleteCurrentUser") }

    func confirmAccountLink() async throws -> Profile {
        calls.append("confirmAccountLink")
        return Profile(callable: ["gender": profile?.gender ?? "F", "birthYear": profile?.birthYear ?? 1990, "linked": true])!
    }

    func getBackupKey() async throws -> Data { backupKey }

    func revokeApple(authorizationCode: String) async throws { calls.append("revoke:\(authorizationCode)") }
}

/** Backup file kept in memory. */
final class MemoryBackupStorage: BackupStorage, @unchecked Sendable {
    var data: Data?
    func write(_ data: Data) async throws { self.data = data }
    func read() async throws -> Data? { data }
    func remove() async throws { data = nil }
}
