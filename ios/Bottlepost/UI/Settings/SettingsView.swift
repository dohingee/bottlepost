import AuthenticationServices
import SwiftUI

/** Profile, Apple account and iCloud backup, help links, appeal (when suspended) and account deletion. */
struct SettingsView: View {
    let session: Session

    @State private var nickname = ""
    @State private var receiveFrom = "ANY"
    @State private var lang = "en"
    @State private var loaded = false
    @State private var showAppeal = false
    @State private var confirmDelete = false
    @State private var deleting = false
    @State private var appleNonce = ""
    @State private var confirmSwitch = false
    @State private var confirmRestore = false

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

                accountSection

                PaperSheet {
                    Text(L("settings_help")).font(.system(.headline, design: .serif))
                    Link(L("settings_terms"), destination: Links.terms)
                    Link(L("settings_privacy"), destination: Links.privacy)
                    Link(L("settings_child_safety"), destination: Links.childSafety)
                    Link(L("settings_contact"), destination: Links.contact)
                    if session.ads?.privacyOptionsRequired == true {
                        Button(L("settings_ad_privacy")) { Task { await session.ads?.showPrivacyOptions() } }
                    }
                    if session.profile.map({ $0.status != "active" }) == true {
                        Button(L("settings_appeal")) { showAppeal = true }
                    }
                }
                .tint(Sea.deep)

                Button(role: .destructive) { confirmDelete = true } label: {
                    Group {
                        if deleting { ProgressView().tint(Sea.paper) } else { Text(L("action_delete_account")) }
                    }
                    .font(.headline)
                    .foregroundStyle(Sea.paper)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Capsule().fill(Sea.wax))
                }
                .disabled(deleting)
            }
            .foregroundStyle(Sea.inkText)
            .padding(16)
            .padding(.bottom, 40)
        }
        .alert(L("ios_switch_title"), isPresented: $confirmSwitch) {
            Button(L("action_cancel"), role: .cancel) {}
            Button(L("action_switch_account"), role: .destructive) { switchAccount() }
        } message: {
            Text(L("ios_switch_body"))
        }
        .alert(L("restore_confirm_title"), isPresented: $confirmRestore) {
            Button(L("action_cancel"), role: .cancel) {}
            Button(L("action_restore"), role: .destructive) { Task { await session.restoreBackup() } }
        } message: {
            Text(L("restore_confirm_body"))
        }
        .alert(L("delete_title"), isPresented: $confirmDelete) {
            Button(L("action_cancel"), role: .cancel) {}
            Button(L("action_delete_account"), role: .destructive) { deleteAccount() }
        } message: {
            Text(L("delete_body"))
        }
        .sheet(isPresented: $showAppeal) {
            AppealSheet(session: session)
        }
    }

    /** Linked: backup status and actions. Not linked: why to link, and the Apple button. */
    private var accountSection: some View {
        PaperSheet {
            Text(L("settings_account")).font(.system(.headline, design: .serif))
            if session.isAppleLinked {
                Label(L("ios_apple_linked"), systemImage: "apple.logo").font(.subheadline.weight(.semibold))
                Text(session.lastBackupAt.map { L("backup_last", $0.formatted(date: .abbreviated, time: .shortened)) }
                     ?? L("backup_never"))
                    .font(.footnote)
                HStack(spacing: 12) {
                    Button(L("action_backup_now")) { Task { await session.backupNow() } }
                    Button(L("action_restore")) { confirmRestore = true }
                    if session.backupBusy { ProgressView() }
                }
                .disabled(session.backupBusy)
                .tint(Sea.deep)
            } else {
                Text(L("ios_link_explain")).font(.footnote)
                SignInWithAppleButton(.signIn) { request in
                    appleNonce = AppleSignIn.randomNonce()
                    AppleSignIn.configure(request, nonce: appleNonce)
                } onCompletion: { result in
                    link(result)
                }
                .signInWithAppleButtonStyle(.black)
                .frame(height: 46)
            }
        }
    }

    private func link(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let authorization):
            guard let credential = AppleCredential(authorization: authorization, rawNonce: appleNonce) else { return }
            Task {
                do {
                    if try await session.linkApple(credential) == .needsSwitchConfirm { confirmSwitch = true }
                } catch {
                    session.show(errorMessageKey(ApiError.from(error)))
                }
            }
        case .failure(let failure):
            if !AppleSignIn.isCancel(failure) { session.show(errorMessageKey(ApiError.from(failure))) }
        }
    }

    private func switchAccount() {
        Task {
            do {
                try await session.switchToAppleAccount()
            } catch {
                session.show(errorMessageKey(ApiError.from(error)))
            }
        }
    }

    private func deleteAccount() {
        deleting = true
        Task {
            do {
                // Apple-linked: sign in with Apple once more so the token can be revoked.
                var reauth: AppleCredential?
                if session.isAppleLinked {
                    do {
                        reauth = try await AppleReauth().run()
                    } catch let cancel where AppleSignIn.isCancel(cancel) {
                        deleting = false
                        return
                    }
                }
                try await session.deleteAccount(appleReauth: reauth)
            } catch {
                session.show(errorMessageKey(ApiError.from(error)))
            }
            deleting = false
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

/** Appeal a suspension or ban: 5 to 1000 characters, answered by e-mail within 14 days. */
private struct AppealSheet: View {
    let session: Session

    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var sending = false

    private var valid: Bool { (5...1000).contains(text.trimmingCharacters(in: .whitespacesAndNewlines).count) }

    var body: some View {
        NavigationStack {
            Form {
                TextField("", text: $text, axis: .vertical)
                    .lineLimit(4...12)
                    .onChange(of: text) { _, new in
                        if new.count > 1000 { text = String(new.prefix(1000)) }
                    }
            }
            .navigationTitle(L("settings_appeal"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L("action_cancel")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("action_send")) { send() }.disabled(!valid || sending)
                }
            }
        }
    }

    private func send() {
        sending = true
        Task {
            do {
                try await session.submitAppeal(text)
                session.show("appeal_sent")
                dismiss()
            } catch {
                session.show(errorMessageKey(ApiError.from(error)))
            }
            sending = false
        }
    }
}
