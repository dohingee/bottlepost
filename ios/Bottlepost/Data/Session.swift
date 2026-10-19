import Foundation
import Observation

/** App start and the signed-in user's state: onboarding vs main screen, profile, mail, toasts. */
@MainActor
@Observable
final class Session {
    enum State: Equatable {
        case loading
        case needsOnboarding
        /** Failed the 18+ check on this phone (see AgeGate). */
        case underage
        case ready
        case failed(ApiError)
    }

    private(set) var state: State = .loading
    private(set) var profile: Profile?
    /** One-line message shown at the bottom (a string key). */
    var toast: String?

    let mail: MailStore?
    let ageLock: UnderageLock
    @ObservationIgnored private let service: BottlepostService?
    @ObservationIgnored private let sync: DeliverySync?
    @ObservationIgnored private var pushToken: String?
    @ObservationIgnored let backup: BackupManager?
    /** AdMob (M7); nil in unit tests. */
    let ads: AdsManager?
    /** Set while backing up or restoring, for the settings screen. */
    private(set) var backupBusy = false
    /** Shown in settings; read from the backup manager when it changes. */
    private(set) var lastBackupAt: Date?

    init(service: BottlepostService?, mail: MailStore?, sync: DeliverySync?, ageLock: UnderageLock,
         backup: BackupManager? = nil, ads: AdsManager? = nil) {
        self.ads = ads
        self.service = service
        self.mail = mail
        self.sync = sync
        self.ageLock = ageLock
        self.backup = backup
        self.lastBackupAt = backup?.lastBackupAt
    }

    func start() async {
        guard let service else { return }
        if ageLock.isLocked {
            state = .underage
            return
        }
        // A refresh (back from the background, or after a push start) keeps the main screen up.
        if state != .ready { state = .loading }
        do {
            try await service.signInIfNeeded()
            if let p = try await service.getProfile() {
                profile = p
                state = .ready
                await onReady()
            } else {
                // No profile: first start, or the account was deleted elsewhere (web page).
                mail?.wipe()
                state = .needsOnboarding
            }
        } catch {
            state = .failed(ApiError.from(error))
        }
    }

    /** Onboarding finished: creates the profile (the server re-checks the age). */
    func createProfile(_ p: NewProfile) async throws {
        guard let service else { return }
        do {
            profile = try await service.createProfile(p)
            // Apple was linked on the onboarding screen: record it now that a profile exists.
            if service.isAppleLinked, let linked = try? await service.confirmAccountLink() { profile = linked }
            state = .ready
            await onReady()
        } catch {
            let e = ApiError.from(error)
            if e.code == "underage" { failAgeCheck() }
            throw e
        }
    }

    func failAgeCheck() {
        ageLock.lock()
        state = .underage
    }

    func updateProfile(_ u: ProfileUpdate) async throws {
        guard let service else { return }
        profile = try await service.updateProfile(u)
    }

    func submitAppeal(_ message: String) async throws {
        try await service?.submitAppeal(message.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    /**
     * Deletes the account on the server (partners are told it left), then everything on this phone,
     * and starts over at onboarding. The age-check lock is about the phone, not the account: it stays.
     */
    func deleteAccount(appleReauth: AppleCredential? = nil) async throws {
        guard let service else { return }
        if service.isAppleLinked {
            // Apple requires revoking Sign in with Apple when the account goes. Not fatal: the
            // account deletion itself must never be blocked.
            if let code = appleReauth?.authorizationCode { try? await service.revokeApple(authorizationCode: code) }
            await backup?.deleteBackup()
            lastBackupAt = nil
        }
        try await service.deleteAccount()
        sync?.stop()
        mail?.wipe()
        service.signOut()
        profile = nil
        state = .needsOnboarding
    }

    // MARK: Apple account and backup

    var isAppleLinked: Bool { service?.isAppleLinked == true }

    enum AppleLinkResult: Equatable {
        case linked
        /** The Apple ID belongs to another account: ask before switching (this one would be deleted). */
        case needsSwitchConfirm
    }

    /** Settings: links Apple to this account, or reports that it belongs to another one. */
    func linkApple(_ credential: AppleCredential) async throws -> AppleLinkResult {
        guard let service else { return .linked }
        switch try await service.linkApple(credential) {
        case .linked:
            profile = try await service.confirmAccountLink()
            await backupNow(quiet: true)
            return .linked
        case .belongsToAnother:
            return .needsSwitchConfirm
        }
    }

    /** Settings, after the user confirmed: deletes this account and moves to the Apple ID's one. */
    func switchToAppleAccount() async throws {
        guard let service else { return }
        try await service.deleteAccount()
        sync?.stop()
        mail?.wipe()
        try await service.switchToLinkedApple()
        await afterSwitch()
    }

    /**
     * Onboarding "restore with Apple". Returns true when an existing account was restored, false when
     * the Apple ID is new (it stays linked and is recorded after createProfile).
     */
    func restoreWithApple(_ credential: AppleCredential) async throws -> Bool {
        guard let service else { return false }
        switch try await service.linkApple(credential) {
        case .linked:
            return false
        case .belongsToAnother:
            // Nothing exists yet for this anonymous user: drop it and sign in to the old account.
            try await service.deleteCurrentUser()
            try await service.switchToLinkedApple()
            await afterSwitch()
            return true
        }
    }

    private func afterSwitch() async {
        guard let service else { return }
        do {
            if let p = try await service.getProfile() {
                profile = p
                state = .ready
                await onReady()
                if (try? await backup?.restore()) != nil { show("backup_restored") }
            } else {
                profile = nil
                state = .needsOnboarding
            }
        } catch {
            state = .failed(ApiError.from(error))
        }
    }

    func backupNow(quiet: Bool = false) async {
        guard let backup else { return }
        backupBusy = true
        defer { backupBusy = false }
        do {
            try await backup.backup()
            lastBackupAt = backup.lastBackupAt
            if !quiet { show("ios_backup_done") }
        } catch {
            if !quiet { show(errorMessageKey(ApiError.from(error))) }
        }
    }

    func restoreBackup() async {
        guard let backup else { return }
        backupBusy = true
        defer { backupBusy = false }
        do {
            try await backup.restore()
            show("backup_restored")
        } catch {
            show(errorMessageKey(ApiError.from(error)))
        }
    }

    /** App went to the background: daily backup for linked accounts (Android BackupWorker). */
    func backupIfDue() async {
        guard state == .ready, isAppleLinked, let backup else { return }
        await backup.backupIfDue()
        lastBackupAt = backup.lastBackupAt
    }

    /** Fetches waiting letters once (app became active, or a push arrived). */
    func syncOnce() async {
        guard state == .ready, let uid = service?.uid else { return }
        await sync?.syncOnce(uid: uid)
    }

    /** A push arrived; the app may have been launched in the background without any view. */
    func handlePush() async {
        if state != .ready { await start() }
        await syncOnce()
    }

    /** FCM token from the push delegate; registered once there is a profile. */
    func setPushToken(_ token: String) {
        pushToken = token
        guard state == .ready else { return }
        Task { try? await service?.registerPushToken(token) }
    }

    func show(_ key: String) {
        toast = key
    }

    var canWrite: Bool { profile?.status == "active" }

    private func onReady() async {
        if let uid = service?.uid { sync?.start(uid: uid) }
        if let pushToken { try? await service?.registerPushToken(pushToken) }
    }
}
