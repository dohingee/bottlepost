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

    init(service: BottlepostService?, mail: MailStore?, sync: DeliverySync?, ageLock: UnderageLock) {
        self.service = service
        self.mail = mail
        self.sync = sync
        self.ageLock = ageLock
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
    func deleteAccount() async throws {
        guard let service else { return }
        try await service.deleteAccount()
        sync?.stop()
        mail?.wipe()
        service.signOut()
        profile = nil
        state = .needsOnboarding
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
