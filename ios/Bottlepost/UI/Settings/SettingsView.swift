import SwiftUI

/** Profile and help links. Account deletion, appeals (M4) and backup (M6) come later. */
struct SettingsView: View {
    let session: Session

    @State private var nickname = ""
    @State private var receiveFrom = "ANY"
    @State private var lang = "en"
    @State private var loaded = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if let p = session.profile {
                    PaperSheet {
                        Text(L("settings_profile")).font(.system(.headline, design: .serif))
                        Text(L("settings_my_gender", selfGenderLabel(p.gender)) + " · \(p.birthYear)")
                        HStack {
                            TextField("", text: $nickname, prompt: Text(L("onboarding_nickname")))
                                .textFieldStyle(.roundedBorder)
                                .onChange(of: nickname) { _, new in
                                    if new.count > 20 { nickname = String(new.prefix(20)) }
                                }
                            Button(L("action_save")) {
                                update(ProfileUpdate(nickname: nickname.trimmingCharacters(in: .whitespaces)))
                            }
                            .disabled(nickname.trimmingCharacters(in: .whitespaces) == p.nickname)
                        }
                        Text(L("onboarding_receive_from")).font(.subheadline.weight(.semibold))
                        ChoiceChips(options: ["ANY", "M", "F"], selection: $receiveFrom, label: genderLabel)
                        Text(L("onboarding_language")).font(.subheadline.weight(.semibold))
                        ChoiceChips(options: supportedLangs, selection: $lang, label: langLabel)
                    }
                    .onAppear {
                        guard !loaded else { return }
                        nickname = p.nickname
                        receiveFrom = p.receiveFrom
                        lang = p.lang
                        loaded = true
                    }
                    .onChange(of: receiveFrom) { _, new in
                        if loaded && new != p.receiveFrom { update(ProfileUpdate(receiveFrom: new)) }
                    }
                    .onChange(of: lang) { _, new in
                        if loaded && new != p.lang { update(ProfileUpdate(lang: new)) }
                    }
                }

                PaperSheet {
                    Text(L("settings_help")).font(.system(.headline, design: .serif))
                    Link(L("settings_terms"), destination: Links.terms)
                    Link(L("settings_privacy"), destination: Links.privacy)
                    Link(L("settings_child_safety"), destination: Links.childSafety)
                    Link(L("settings_contact"), destination: Links.contact)
                }
                .tint(Sea.deep)
            }
            .foregroundStyle(Sea.inkText)
            .padding(16)
            .padding(.bottom, 40)
        }
    }

    private func update(_ u: ProfileUpdate) {
        Task {
            do {
                try await session.updateProfile(u)
                session.show("settings_saved")
            } catch {
                session.show(errorMessageKey(ApiError.from(error)))
            }
        }
    }
}
