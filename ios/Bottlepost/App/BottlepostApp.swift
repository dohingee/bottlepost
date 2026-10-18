import SwiftUI
import FirebaseCore
import FirebaseAppCheck

@main
struct BottlepostApp: App {
    @State private var session = Session(service: Runtime.isUnitTest ? nil : FirebaseService())

    init() {
        // Unit tests run inside this app; they never talk to Firebase.
        guard !Runtime.isUnitTest else { return }
        // Must be installed before FirebaseApp.configure(); every callable enforces App Check.
        AppCheck.setAppCheckProviderFactory(AppCheckSetup.factory())
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            RootView(session: session)
        }
    }
}

enum Runtime {
    static let isUnitTest = ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
}
