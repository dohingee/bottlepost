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
        case AuthErrorDomain where ns.code == AuthErrorCode.networkError.rawValue, NSURLErrorDomain:
            return ApiError(code: "network")
        default:
            // Local failures (file, crypto, sign-in): the generic message, not "check your connection".
            return ApiError(code: "unknown")
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

/** One letter attached to a report, exactly as signed (signing.ts); the server verifies it. */
struct ReportMessage: Equatable, Sendable {
    let deliveryId: String
    let body: String
    let sentAt: Int64
    let sig: String
    /** "received" (written by the other person) or "sent". */
    let direction: String

    var payload: [String: Any] {
        ["deliveryId": deliveryId, "body": body, "sentAt": sentAt, "sig": sig, "direction": direction]
    }
}

/** Result of linking Sign in with Apple to the current (anonymous) user. */
enum LinkOutcome: Equatable, Sendable {
    /** Linked; the uid and everything on it stay. */
    case linked
    /** That Apple ID already belongs to another Bottlepost account (reinstall, new phone). */
    case belongsToAnother
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
    func blockThread(_ threadId: String) async throws
    func submitReport(threadId: String, reason: String, note: String, messages: [ReportMessage]) async throws
    func submitAppeal(_ message: String) async throws
    func deleteAccount() async throws
    func signOut()

    var isAppleLinked: Bool { get }
    func linkApple(_ credential: AppleCredential) async throws -> LinkOutcome
    /** After .belongsToAnother: signs in to the account that owns the Apple ID. */
    func switchToLinkedApple() async throws
    /** Removes the Firebase user itself; only for an anonymous user without a profile. */
    func deleteCurrentUser() async throws
    func confirmAccountLink() async throws -> Profile
    /** 32-byte AES key for the backup (getBackupKey). */
    func getBackupKey() async throws -> Data
    func revokeApple(authorizationCode: String) async throws
}

/** Thin wrapper over the callable Cloud Functions in firebase/functions/src. */
final class FirebaseService: BottlepostService, @unchecked Sendable {
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

    func blockThread(_ threadId: String) async throws {
        _ = try await call("blockThread", ["threadId": threadId])
    }

    func submitReport(threadId: String, reason: String, note: String, messages: [ReportMessage]) async throws {
        _ = try await call("submitReport", ["threadId": threadId, "reason": reason, "note": note,
                                            "messages": messages.map(\.payload)])
    }

    func submitAppeal(_ message: String) async throws {
        _ = try await call("submitAppeal", ["message": message, "kind": "appeal"])
    }

    func deleteAccount() async throws {
        _ = try await call("deleteAccount")
    }

    func signOut() {
        try? Auth.auth().signOut()
    }

    /** Credential handed back by Firebase when the Apple ID is already linked elsewhere. */
    private var pendingApple: AuthCredential?

    var isAppleLinked: Bool {
        Auth.auth().currentUser?.providerData.contains { $0.providerID == "apple.com" } == true
    }

    func linkApple(_ c: AppleCredential) async throws -> LinkOutcome {
        let credential = OAuthProvider.appleCredential(withIDToken: c.idToken, rawNonce: c.rawNonce, fullName: nil)
        guard let user = Auth.auth().currentUser else {
            pendingApple = credential
            return .belongsToAnother
        }
        do {
            _ = try await user.link(with: credential)
            // Fresh ID token so the server sees the apple.com identity.
            _ = try await user.getIDTokenResult(forcingRefresh: true)
            return .linked
        } catch let error as NSError where error.code == AuthErrorCode.credentialAlreadyInUse.rawValue {
            pendingApple = error.userInfo[AuthErrorUserInfoUpdatedCredentialKey] as? AuthCredential ?? credential
            return .belongsToAnother
        } catch {
            throw ApiError.from(error)
        }
    }

    func switchToLinkedApple() async throws {
        guard let pendingApple else { throw ApiError(code: "unknown") }
        do {
            _ = try await Auth.auth().signIn(with: pendingApple)
            self.pendingApple = nil
        } catch {
            throw ApiError.from(error)
        }
    }

    func deleteCurrentUser() async throws {
        do {
            try await Auth.auth().currentUser?.delete()
        } catch {
            throw ApiError.from(error)
        }
    }

    func confirmAccountLink() async throws -> Profile {
        guard let profile = Profile(callable: try await call("confirmAccountLink")) else { throw ApiError(code: "unknown") }
        return profile
    }

    func getBackupKey() async throws -> Data {
        guard let m = try await call("getBackupKey") as? [String: Any], let b64 = m["key"] as? String,
              let key = Data(base64Encoded: b64), key.count == 32 else { throw ApiError(code: "unknown") }
        return key
    }

    func revokeApple(authorizationCode: String) async throws {
        do {
            try await Auth.auth().revokeToken(withAuthorizationCode: authorizationCode)
        } catch {
            throw ApiError.from(error)
        }
    }

    private func call(_ name: String, _ data: [String: Any] = [:]) async throws -> Any? {
        do {
            return try await Functions.functions(region: Self.region).httpsCallable(name).call(data).data
        } catch {
            throw ApiError.from(error)
        }
    }
}
