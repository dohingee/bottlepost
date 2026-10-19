import Foundation

/** The owner's profile as returned by getProfile/createProfile (`view()` in firebase/functions/src/profile.ts). */
struct Profile: Equatable, Sendable {
    var gender: String
    var birthYear: Int
    var lang: String
    var country: String
    var receiveFrom: String
    var nickname: String
    var tags: [String]
    var linked: Bool
    var status: String
    var suspendedUntil: Date?

    /** `nil` when the server has no profile yet (onboarding not finished) or the payload is not an object. */
    init?(callable data: Any?) {
        guard let m = data as? [String: Any] else { return nil }
        gender = m["gender"] as? String ?? ""
        birthYear = (m["birthYear"] as? NSNumber)?.intValue ?? 0
        lang = m["lang"] as? String ?? "en"
        country = m["country"] as? String ?? ""
        receiveFrom = m["receiveFrom"] as? String ?? "ANY"
        nickname = m["nickname"] as? String ?? ""
        tags = m["tags"] as? [String] ?? []
        linked = m["linked"] as? Bool ?? false
        status = m["status"] as? String ?? "active"
        suspendedUntil = (m["suspendedUntil"] as? NSNumber).map { Date(timeIntervalSince1970: $0.doubleValue / 1000) }
    }

    var isBanned: Bool { status == "banned" }

    /** Suspended with no end date means "while a report is reviewed". */
    func isSuspended(now: Date = .now) -> Bool {
        status == "suspended" && (suspendedUntil.map { $0 > now } ?? true)
    }
}
