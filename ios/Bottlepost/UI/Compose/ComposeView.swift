import SwiftUI

/** Hook for the pre-send interstitial (M7, AdMob). Never blocks sending. */
@MainActor
protocol AdGate {
    func beforeSend() async
}

struct NoAdGate: AdGate {
    func beforeSend() async {}
}

/** A new letter to a stranger, like ComposeScreen.kt. */
struct ComposeView: View {
    let session: Session
    var adGate: AdGate = NoAdGate()

    @Environment(\.dismiss) private var dismiss
    @State private var wantGender = "ANY"
    @State private var sameLanguageOnly = false
    @State private var text = Demo.draft
    @State private var sending = false
    @State private var launched = false
    @State private var error: ApiError?

    static let minChars = 2
    static let maxChars = 1000

    private var trimmedCount: Int { text.trimmingCharacters(in: .whitespacesAndNewlines).count }
    private var canSend: Bool { (Self.minChars...Self.maxChars).contains(trimmedCount) && !sending && !launched }

    var body: some View {
        VStack(spacing: 0) {
            SeaTopBar(title: L("compose_title"), onBack: { dismiss() })
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(L("compose_to")).font(.headline)
                    ChoiceChips(options: ["ANY", "F", "M"], selection: $wantGender, label: genderLabel)
                    Toggle(L("compose_same_language"), isOn: $sameLanguageOnly).tint(Sea.teal)

                    paper
                        .offset(x: launched ? 400 : 0, y: launched ? -160 : 0)
                        .rotationEffect(.degrees(launched ? 12 : 0))
                        .opacity(launched ? 0 : 1)

                    Text(L("compose_rules")).font(.footnote).foregroundStyle(Sea.inkText.opacity(0.7))
                    if let error {
                        Text(errorMessage(error)).font(.callout).foregroundStyle(Sea.wax)
                    }
                    Button { send() } label: {
                        Text(L(sending ? "compose_sending" : "compose_send")).frame(maxWidth: .infinity)
                    }
                    .buttonStyle(BottleButtonStyle())
                    .disabled(!canSend)
                    .opacity(canSend ? 1 : 0.5)
                }
                .foregroundStyle(Sea.inkText)
                .padding(20)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(SeaBackdrop())
        .toolbar(.hidden, for: .navigationBar)
    }

    private var paper: some View {
        VStack(alignment: .trailing, spacing: 6) {
            TextField("", text: $text, prompt: Text(L("compose_placeholder")), axis: .vertical)
                .lineLimit(10...)
                .font(.system(size: 17, design: .serif))
                .lineSpacing(6)
                .background(RuledLines(lineHeight: 28))
                .onChange(of: text) { _, new in
                    if new.count > Self.maxChars { text = String(new.prefix(Self.maxChars)) }
                }
            Text("\(text.count) / \(Self.maxChars)").font(.caption).foregroundStyle(Sea.inkText.opacity(0.6))
        }
        .padding(16)
        .background(Sea.paper)
        .clipShape(UnevenRoundedRectangle(topLeadingRadius: 4, bottomLeadingRadius: 14, bottomTrailingRadius: 4, topTrailingRadius: 14))
        .overlay(UnevenRoundedRectangle(topLeadingRadius: 4, bottomLeadingRadius: 14, bottomTrailingRadius: 4, topTrailingRadius: 14).stroke(Sea.paperEdge))
        .shadow(color: .black.opacity(0.1), radius: 3, y: 2)
    }

    private func send() {
        guard canSend, let mail = session.mail else { return }
        sending = true
        error = nil
        let body = text
        let gender = wantGender
        let same = sameLanguageOnly
        let myLang = session.profile?.lang ?? "en"
        // Detached from the view: the letter still goes out if the user leaves this screen.
        Task { @MainActor in
            await adGate.beforeSend()
            do {
                _ = try await mail.sendFirst(body: body, wantGender: gender, sameLanguageOnly: same, myLang: myLang)
                session.show("letter_launched")
                withAnimation(.easeIn(duration: 0.9)) { launched = true }
                try? await Task.sleep(for: .seconds(1))
                dismiss()
            } catch {
                self.error = ApiError.from(error)
            }
            sending = false
        }
    }
}
