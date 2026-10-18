import FirebaseAppCheck
import FirebaseCore

/**
 * Debug builds (simulator) use the debug provider: the token printed in the Xcode console must be
 * added in Firebase console > App Check > Apps > iOS > Manage debug tokens. Release builds
 * (TestFlight, App Store) use App Attest, the iOS counterpart of Play Integrity.
 */
enum AppCheckSetup {
    static func factory() -> AppCheckProviderFactory {
        #if DEBUG
        AppCheckDebugProviderFactory()
        #else
        AppAttestFactory()
        #endif
    }
}

private final class AppAttestFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        AppAttestProvider(app: app)
    }
}
