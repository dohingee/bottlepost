import Foundation
import FirebaseFirestore

/**
 * Picks up letters from the relay: a live listener while the app is open, plus one-shot fetches
 * when the app becomes active and when a push arrives. Clients may only read and delete
 * deliveries addressed to them (firestore.rules).
 */
@MainActor
final class DeliverySync {
    private let mail: MailStore
    private var listener: ListenerRegistration?

    init(mail: MailStore) {
        self.mail = mail
    }

    private func query(_ uid: String) -> Query {
        Firestore.firestore().collection("deliveries").whereField("to", isEqualTo: uid)
    }

    func start(uid: String) {
        guard listener == nil else { return }
        listener = query(uid).addSnapshotListener { [weak self] snapshot, _ in
            guard let docs = snapshot?.documents else { return }
            let incoming = docs.map(Self.wrap)
            Task { @MainActor in await self?.mail.ingest(incoming) }
        }
    }

    func syncOnce(uid: String) async {
        guard let snapshot = try? await query(uid).getDocuments() else { return }
        await mail.ingest(snapshot.documents.map(Self.wrap))
    }

    func stop() {
        listener?.remove()
        listener = nil
    }

    private nonisolated static func wrap(_ doc: QueryDocumentSnapshot) -> IncomingDelivery {
        let ref = doc.reference
        return IncomingDelivery(id: doc.documentID, data: doc.data(), delete: { try await ref.delete() })
    }
}
