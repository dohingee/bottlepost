import SwiftUI
#if canImport(DeclaredAgeRange)
import DeclaredAgeRange
#endif

/**
 * Profile creation. The age question is neutral (docs/IOS_PLAN.md): an empty birth-year field and no
 * hint about the 18+ cut-off before it is answered; a failed check locks this phone (AgeGate).
 */
struct OnboardingView: View {
    let session: Session

    @State private var gender = ""
    @State private var birthYear = ""
    @State private var lang = deviceLang()
    @State private var receiveFrom = "ANY"
    @State private var nickname = ""
    @State private var agreeTerms = false
    @State private var agreePrivacy = false
    @State private var busy = false
    @State private var error: ApiError?

    private var year: Int? { Int(birthYear) }

    private var canSubmit: Bool {
        guard let year, AgeGate.isPlausible(birthYear: year, currentYear: AgeGate.currentYear) else { return false }
        return !gender.isEmpty && agreeTerms && agreePrivacy && !busy
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                PaperSheet {
                    Text(L("onboarding_tagline")).font(.system(.title3, design: .serif))
                    Text(L("onboarding_privacy_note")).font(.callout)
                }

                section("onboarding_my_gender") {
                    ChoiceChips(options: ["M", "F"], selection: $gender, label: selfGenderLabel)
                }
                section("onboarding_birth_year") {
                    TextField("", text: $birthYear, prompt: Text(verbatim: "YYYY"))
                        .keyboardType(.numberPad)
                        .textFieldStyle(.roundedBorder)
                        .frame(maxWidth: 140)
                        .onChange(of: birthYear) { _, new in
                            let digits = String(new.filter(\.isNumber).prefix(4))
                            if digits != new { birthYear = digits }
                        }
                }
                section("onboarding_language") {
                    ChoiceChips(options: supportedLangs, selection: $lang, label: langLabel)
                }
                section("onboarding_receive_from") {
                    ChoiceChips(options: ["ANY", "M", "F"], selection: $receiveFrom, label: genderLabel)
                }
                section("onboarding_nickname") {
                    TextField("", text: $nickname, prompt: Text(L("onboarding_nickname_hint")))
                        .textFieldStyle(.roundedBorder)
                        .onChange(of: nickname) { _, new in
                            if new.count > 20 { nickname = String(new.prefix(20)) }
                        }
                }

                VStack(alignment: .leading, spacing: 10) {
                    agreement("onboarding_agree_terms", isOn: $agreeTerms, link: Links.terms)
                    agreement("onboarding_agree_privacy", isOn: $agreePrivacy, link: Links.privacy)
                }

                if let error {
                    Text(errorMessage(error)).foregroundStyle(Sea.wax).font(.callout)
                }

                Button {
                    Task { await submit() }
                } label: {
                    Group {
                        if busy { ProgressView().tint(Sea.paper) } else { Text(L("onboarding_start")) }
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(BottleButtonStyle())
                .disabled(!canSubmit)
                .opacity(canSubmit ? 1 : 0.5)
            }
            .padding(20)
        }
        .scrollDismissesKeyboard(.interactively)
        .modifier(DeclaredAgeCheck { result in
            if result == .minor { session.failAgeCheck() }
        })
    }

    private func section<Content: View>(_ titleKey: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L(titleKey)).font(.headline).foregroundStyle(Sea.inkText)
            content()
        }
    }

    private func agreement(_ key: String, isOn: Binding<Bool>, link: URL) -> some View {
        HStack {
            Toggle(isOn: isOn) { Text(L(key)).font(.subheadline) }
                .toggleStyle(CheckboxStyle())
            Spacer()
            Link(L("action_view"), destination: link).font(.subheadline)
        }
    }

    private func submit() async {
        guard let year else { return }
        guard AgeGate.isAdult(birthYear: year, currentYear: AgeGate.currentYear) else {
            session.failAgeCheck()
            return
        }
        busy = true
        error = nil
        defer { busy = false }
        let name = nickname.trimmingCharacters(in: .whitespaces)
        do {
            try await session.createProfile(NewProfile(gender: gender, birthYear: year, lang: lang, country: deviceCountry(),
                                                       receiveFrom: receiveFrom, nickname: name))
        } catch {
            self.error = ApiError.from(error)
        }
    }
}

/** Shown instead of onboarding once this phone failed the age check. */
struct UnderageView: View {
    var body: some View {
        PaperSheet {
            Text(L("error_underage")).font(.system(.title3, design: .serif))
        }
        .padding(20)
        .frame(maxHeight: .infinity, alignment: .top)
    }
}

/** A checkbox, since iOS has no native one. */
struct CheckboxStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        Button { configuration.isOn.toggle() } label: {
            HStack(spacing: 10) {
                Image(systemName: configuration.isOn ? "checkmark.square.fill" : "square")
                    .foregroundStyle(configuration.isOn ? Sea.teal : Sea.inkText.opacity(0.6))
                    .font(.title3)
                configuration.label.foregroundStyle(Sea.inkText)
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(configuration.isOn ? .isSelected : [])
    }
}

/**
 * Asks Apple for the user's declared age range (iOS 26+) once. Only "under 18" changes anything;
 * declining or older iOS falls back to the birth year. The answer is neither stored nor sent.
 */
struct DeclaredAgeCheck: ViewModifier {
    let onResult: (DeclaredAge) -> Void

    func body(content: Content) -> some View {
        #if canImport(DeclaredAgeRange)
        if #available(iOS 26.0, *) {
            content.modifier(DeclaredAgeRequest(onResult: onResult))
        } else {
            content
        }
        #else
        content
        #endif
    }
}

#if canImport(DeclaredAgeRange)
@available(iOS 26.0, *)
private struct DeclaredAgeRequest: ViewModifier {
    let onResult: (DeclaredAge) -> Void
    @Environment(\.requestAgeRange) private var requestAgeRange

    func body(content: Content) -> some View {
        content.task {
            do {
                switch try await requestAgeRange(ageGates: AgeGate.minAge) {
                case .sharing(let range):
                    if let upper = range.upperBound, upper < AgeGate.minAge {
                        onResult(.minor)
                    } else if let lower = range.lowerBound, lower >= AgeGate.minAge {
                        onResult(.adult)
                    } else {
                        onResult(.unknown)
                    }
                case .declinedSharing:
                    onResult(.unknown)
                @unknown default:
                    onResult(.unknown)
                }
            } catch {
                onResult(.unknown)
            }
        }
    }
}
#endif
