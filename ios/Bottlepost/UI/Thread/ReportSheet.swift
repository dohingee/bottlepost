import SwiftUI

/** Reasons in the order of the Android dialog; "minor" suspends the sender at once (safety.ts). */
let reportReasons = ["spam", "scam", "sexual", "harassment", "minor", "hate", "self_harm", "other"]

/**
 * Report a conversation. Reporting is the only way letter text ever reaches our server, so the
 * consent text is always shown before sending (report_consent).
 */
struct ReportSheet: View {
    let onSubmit: (_ reason: String, _ note: String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var reason: String?
    @State private var note = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ForEach(reportReasons, id: \.self) { r in
                        Button { reason = r } label: {
                            HStack {
                                Image(systemName: reason == r ? "largecircle.fill.circle" : "circle")
                                    .foregroundStyle(reason == r ? Sea.wax : Sea.inkText.opacity(0.5))
                                Text(L("reason_\(r)")).foregroundStyle(Sea.inkText)
                            }
                        }
                        .accessibilityAddTraits(reason == r ? .isSelected : [])
                    }
                }
                Section {
                    TextField("", text: $note, prompt: Text(L("report_note_hint")), axis: .vertical)
                        .lineLimit(2...5)
                        .onChange(of: note) { _, new in
                            if new.count > 500 { note = String(new.prefix(500)) }
                        }
                } footer: {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(L("report_consent"))
                        Text(L("ios_report_contact", Links.supportEmail))
                    }
                }
            }
            .navigationTitle(L("report_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L("action_cancel")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("action_report")) {
                        if let reason {
                            onSubmit(reason, note.trimmingCharacters(in: .whitespacesAndNewlines))
                            dismiss()
                        }
                    }
                    .disabled(reason == nil)
                }
            }
        }
    }
}
