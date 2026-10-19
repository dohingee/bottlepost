import Foundation
import FirebaseAuth
import FirebaseFunctions

/** Server error codes (HttpsError messages in firebase/functions) mapped to strings in UI/Errors.swift. */
struct ApiError: Error, Equatable {
    let code: String

    static func from(_ error: Error) -> ApiError {
        if let api = error as? ApiError { return api }
        let ns = error as NSError
        switch ns.domain {
        case FunctionsErrorDomain:
            // Our functions throw lower_snake_case messages, also with "unavailable" (service_paused,
            // no_recipient_available). Anything else under these two codes is a timeout or outage;
            // plain offline errors arrive as URL errors (default branch).
            let message = ns.localizedDescription
            if message.range(of: "^[a-z_]+$", options: .regularExpression) != nil { return ApiError(code: message) }
            switch FunctionsErrorCode(rawValue: ns.code) {
            case .unavailable, .deadlineExceeded: return ApiError(code: "network")
            default: return ApiError(code: message)
            }
        case AuthErrorDomain where ns.code == AuthErrorCode.networkError.rawValue:
            return ApiError(code: "network")
        default:
            return ApiError(code: "network")
        }
    }
}

/** createProfile input (profile.ts). Gender and birth year cannot be changed later. */
struct NewProfile: Equatable, Sendable {
    var gender: String
    var birthYear: Int
    var lang: String
    var country: String
    var receiveFrom: String
    var nickname: String
}

/** updateProfile input: only the fields that are set are sent. */
struct ProfileUpdate: Equatable, Sendable {
    var nickname: String?
    var lang: String?
    var receiveFrom: String?

    var payload: [String: Any] {
        var d: [String: Any] = [:]
        if let nickname { d["nickname"] = nickname }
        if let lang { d["lang"] = lang }
        if let receiveFrom { d["receiveFrom"] = receiveFrom }
        return d
    }
}

/** sendLetter / replyLetter response: what is needed to keep my own letter verifiable. */
struct SentLetter: Equatable, Sendable {
    let threadId: String
    let deliveryId: String
    let sentAt: Int64
    let sig: String

    init?(callable data: Any?) {
        guard let m = data as? [String: Any],
              let threadId = m["threadId"] as? String,
              let deliveryId = m["deliveryId"] as? String,
              let sentAt = (m["sentAt"] as? NSNumber)?.int64Value else { return nil }
        self.threadId = threadId
        self.deliveryId = deliveryId
        self.sentAt = sentAt
        self.sig = m["sig"] as? String ?? ""
    }

    init(threadId: String, deliveryId: String, sentAt: Int64, sig: String) {
        self.threadId = threadId
        self.deliveryId = deliveryId
        self.sentAt = sentAt
        self.sig = sig
    }
}

/** What the screens need from the backend; tests substitute a fake. */
protocol BottlepostService: Sendable {
    var uid: String? { get }
    func signInIfNeeded() async throws
    func getProfile() async throws -> Profile?
    func createProfile(_ p: NewProfile) async throws -> Profile
    func updateProfile(_ u: ProfileUpdate) async throws -> Profile
    func registerPushToken(_ token: String) async throws
    func sendLetter(body: String, wantGender: String, sameLanguageOnly: Bool) async throws -> SentLetter
    func replyLetter(threadId: String, body: String) async throws -> SentLetter
    func leaveThread(_ threadId: String) async throws
}

/** Thin wrapper over the callable Cloud Functions in firebase/functions/src. */
final class FirebaseService: BottlepostService {
    static let region = "asia-northeast3"

    var uid: String? { Auth.auth().currentUser?.uid }

    /** Anonymous Firebase account: no sign-up; the uid never leaves our backend. */
    func signInIfNeeded() async throws {
        guard Auth.auth().currentUser == nil else { return }
        do {
            _ = try await Auth.auth().signInAnonymously()
        } catch {
            throw ApiError.from(error)
        }
    }

    func getProfile() async throws -> Profile? {
        Profile(callable: try await call("getProfile"))
    }

    func createProfile(_ p: NewProfile) async throws -> Profile {
        let data: [String: Any] = [
            "gender": p.gender, "birthYear": p.birthYear, "lang": p.lang, "country": p.country,
            "receiveFrom": p.receiveFrom, "nickname": p.nickname, "acceptedTerms": true, "termsVersion": "1",
        ]
        guard let profile = Profile(callable: try await call("createProfile", data)) else { throw ApiError(code: "unknown") }
        return profile
    }

    func updateProfile(_ u: ProfileUpdate) async throws -> Profile {
        guard let profile = Profile(callable: try await call("updateProfile", u.payload)) else { throw ApiError(code: "unknown") }
        return profile
    }

    func registerPushToken(_ token: String) async throws {
        _ = try await call("registerPushToken", ["token": token])
    }

    func sendLetter(body: String, wantGender: String, sameLanguageOnly: Bool) async throws -> SentLetter {
        let data: [String: Any] = ["body": body, "wantGender": wantGender, "sameLanguageOnly": sameLanguageOnly]
        guard let sent = SentLetter(callable: try await call("sendLetter", data)) else { throw ApiError(code: "unknown") }
        return sent
    }

    func replyLetter(threadId: String, body: String) async throws -> SentLetter {
        guard let sent = SentLetter(callable: try await call("replyLetter", ["threadId": threadId, "body": body])) else {
            throw ApiError(code: "unknown")
        }
        return sent
    }

    func leaveThread(_ threadId: String) async throws {
        _ = try await call("leaveThread", ["threadId": threadId])
    }

    private func call(_ name: String, _ data: [String: Any] = [:]) async throws -> Any? {
        do {
            return try await Functions.functions(region: Self.region).httpsCallable(name).call(data).data
        } catch {
            throw ApiError.from(error)
        }
    }
}
