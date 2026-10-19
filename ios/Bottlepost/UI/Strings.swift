import Foundation

/**
 * Looks up a key in Localizable.xcstrings, which tools/ios-strings.ts generates from the Android
 * string resources (same keys, same 8 locales). Add or change text there, never in the catalog.
 */
func L(_ key: String, _ args: CVarArg...) -> String {
    let format = Bundle.main.localizedString(forKey: key, value: nil, table: nil)
    return args.isEmpty ? format : String(format: format, locale: .current, arguments: args)
}
