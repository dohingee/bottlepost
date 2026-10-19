import Foundation
import Security

/**
 * 18+ check (docs/IOS_PLAN.md "18세 이상 확인"). Birth year only, counted conservatively like the
 * server (createProfile re-checks it): someone born in 2008 is treated as 17 for all of 2026.
 */
enum AgeGate {
    static let minAge = 18

    static func isAdult(birthYear: Int, currentYear: Int) -> Bool {
        currentYear - birthYear - 1 >= minAge
    }

    /** A complete, believable year; anything else just keeps the Start button disabled. */
    static func isPlausible(birthYear: Int, currentYear: Int) -> Bool {
        (1900...currentYear).contains(birthYear)
    }

    static var currentYear: Int { Calendar(identifier: .gregorian).component(.year, from: Date()) }
}

/**
 * Remembers that this phone failed the age check, so the person cannot simply retry with another
 * year or reinstall the app. Lives in the keychain, which survives deleting the app, and never
 * leaves the device.
 */
protocol UnderageLock: AnyObject {
    var isLocked: Bool { get }
    func lock()
}

final class KeychainUnderageLock: UnderageLock {
    private let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: "com.bottlepost.app.agegate",
        kSecAttrAccount as String: "underage",
    ]

    var isLocked: Bool {
        SecItemCopyMatching(query as CFDictionary, nil) == errSecSuccess
    }

    func lock() {
        guard !isLocked else { return }
        var item = query
        item[kSecValueData as String] = Data("1".utf8)
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(item as CFDictionary, nil)
    }
}

/** For tests and previews. */
final class MemoryUnderageLock: UnderageLock {
    private(set) var isLocked = false
    func lock() { isLocked = true }
}

/** What Apple's Declared Age Range answered (iOS 26+). Never stored or sent to the server. */
enum DeclaredAge: Equatable {
    case adult, minor, unknown
}
