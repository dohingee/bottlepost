import UIKit
import UserNotifications
import FirebaseMessaging

/**
 * Push plumbing. The server sends an APNs alert with localization keys only (pushMessage.ts), so
 * iOS shows "A letter has drifted in" in the phone's language; the app then picks the letter up.
 */
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    @MainActor static var session: Session?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        guard !Runtime.isOffline else { return true }
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self
        // Gets an APNs token even before the user allows alerts (silent "left" pushes still work).
        application.registerForRemoteNotifications()
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        Task { @MainActor in Self.session?.setPushToken(fcmToken) }
    }

    /** Letter or "left" notice waiting on the relay: fetch it now, also when woken in the background. */
    func application(_ application: UIApplication,
                     didReceiveRemoteNotification userInfo: [AnyHashable: Any]) async -> UIBackgroundFetchResult {
        guard userInfo["type"] as? String == "letter" else { return .noData }
        await Self.session?.handlePush()
        return .newData
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }
}
