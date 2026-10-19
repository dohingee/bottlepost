import SwiftData
import SwiftUI
import Translation

/** One conversation: alternating letters, waiting banner, reply box, leave / delete (ThreadScreen.kt). */
struct ThreadView: View {
    let session: Session
    let threadId: String

    @Environment(\.dismiss) private var dismiss
    @Query private var threads: [LetterThread]
    @Query private var letters: [Letter]
    @State private var reply = ""
    @State private var sending = false
    @State private var confirmLeave = false
    @State private var startDelete = false
    @State private var confirmBlock = false
    @State private var showReport = false

    init(session: Session, threadId: String) {
        self.session = session
        self.threadId = threadId
        _threads = Query(filter: #Predicate<LetterThread> { $0.threadId == threadId })
        _letters = Query(filter: #Predicate<Letter> { $0.threadId == threadId }, sort: \Letter.sentAt)
    }

    var body: some View {
        if let thread = threads.first {
            content(thread)
        } else {
            // Deleted: nothing left to show.
            Color.clear.onAppear { dismiss() }
        }
    }

    private func content(_ thread: LetterThread) -> some View {
        let name = thread.partnerNickname.isEmpty ? L("stranger") : thread.partnerNickname
        let title = thread.partnerGender.isEmpty ? name : "\(name) · \(selfGenderLabel(thread.partnerGender))"
        let waiting = !thread.closed && thread.awaitingReply()
        return VStack(spacing: 0) {
            SeaTopBar(title: title, onBack: { dismiss() }) {
                Menu {
                    if !thread.closed {
                        Button(L("action_leave"), systemImage: "rectangle.portrait.and.arrow.right") { confirmLeave = true }
                    }
                    Button(L("action_delete_thread"), systemImage: "trash", role: .destructive) { startDelete = true }
                    Divider()
                    Button(L("action_report"), systemImage: "exclamationmark.bubble") { showReport = true }
                    if !thread.closed {
                        Button(L("action_block"), systemImage: "hand.raised") { confirmBlock = true }
                    }
                } label: {
                    Image(systemName: "ellipsis.circle").font(.title3)
                }
                .accessibilityLabel(L("action_more"))
            }
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 14) {
                        if waiting { WaitingBanner() }
                        ForEach(letters) { letter in
                            LetterSheet(letter: letter, targetLang: session.profile?.lang ?? deviceLang())
                                .id(letter.deliveryId)
                        }
                    }
                    .padding(16)
                }
                .onAppear { scrollToEnd(proxy) }
                .onChange(of: letters.count) { _, _ in withAnimation { scrollToEnd(proxy) } }
            }
            footer(thread, waiting: waiting)
        }
        .background(SeaBackdrop())
        .toolbar(.hidden, for: .navigationBar)
        // Read while open, also for letters that arrive while it is open.
        .onAppear { session.mail?.markRead(threadId: threadId) }
        .onChange(of: letters.count) { _, _ in session.mail?.markRead(threadId: threadId) }
        .modifier(DeleteConversation(session: session, thread: thread, asMenu: false, start: $startDelete, onDeleted: { dismiss() }))
        .alert(L("block_title"), isPresented: $confirmBlock) {
            Button(L("action_cancel"), role: .cancel) {}
            Button(L("action_block"), role: .destructive) { block() }
        } message: {
            Text(L("block_body"))
        }
        .sheet(isPresented: $showReport) {
            ReportSheet { reason, note in report(reason: reason, note: note) }
        }
        .alert(L("leave_title"), isPresented: $confirmLeave) {
            Button(L("action_cancel"), role: .cancel) {}
            Button(L("action_leave"), role: .destructive) { leave() }
        } message: {
            Text(L("leave_body"))
        }
    }

    private func scrollToEnd(_ proxy: ScrollViewProxy) {
        if let last = letters.last { proxy.scrollTo(last.deliveryId, anchor: .bottom) }
    }

    @ViewBuilder
    private func footer(_ thread: LetterThread, waiting: Bool) -> some View {
        if thread.closed {
            VStack(spacing: 10) {
                Text(L(thread.partnerLeft ? "partner_left" : "thread_closed")).font(.callout).foregroundStyle(Sea.wax)
                Button(L("action_delete_thread"), role: .destructive) { startDelete = true }
            }
            .padding(16)
            .frame(maxWidth: .infinity)
            .background(Sea.paper.opacity(0.95))
        } else if !waiting {
            HStack(alignment: .bottom, spacing: 10) {
                TextField("", text: $reply, prompt: Text(L("reply_placeholder")), axis: .vertical)
                    .lineLimit(1...6)
                    .padding(10)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Sea.paper))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Sea.paperEdge))
                    .onChange(of: reply) { _, new in
                        if new.count > ComposeView.maxChars { reply = String(new.prefix(ComposeView.maxChars)) }
                    }
                Button { send() } label: {
                    Image(systemName: "paperplane.fill")
                        .foregroundStyle(Sea.paper)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(LinearGradient(colors: [Sea.deep, Sea.teal], startPoint: .leading, endPoint: .trailing)))
                }
                .disabled(sending)
                .accessibilityLabel(L("action_send"))
            }
            .padding(12)
            .background(Sea.foam.opacity(0.95))
        }
    }

    private func send() {
        let text = reply
        guard text.trimmingCharacters(in: .whitespacesAndNewlines).count >= ComposeView.minChars else {
            session.show("error_too_short")
            return
        }
        guard let mail = session.mail else { return }
        sending = true
        let myLang = session.profile?.lang ?? "en"
        Task {
            do {
                try await mail.reply(threadId: threadId, body: text, myLang: myLang)
                reply = ""
            } catch {
                session.show(errorMessageKey(ApiError.from(error)))
            }
            sending = false
        }
    }

    private func block() {
        Task {
            do {
                try await session.mail?.block(threadId: threadId)
                session.show("block_done")
                dismiss()
            } catch {
                session.show(errorMessageKey(ApiError.from(error)))
            }
        }
    }

    private func report(reason: String, note: String) {
        Task {
            do {
                try await session.mail?.report(threadId: threadId, reason: reason, note: note)
                session.show("report_done")
                dismiss()
            } catch {
                session.show(errorMessageKey(ApiError.from(error)))
            }
        }
    }

    private func leave() {
        Task {
            do {
                try await session.mail?.leave(threadId: threadId)
                session.show("leave_done")
            } catch {
                session.show(errorMessageKey(ApiError.from(error)))
            }
        }
    }
}

