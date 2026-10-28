import SwiftUI

/**
 * Report and block, reachable from every received letter, the conversation menu and the list's
 * long-press menu (App Review Guideline 1.2). Both remove the conversation from this phone at once;
 * a screen showing it then closes by itself.
 */
struct SafetyActions: ViewModifier {
    let session: Session
    let threadId: String
    @Binding var report: Bool
    @Binding var block: Bool

    func body(content: Content) -> some View {
        content
            .alert(L("block_title"), isPresented: $block) {
                Button(L("action_cancel"), role: .cancel) {}
                Button(L("action_block"), role: .destructive) { blockNow() }
            } message: {
                Text(L("block_body"))
            }
            .sheet(isPresented: $report) {
                ReportSheet { reason, note in submit(reason: reason, note: note) }
            }
    }

    private func blockNow() {
        Task {
            do {
                try await session.mail?.block(threadId: threadId)
                session.show("ios_block_removed")
            } catch {
                session.show(errorMessageKey(ApiError.from(error)))
            }
        }
    }

    private func submit(reason: String, note: String) {
        Task {
            do {
                try await session.mail?.report(threadId: threadId, reason: reason, note: note)
                session.show("ios_report_removed")
            } catch {
                session.show(errorMessageKey(ApiError.from(error)))
            }
        }
    }
}

extension View {
    func safetyActions(session: Session, threadId: String, report: Binding<Bool>, block: Binding<Bool>) -> some View {
        modifier(SafetyActions(session: session, threadId: threadId, report: report, block: block))
    }
}
