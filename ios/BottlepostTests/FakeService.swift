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
}