/** "Your bottle is on its way", above the letters while waiting for a reply. */
private struct WaitingBanner: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            DriftingBottleScene()
            Text(L("waiting_reply")).font(.system(.headline, design: .serif))
            Text(L("waiting_reply_hint")).font(.footnote)
        }
        .foregroundStyle(Sea.inkText)
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 18).fill(Sea.paper.opacity(0.9)))
    }
}

/** One letter on paper: mine tinted and indented from the left, theirs from the right. */
private struct LetterSheet: View {
    let letter: Letter
    /** The reader's letter language (profile lang), like Android. */
    let targetLang: String

    @State private var translated: String?
    /** translate_loading / _failed / _same_language / _unsupported while there is no translation. */
    @State private var notice: String?
    @State private var request: TranslationSession.Configuration?

    var body: some View {
        PaperSheet(tinted: letter.outgoing) {
            HStack {
                Text(L(letter.outgoing ? "letter_from_me" : "letter_from_them"))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(letter.outgoing ? Sea.teal : Sea.wax)
                Spacer()
                Text(shortDateTime(letter.sentAt)).font(.caption).foregroundStyle(Sea.inkText.opacity(0.6))
            }
            Text(translated ?? letter.body)
                .font(.system(size: 17, design: .serif))
                .lineSpacing(9)
                .textSelection(.enabled)
                .background(RuledLines(lineHeight: 29.5), alignment: .topLeading)
            if !letter.outgoing {
                if let notice {
                    Text(L(notice)).font(.footnote).foregroundStyle(Sea.inkText.opacity(0.7))
                }
                Button(L(translated == nil && notice == nil ? "action_translate" : "action_show_original")) { toggle() }
                    .font(.footnote.weight(.semibold))
                    .tint(Sea.teal)
            }
        }
        .padding(letter.outgoing ? .leading : .trailing, 28)
        .translationTask(request) { session in
            do {
                let response = try await session.translate(letter.body)
                translated = response.targetText
                notice = nil
            } catch {
                notice = "translate_failed"
            }
        }
    }

    private func toggle() {
        if translated != nil || notice != nil {
            translated = nil
            notice = nil
            request = nil
            return
        }
        let source = LetterTranslation.detectLanguage(letter.body, hint: letter.lang)
        switch LetterTranslation.plan(source: source, target: targetLang) {
        case .sameLanguage:
            notice = "translate_same_language"
        case .translate(let from):
            notice = "translate_loading"
            Task {
                guard await LetterTranslation.isSupported(from: from, to: targetLang) else {
                    notice = "translate_unsupported"
                    return
                }
                request = TranslationSession.Configuration(source: Locale.Language(identifier: from),
                                                           target: Locale.Language(identifier: targetLang))
            }
        }
    }
}
