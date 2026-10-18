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
            // Offline surfaces as a plain URL error; timeouts and outages as these two codes.
            switch FunctionsErrorCode(rawValue: ns.code) {
            case .unavailable, .deadlineExceeded: return ApiError(code: "network")
            default: return ApiError(code: ns.localizedDescription)
            }
        case AuthErrorDomain where ns.code == AuthErrorCode.networkError.rawValue:
            return ApiError(code: "network")
        default:
            return ApiError(code: "network")
        }
    }
}

/** What the screens need from the backend; tests substitute a fake. */
protocol BottlepostService: Sendable {
    func signInIfNeeded() async throws
    func getProfile() async throws -> Profile?
}

/** Thin wrapper over the callable Cloud Functions in firebase/functions/src. */
final class FirebaseService: BottlepostService {
    static let region = "asia-northeast3"

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

    private func call(_ name: String, _ data: [String: Any] = [:]) async throws -> Any? {
        do {
            return try await Functions.functions(region: Self.region).httpsCallable(name).call(data).data
        } catch {
            throw ApiError.from(error)
        }
    }
}
