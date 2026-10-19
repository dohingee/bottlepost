import Foundation

/** Public pages served by Firebase Hosting (firebase/hosting), same as Links in Common.kt. */
enum Links {
    static let base = "https://bottlepost.web.app"
    // Korean originals; every other UI language gets the English translation.
    private static var korean: Bool { Bundle.main.preferredLocalizations.first?.hasPrefix("ko") == true }
    static var terms: URL { URL(string: korean ? "\(base)/terms" : "\(base)/terms-en")! }
    static var privacy: URL { URL(string: korean ? "\(base)/privacy" : "\(base)/privacy-en")! }
    static let childSafety = URL(string: "\(base)/child-safety")!
    static let supportEmail = "kimyeoware@gmail.com"
    static var contact: URL { URL(string: "mailto:\(supportEmail)?subject=Bottlepost")! }
}

/** Letter languages (server SUPPORTED_LANGS); also the UI languages of the app. */
let supportedLangs = ["ko", "en", "ja", "es", "pt", "id", "vi", "th"]

func langLabel(_ code: String) -> String { L("lang_\(supportedLangs.contains(code) ? code : "en")") }

/** The phone's language if supported, else English (onboarding default). */
func deviceLang() -> String {
    let code = Locale.current.language.languageCode?.identifier ?? "en"
    let mapped = code == "in" ? "id" : code
    return supportedLangs.contains(mapped) ? mapped : "en"
}

func deviceCountry() -> String { Locale.current.region?.identifier ?? "" }

/** "5 min. ago", "yesterday", in the app language. */
func relativeTime(_ ms: Int64) -> String {
    Date(timeIntervalSince1970: Double(ms) / 1000).formatted(.relative(presentation: .named, unitsStyle: .abbreviated))
}

func shortDateTime(_ ms: Int64) -> String {
    Date(timeIntervalSince1970: Double(ms) / 1000).formatted(date: .abbreviated, time: .shortened)
}

/** Recipient choices are plural ("Men"); the user's own gender is singular ("Man"). */
func genderLabel(_ code: String) -> String {
    switch code {
    case "M": L("gender_male")
    case "F": L("gender_female")
    default: L("gender_any")
    }
}

func selfGenderLabel(_ code: String) -> String {
    L(code == "M" ? "gender_self_male" : "gender_self_female")
}
