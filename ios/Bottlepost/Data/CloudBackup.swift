import CryptoKit
import Foundation

/**
 * Encrypted conversation backup in the user's own iCloud (the iOS counterpart of the Android Drive
 * appDataFolder backup). The AES-256 key comes from our server (getBackupKey) and the ciphertext
 * lives only in iCloud, so neither side alone can read the letters.
 */
struct BackupPayload: Codable, Equatable {
    struct ThreadRow: Codable, Equatable {
        var threadId: String
        var startedByMe: Bool
        var partnerNickname: String
        var partnerGender: String
        var partnerLang: String
        var createdAt: Int64
        var lastAt: Int64
        var lastPreview: String
        var hasIncoming: Bool
        var hasOutgoing: Bool
        var unread: Int
        var closed: Bool
        var partnerLeft: Bool
        var lastOutgoing: Bool
    }

    struct LetterRow: Codable, Equatable {
        var deliveryId: String
        var threadId: String
        var outgoing: Bool
        var body: String
        var lang: String
        var sentAt: Int64
        var sig: String
    }

    var version = 1
    var createdAt: Int64
    var threads: [ThreadRow]
    var letters: [LetterRow]
}

enum BackupCrypto {
    static func seal(_ plain: Data, key: Data) throws -> Data {
        guard let combined = try AES.GCM.seal(plain, using: SymmetricKey(data: key)).combined else {
            throw ApiError(code: "unknown")
        }
        return combined
    }

    static func open(_ sealed: Data, key: Data) throws -> Data {
        try AES.GCM.open(AES.GCM.SealedBox(combined: sealed), using: SymmetricKey(data: key))
    }
}

/** Where the encrypted file is kept; tests use memory. */
protocol BackupStorage: Sendable {
    func write(_ data: Data) async throws
    /** nil when there is no backup. */
    func read() async throws -> Data?
    func remove() async throws
}

/**
 * The app's private iCloud Drive container (not visible in the Files app). Unlike CloudKit it needs
 * no server-side schema. Errors: icloud_unavailable (no iCloud account), backup_none.
 */
final class ICloudBackupStorage: BackupStorage {
    static let container = "iCloud.com.bottlepost.app"

    private func fileURL() async throws -> URL {
        // May block while iCloud sets up the container: never on the main thread.
        try await Task.detached {
            guard let base = FileManager.default.url(forUbiquityContainerIdentifier: Self.container) else {
                throw ApiError(code: "icloud_unavailable")
            }
            let dir = base.appendingPathComponent("Documents", isDirectory: true)
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            return dir.appendingPathComponent("mailbox.bpb")
        }.value
    }

    func write(_ data: Data) async throws {
        let url = try await fileURL()
        try coordinate(url, writing: true) { try data.write(to: $0, options: .atomic) }
    }

    func read() async throws -> Data? {
        let url = try await fileURL()
        let fm = FileManager.default
        // On a new phone the file is only a placeholder until iCloud downloads it.
        if (try? fm.startDownloadingUbiquitousItem(at: url)) == nil && !fm.fileExists(atPath: url.path) { return nil }
        for _ in 0..<60 {
            let status = try? url.resourceValues(forKeys: [.ubiquitousItemDownloadingStatusKey]).ubiquitousItemDownloadingStatus
            if status == .current || (status == nil && fm.fileExists(atPath: url.path)) { break }
            try await Task.sleep(for: .milliseconds(500))
        }
        guard fm.fileExists(atPath: url.path) else { return nil }
        var result: Data?
        try coordinate(url, writing: false) { result = try Data(contentsOf: $0) }
        return result
    }

    func remove() async throws {
        let url = try await fileURL()
        guard FileManager.default.fileExists(atPath: url.path) else { return }
        try coordinate(url, writing: true) { try FileManager.default.removeItem(at: $0) }
    }

    private func coordinate(_ url: URL, writing: Bool, _ body: (URL) throws -> Void) throws {
        var coordinationError: NSError?
        var bodyError: Error?
        let coordinator = NSFileCoordinator()
        let run: (URL) -> Void = { u in
            do { try body(u) } catch { bodyError = error }
        }
        if writing {
            coordinator.coordinate(writingItemAt: url, options: .forReplacing, error: &coordinationError, byAccessor: run)
        } else {
            coordinator.coordinate(readingItemAt: url, options: [], error: &coordinationError, byAccessor: run)
        }
        if let error = coordinationError ?? bodyError { throw error }
    }
}

/** Backup and restore of the local conversations. */
@MainActor
final class BackupManager {
    private let mail: MailStore
    private let service: BottlepostService
    private let storage: BackupStorage
    private let defaults: UserDefaults

    /** Same cadence as the Android daily backup worker. */
    static let interval: TimeInterval = 24 * 60 * 60

    init(mail: MailStore, service: BottlepostService, storage: BackupStorage, defaults: UserDefaults = .standard) {
        self.mail = mail
        self.service = service
        self.storage = storage
        self.defaults = defaults
    }

    var lastBackupAt: Date? {
        get { defaults.object(forKey: "lastBackupAt") as? Date }
        set { defaults.set(newValue, forKey: "lastBackupAt") }
    }

    func backup() async throws {
        let key = try await service.getBackupKey()
        let plain = try JSONEncoder().encode(mail.snapshot())
        try await storage.write(try BackupCrypto.seal(plain, key: key))
        lastBackupAt = Date()
    }

    /** Replaces the conversations on this phone with the backup. */
    func restore() async throws {
        guard let sealed = try await storage.read() else { throw ApiError(code: "backup_none") }
        let key = try await service.getBackupKey()
        let payload = try JSONDecoder().decode(BackupPayload.self, from: try BackupCrypto.open(sealed, key: key))
        mail.replaceAll(payload)
    }

    func backupIfDue(now: Date = Date()) async {
        if let last = lastBackupAt, now.timeIntervalSince(last) < Self.interval { return }
        try? await backup()
    }

    /** Account deleted: the backup goes too. */
    func deleteBackup() async {
        try? await storage.remove()
        lastBackupAt = nil
    }
}
