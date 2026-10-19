import AppTrackingTransparency
import FirebaseRemoteConfig
import GoogleMobileAds
import SwiftUI
import UserMessagingPlatform

/**
 * AdMob, the iOS twin of AdMobAdGate.kt: an interstitial after the user taps Send (before the letter
 * leaves) and the main screen's bottom banner. Rules (.claude/skills/enable-admob): consent first
 * (UMP), then Apple's tracking prompt (ATT); never on launch or exit or while reading; frequency
 * capped; never blocks sending (no ad within 2 s means no ad).
 *
 * Release builds stay ad-free until Remote Config `ads_enabled` is turned on. Debug builds always run
 * with Google's test ads.
 */
@MainActor
@Observable
final class AdsManager: AdGate {
    private(set) var bannersAllowed = false
    private(set) var privacyOptionsRequired = false

    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private var started = false
    @ObservationIgnored private var initialized = false
    @ObservationIgnored private var loading = false
    @ObservationIgnored private var interstitial: InterstitialAd?
    @ObservationIgnored private let dismissal = DismissalWaiter()

    static let keyEnabled = "ads_enabled"
    static let keyInterval = "ads_interstitial_min_interval_sec"
    static let keySkipFirst = "ads_skip_first_letters"
    static let keyMaxPerDay = "ads_max_per_day"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    private var remote: RemoteConfig { RemoteConfig.remoteConfig() }

    #if DEBUG
    private var enabled: Bool { true }
    private var interval: TimeInterval { 60 }
    private var skipFirst: Int { 0 }
    private var maxPerDay: Int { 50 }
    #else
    private var enabled: Bool { remote[Self.keyEnabled].boolValue }
    private var interval: TimeInterval { max(60, remote[Self.keyInterval].numberValue.doubleValue) }
    private var skipFirst: Int { remote[Self.keySkipFirst].numberValue.intValue }
    private var maxPerDay: Int { remote[Self.keyMaxPerDay].numberValue.intValue }
    #endif

    /** Main screen ready: fetch the switch, then consent and tracking permission only if ads are on. */
    func start() async {
        guard !started else { return }
        started = true
        remote.setDefaults([
            Self.keyEnabled: false as NSObject, Self.keyInterval: 180 as NSObject,
            Self.keySkipFirst: 1 as NSObject, Self.keyMaxPerDay: 5 as NSObject,
        ])
        _ = try? await remote.fetchAndActivate()
        guard enabled else { return }

        let consent = ConsentInformation.shared
        // Consent from an earlier session is enough to start loading right away.
        if consent.canRequestAds { await initAds() }
        do {
            try await consent.requestConsentInfoUpdate(with: RequestParameters())
            try await ConsentForm.loadAndPresentIfRequired(from: nil)
        } catch {
            // No consent information: ads stay off unless consent from before still allows them.
        }
        privacyOptionsRequired = consent.privacyOptionsRequirementStatus == .required
        if consent.canRequestAds { await initAds() }
    }

    func showPrivacyOptions() async {
        try? await ConsentForm.presentPrivacyOptionsForm(from: nil)
        privacyOptionsRequired = ConsentInformation.shared.privacyOptionsRequirementStatus == .required
    }

    private func initAds() async {
        guard !initialized else { return }
        initialized = true
        // Apple's prompt comes after Google's consent form and before the first ad request.
        if ATTrackingManager.trackingAuthorizationStatus == .notDetermined {
            _ = await ATTrackingManager.requestTrackingAuthorization()
        }
        _ = await MobileAds.shared.start()
        bannersAllowed = !AdUnits.banner.isEmpty
        preload()
    }

    private func preload() {
        guard initialized, interstitial == nil, !loading else { return }
        loading = true
        Task {
            interstitial = try? await InterstitialAd.load(with: AdUnits.interstitial, request: Request())
            loading = false
        }
    }

    func beforeSend() async {
        guard enabled, initialized, ConsentInformation.shared.canRequestAds else { return }
        let sends = defaults.integer(forKey: "adsSends")
        defaults.set(sends + 1, forKey: "adsSends")
        guard sends >= skipFirst else { return }

        let now = Date()
        if let last = defaults.object(forKey: "adsLastShown") as? Date, now.timeIntervalSince(last) < interval { return }
        let today = Calendar.current.startOfDay(for: now)
        let shownToday = (defaults.object(forKey: "adsDay") as? Date) == today ? defaults.integer(forKey: "adsDayCount") : 0
        guard shownToday < maxPerDay else { return }

        preload()
        for _ in 0..<20 where interstitial == nil {
            try? await Task.sleep(for: .milliseconds(100))
        }
        guard let ad = interstitial else { return }
        interstitial = nil
        ad.fullScreenContentDelegate = dismissal
        await dismissal.present(ad)
        defaults.set(now, forKey: "adsLastShown")
        defaults.set(today, forKey: "adsDay")
        defaults.set(shownToday + 1, forKey: "adsDayCount")
        preload()
    }
}

/** Resumes once the interstitial is closed (or could not be shown). */
private final class DismissalWaiter: NSObject, FullScreenContentDelegate {
    private var continuation: CheckedContinuation<Void, Never>?

    @MainActor
    func present(_ ad: InterstitialAd) async {
        await withCheckedContinuation { cont in
            continuation = cont
            ad.present(from: nil)
        }
    }

    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) { finish() }

    func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) { finish() }

    private func finish() {
        continuation?.resume()
        continuation = nil
    }
}

/** Ad unit IDs from Info.plist (build settings in project.yml: Google's test IDs until AdMob approves). */
enum AdUnits {
    static var banner: String { Bundle.main.object(forInfoDictionaryKey: "BPAdMobBanner") as? String ?? "" }
    static var interstitial: String { Bundle.main.object(forInfoDictionaryKey: "BPAdMobInterstitial") as? String ?? "" }
}

/** Anchored adaptive banner at the bottom of the main screen, like AdBanner.kt. */
struct AdBanner: UIViewRepresentable {
    let width: CGFloat

    func makeUIView(context: Context) -> BannerView {
        let view = BannerView(adSize: largeAnchoredAdaptiveBanner(width: width))
        view.adUnitID = AdUnits.banner
        view.rootViewController = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow?.rootViewController }.first
        view.load(Request())
        return view
    }

    func updateUIView(_ uiView: BannerView, context: Context) {}
}
