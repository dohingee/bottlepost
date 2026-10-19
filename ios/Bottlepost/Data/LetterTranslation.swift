import Foundation
import NaturalLanguage
import Translation

/**
 * On-device translation of a received letter (Apple Translation; the iOS counterpart of
 * LetterTranslator.kt with ML Kit). The text never leaves the phone; a language pack may be
 * downloaded the first time, with the system's own prompt.
 */
enum LetterTranslation {
    enum Plan: Equatable {
        case sameLanguage
        case translate(from: String)
    }

    /** The sender's language from the delivery is a hint; on-device detection wins when it is sure. */
    static func detectLanguage(_ text: String, hint: String) -> String {
        let recognizer = NLLanguageRecognizer()
        recognizer.processString(text)
        if let best = recognizer.languageHypotheses(withMaximum: 1).first, best.value >= 0.6 {
            return baseCode(best.key.rawValue)
        }
        return hint.isEmpty ? (recognizer.dominantLanguage.map { baseCode($0.rawValue) } ?? "") : hint
    }

    static func plan(source: String, target: String) -> Plan {
        baseCode(source) == baseCode(target) ? .sameLanguage : .translate(from: source)
    }

    /** "zh-Hant" stays as is; "pt-BR" -> "pt" so it matches our 2-letter profile languages. */
    static func baseCode(_ code: String) -> String {
        code.hasPrefix("zh") ? code : String(code.prefix { $0 != "-" && $0 != "_" })
    }

    static func isSupported(from source: String, to target: String) async -> Bool {
        let status = await LanguageAvailability().status(from: Locale.Language(identifier: source),
                                                         to: Locale.Language(identifier: target))
        return status != .unsupported
    }
}
