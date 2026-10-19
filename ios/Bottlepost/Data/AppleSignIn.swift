import AuthenticationServices
import CryptoKit
import Foundation
import UIKit

/**
 * Sign in with Apple for account restore. No scopes are requested: Apple shares neither the e-mail
 * nor the name, only a stable identifier, which our server keeps as a one-way hash (identity.ts).
 */
enum AppleSignIn {
    /** One-time value; its SHA-256 goes to Apple, the raw value to Firebase, so a token cannot be replayed. */
    static func randomNonce(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var bytes = [UInt8](repeating: 0, count: length)
        precondition(SecRandomCopyBytes(kSecRandomDefault, length, &bytes) == errSecSuccess)
        return String(bytes.map { charset[Int($0) % charset.count] })
    }

    static func sha256(_ text: String) -> String {
        SHA256.hash(data: Data(text.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    static func configure(_ request: ASAuthorizationAppleIDRequest, nonce: String) {
        request.requestedScopes = []
        request.nonce = sha256(nonce)
    }

    static func isCancel(_ error: Error) -> Bool {
        (error as? ASAuthorizationError)?.code == .canceled
    }
}

/** What Firebase needs from one Apple sign-in, plus the code used to revoke it on account deletion. */
struct AppleCredential: Equatable, Sendable {
    let idToken: String
    let authorizationCode: String
    let rawNonce: String

    init(idToken: String, authorizationCode: String, rawNonce: String) {
        self.idToken = idToken
        self.authorizationCode = authorizationCode
        self.rawNonce = rawNonce
    }

    init?(authorization: ASAuthorization, rawNonce: String) {
        guard let c = authorization.credential as? ASAuthorizationAppleIDCredential,
              let token = c.identityToken.flatMap({ String(data: $0, encoding: .utf8) }),
              let code = c.authorizationCode.flatMap({ String(data: $0, encoding: .utf8) }) else { return nil }
        self.init(idToken: token, authorizationCode: code, rawNonce: rawNonce)
    }
}

/**
 * Asks Apple again without a button (account deletion must revoke the Apple token, which needs a
 * fresh authorization code).
 */
final class AppleReauth: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<AppleCredential, Error>?
    private var nonce = ""

    @MainActor
    func run() async throws -> AppleCredential {
        nonce = AppleSignIn.randomNonce()
        let request = ASAuthorizationAppleIDProvider().createRequest()
        AppleSignIn.configure(request, nonce: nonce)
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        return try await withCheckedThrowingContinuation { cont in
            continuation = cont
            controller.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        if let credential = AppleCredential(authorization: authorization, rawNonce: nonce) {
            continuation?.resume(returning: credential)
        } else {
            continuation?.resume(throwing: ApiError(code: "unknown"))
        }
        continuation = nil
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        continuation?.resume(throwing: error)
        continuation = nil
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow) ?? ASPresentationAnchor()
    }
}
