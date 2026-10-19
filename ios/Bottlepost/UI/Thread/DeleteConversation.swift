import SwiftUI

/**
 * Delete with a second warning while the other person is still in the conversation, because
 * deleting also leaves it and they will see that (ThreadDialogs.kt).
 */
struct DeleteConversation: ViewModifier {
    let session: Session
    let thread: LetterThread
    var asMenu = true
    @Binding var start: Bool
    var onDeleted: () -> Void = {}

    @State private var confirm = false
    @State private var confirmActive = false

    func body(content: Content) -> some View {
        content
            .contextMenu {
                if asMenu {
                    Button(role: .destructive) { confirm = true } label: {
                        Label(L("action_delete_thread"), systemImage: "trash")
                    }
                }
            }
            .onChange(of: start) { _, new in
                if new { confirm = true; start = false }
            }
            .alert(L("delete_thread_title"), isPresented: $confirm) {
                Button(L("action_cancel"), role: .cancel) {}
                Button(L("action_delete"), role: .destructive) {
                    if thread.closed { delete() } else { confirmActive = true }
                }
            } message: {
                Text(L("delete_thread_body"))
            }
            .alert(L("delete_active_title"), isPresented: $confirmActive) {
                Button(L("action_cancel"), role: .cancel) {}
                Button(L("action_delete"), role: .destructive) { delete() }
            } message: {
                Text(L("delete_active_body"))
            }
    }

    private func delete() {
        let id = thread.threadId
        Task {
            do {
                try await session.mail?.delete(threadId: id)
                session.show("delete_done")
                onDeleted()
            } catch {
                session.show(errorMessageKey(ApiError.from(error)))
            }
        }
    }
}

extension View {
    /** Long-press menu on list cards. */
    func deleteConversation(session: Session, thread: LetterThread) -> some View {
        modifier(DeleteConversation(session: session, thread: thread, start: .constant(false)))
    }
}
