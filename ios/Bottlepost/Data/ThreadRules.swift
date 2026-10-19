import Foundation

/** The thread fields the list rules need; lets the rules be tested without a SwiftData store. */
protocol ThreadFlags {
    var startedByMe: Bool { get }
    var hasIncoming: Bool { get }
    var hasOutgoing: Bool { get }
    var unread: Int { get }
    var closed: Bool { get }
    var partnerLeft: Bool { get }
    var lastOutgoing: Bool { get }
    var lastAt: Int64 { get }
}

extension ThreadFlags {
    /** Both sides have written: shown under Connections. */
    var connected: Bool { hasIncoming && hasOutgoing }

    /** My letter is the last one and has not expired yet, so the server would answer wait_for_reply. */
    func awaitingReply(now: Int64 = nowMs()) -> Bool {
        lastOutgoing && now - lastAt < turnTimeoutMs
    }
}

/** No Sent tab: a letter I started shows up once someone replies (Connections). */
enum MailTab: Hashable {
    case inbox, connections

    func contains(_ t: ThreadFlags) -> Bool {
        switch self {
        case .inbox: !t.startedByMe && !t.connected
        case .connections: t.connected
        }
    }

    func badge(_ threads: [ThreadFlags]) -> Int {
        threads.filter(contains).reduce(0) { $0 + $1.unread }
    }
}

/** Status line under each card, same order as MainScreen.kt. */
enum ThreadStatus: Equatable {
    case partnerLeft, closed, waiting, newLetter, yourTurn

    init(_ t: ThreadFlags, now: Int64 = nowMs()) {
        if t.partnerLeft { self = .partnerLeft }
        else if t.closed { self = .closed }
        else if t.awaitingReply(now: now) { self = .waiting }
        else if t.unread > 0 { self = .newLetter }
        else { self = .yourTurn }
    }

    var key: String {
        switch self {
        case .partnerLeft: "partner_left"
        case .closed: "thread_closed"
        case .waiting: "status_waiting"
        case .newLetter: "status_new_letter"
        case .yourTurn: "status_your_turn"
        }
    }
}

/** Wax-seal text: the unread count, "9+" above nine. */
func sealText(_ unread: Int) -> String { unread > 9 ? "9+" : String(unread) }
