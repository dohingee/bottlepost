import SwiftData
import SwiftUI
import UserNotifications

enum Route: Hashable {
    case thread(String)
    case compose
}

/** Inbox / Connections / Settings, like MainScreen.kt (no Sent tab). */
struct MainView: View {
    let session: Session

    @Query(sort: \LetterThread.lastAt, order: .reverse) private var threads: [LetterThread]
    @State private var tab = Demo.isOn ? MainView.demoTab : Tab.inbox
    @State private var path: [Route] = Demo.isOn ? MainView.demoPath : []

    enum Tab: Hashable { case inbox, connections, settings }

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                SeaTopBar(title: L(titleKey))
                if let profile = session.profile, profile.status != "active" {
                    StatusBanner(profile: profile)
                }
                TabView(selection: $tab) {
                    list(.inbox, emptyKey: "empty_inbox")
                        .tabItem { Label(L("tab_inbox"), systemImage: "tray") }
                        .badge(MailTab.inbox.badge(threads))
                        .tag(Tab.inbox)
                    list(.connections, emptyKey: "empty_connections")
                        .tabItem { Label(L("tab_connections"), systemImage: "envelope.open") }
                        .badge(MailTab.connections.badge(threads))
                        .tag(Tab.connections)
                    SettingsView(session: session)
                        .tabItem { Label(L("tab_settings"), systemImage: "gearshape") }
                        .tag(Tab.settings)
                }
                .tint(Sea.deep)
                // Bottom banner below the tab bar, main screen only (not while reading or writing).
                if session.ads?.bannersAllowed == true {
                    Divider()
                    BannerSlot()
                }
            }
            .background(SeaBackdrop())
            .overlay(alignment: .bottomTrailing) {
                if tab != .settings && session.canWrite {
                    Button { path.append(.compose) } label: {
                        Label(L("action_write_letter"), systemImage: "square.and.pencil")
                    }
                    .buttonStyle(BottleButtonStyle())
                    .padding(.trailing, 20)
                    .padding(.bottom, 64)
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .thread(let id): ThreadView(session: session, threadId: id)
                case .compose: ComposeView(session: session, adGate: (session.ads as AdGate?) ?? NoAdGate())
                }
            }
        }
        .task {
            guard !Demo.isOn else { return }
            await askNotificationPermissionOnce()
            // After the notification question: consent form and tracking prompt only if ads are on.
            await session.ads?.start()
        }
    }

    /** Store screenshots open straight on the screen being captured (App/Demo.swift). */
    private static var demoTab: Tab { Demo.screen == .connections ? .connections : .inbox }
    private static var demoPath: [Route] {
        switch Demo.screen {
        case .compose: [.compose]
        case .thread: [.thread(Demo.threadId)]
        case .waiting: [.thread(Demo.waitingId)]
        case .inbox, .connections: []
        }
    }

    private var titleKey: String {
        switch tab {
        case .inbox: "tab_inbox"
        case .connections: "tab_connections"
        case .settings: "tab_settings"
        }
    }

    @ViewBuilder
    private func list(_ mailTab: MailTab, emptyKey: String) -> some View {
        let items = threads.filter { mailTab.contains($0) }
        if items.isEmpty {
            EmptyBottle(text: L(emptyKey))
        } else {
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(items) { t in
                        ThreadCard(thread: t)
                            .onTapGesture { path.append(.thread(t.threadId)) }
                            .deleteConversation(session: session, thread: t)
                    }
                }
                .padding(16)
                .padding(.bottom, 80)
            }
        }
    }

    /** Asked once, when the main screen first appears; letters still arrive without it. */
    private func askNotificationPermissionOnce() async {
        let center = UNUserNotificationCenter.current()
        guard await center.notificationSettings().authorizationStatus == .notDetermined else { return }
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
    }
}

/** Fixed-height slot for the anchored adaptive banner, so the layout does not jump while it loads. */
private struct BannerSlot: View {
    var body: some View {
        GeometryReader { geo in
            AdBanner(width: geo.size.width)
        }
        .frame(height: 60)
        .background(Sea.paper)
    }
}

/** Suspension / ban notice above the tabs. */
private struct StatusBanner: View {
    let profile: Profile

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(text).font(.callout.weight(.semibold))
            Text(L("status_appeal_hint")).font(.footnote)
        }
        .foregroundStyle(Sea.paper)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Sea.wax)
    }

    private var text: String {
        if profile.isBanned { return L("status_banned") }
        if let until = profile.suspendedUntil {
            return L("status_suspended_until", until.formatted(date: .abbreviated, time: .omitted))
        }
        return L("status_suspended_review")
    }
}

/** An airmail envelope for one conversation. */
struct ThreadCard: View {
    let thread: LetterThread

    var body: some View {
        let status = ThreadStatus(thread)
        let name = thread.partnerNickname.isEmpty ? L("stranger") : thread.partnerNickname
        VStack(spacing: 0) {
            AirmailStripe(faded: thread.closed)
            HStack(alignment: .top, spacing: 12) {
                PartnerSeal(name: name, key: thread.threadId, faded: thread.closed)
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(name).font(.system(.headline, design: .serif)).fontWeight(thread.unread > 0 ? .bold : .regular)
                        if !thread.partnerGender.isEmpty {
                            Text(selfGenderLabel(thread.partnerGender))
                                .font(.caption2)
                                .padding(.horizontal, 6).padding(.vertical, 2)
                                .background(Capsule().fill(Sea.foam))
                        }
                        Spacer()
                        Text(relativeTime(thread.lastAt)).font(.caption).foregroundStyle(Sea.inkText.opacity(0.6))
                    }
                    Text(thread.lastOutgoing ? L("preview_me", thread.lastPreview) : thread.lastPreview)
                        .font(.subheadline)
                        .lineLimit(2)
                    StatusLine(status: status)
                }
                if thread.unread > 0 { WaxSeal(count: thread.unread) }
            }
            .padding(14)
        }
        .foregroundStyle(Sea.inkText)
        .background(RoundedRectangle(cornerRadius: 10).fill(thread.closed ? Sea.parchment : Sea.paper))
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Sea.paperEdge))
        .shadow(color: .black.opacity(0.08), radius: 3, y: 2)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

struct StatusLine: View {
    let status: ThreadStatus

    var body: some View {
        HStack(spacing: 6) {
            if status == .waiting { Image(systemName: "waterbottle").font(.caption) }
            Text(L(status.key)).font(.caption.weight(.medium))
        }
        .foregroundStyle(color)
    }

    private var color: Color {
        switch status {
        case .partnerLeft, .closed, .newLetter: Sea.wax
        case .waiting: Sea.teal
        case .yourTurn: Sea.deep
        }
    }
}
