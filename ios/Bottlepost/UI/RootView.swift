import SwiftUI

/** Start: anonymous sign-in and profile load decide between onboarding and the main screen. */
struct RootView: View {
    @Bindable var session: Session
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        content
            .toast($session.toast)
            .task { await session.start() }
            .onChange(of: scenePhase) { _, phase in
                // The listener may have been suspended in the background: catch up once.
                if phase == .active { Task { await session.syncOnce() } }
                if phase == .background { backupInBackground() }
            }
    }

    /** Daily backup when leaving the app, with the time iOS grants a finishing task. */
    private func backupInBackground() {
        var task = UIBackgroundTaskIdentifier.invalid
        task = UIApplication.shared.beginBackgroundTask { UIApplication.shared.endBackgroundTask(task) }
        Task {
            await session.backupIfDue()
            UIApplication.shared.endBackgroundTask(task)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch session.state {
        case .loading:
            VStack(spacing: 0) {
                SeaTopBar(title: L("app_name"))
                ProgressView().tint(Sea.teal).frame(maxHeight: .infinity)
            }
            .background(SeaBackdrop())
        case .needsOnboarding:
            VStack(spacing: 0) {
                SeaTopBar(title: L("app_name"))
                OnboardingView(session: session)
            }
            .background(SeaBackdrop())
        case .underage:
            VStack(spacing: 0) {
                SeaTopBar(title: L("app_name"))
                UnderageView()
            }
            .background(SeaBackdrop())
        case .ready:
            MainView(session: session)
        case .failed(let error):
            VStack(spacing: 0) {
                SeaTopBar(title: L("app_name"))
                VStack(spacing: 16) {
                    PaperSheet { Text(errorMessage(error)) }
                    Button(L("action_retry")) { Task { await session.start() } }
                        .buttonStyle(BottleButtonStyle())
                }
                .padding(20)
                .frame(maxHeight: .infinity, alignment: .top)
            }
            .background(SeaBackdrop())
        }
    }
}

#Preview {
    RootView(session: Session(service: nil, mail: nil, sync: nil, ageLock: MemoryUnderageLock()))
}
