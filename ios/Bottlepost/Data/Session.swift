import Foundation
import Observation

/** App start: anonymous sign-in, then the profile decides between onboarding and the main screen. */
@MainActor
@Observable
final class Session {
    enum State: Equatable {
        case loading
        case needsOnboarding
        case ready(Profile)
        case failed(ApiError)
    }

    private(set) var state: State = .loading
    private let service: BottlepostService?

    init(service: BottlepostService?) {
        self.service = service
    }

    func start() async {
        guard let service else { return }
        state = .loading
        do {
            try await service.signInIfNeeded()
            state = try await service.getProfile().map(State.ready) ?? .needsOnboarding
        } catch {
            state = .failed(ApiError.from(error))
        }
    }
}
