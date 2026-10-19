import SwiftData
import SwiftUI
import FirebaseCore
import FirebaseAppCheck

@main
struct BottlepostApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    private let container: ModelContainer
    @State private var session: Session

    init() {
        // Unit tests run inside this app; they never talk to Firebase and keep no files.
        let unitTest = Runtime.isUnitTest
        if !unitTest {
            // Must be installed before FirebaseApp.configure(); every callable enforces App Check.
            AppCheck.setAppCheckProviderFactory(AppCheckSetup.factory())
            FirebaseApp.configure()
        }
        do {
            container = try ModelContainer(for: LetterThread.self, Letter.self,
                                           configurations: ModelConfiguration(isStoredInMemoryOnly: unitTest))
        } catch {
            fatalError("local store: \(error)")
        }
        let service: BottlepostService? = unitTest ? nil : FirebaseService()
        let mail = MailStore(context: container.mainContext, service: service)
        let backup = service.map { BackupManager(mail: mail, service: $0, storage: ICloudBackupStorage()) }
        let session = Session(service: service, mail: mail, sync: unitTest ? nil : DeliverySync(mail: mail),
                              ageLock: KeychainUnderageLock(), backup: backup)
        _session = State(initialValue: session)
        // A push can launch the app in the background, before any view exists.
        AppDelegate.session = session
    }

    var body: some Scene {
        WindowGroup {
            RootView(session: session)
        }
        .modelContainer(container)
    }
}

enum Runtime {
    static let isUnitTest = ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
}
