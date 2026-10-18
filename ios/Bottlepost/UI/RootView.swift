import SwiftUI

/**
 * First screen. Milestone 1 of docs/IOS_PLAN.md: signs in anonymously and loads the profile.
 * Onboarding and the inbox replace the placeholders in later milestones.
 */
struct RootView: View {
    let session: Session

    var body: some View {
        VStack(spacing: 0) {
            SeaTopBar(title: L("app_name"))
            ScrollView {
                content
                    .padding(20)
                    .frame(maxWidth: .infinity)
            }
        }
        .background(SeaBackdrop())
        .task { await session.start() }
    }

    @ViewBuilder
    private var content: some View {
        switch session.state {
        case .loading:
            ProgressView()
                .tint(Sea.teal)
                .padding(.top, 80)
        case .needsOnboarding:
            PaperSheet {
                Text(L("onboarding_tagline"))
                    .font(.system(.title3, design: .serif))
                Text(L("onboarding_privacy_note"))
                    .font(.callout)
            }
        case .ready(let profile):
            ProfileCard(profile: profile)
        case .failed(let error):
            VStack(spacing: 16) {
                PaperSheet {
                    Text(errorMessage(error))
                }
                Button(L("action_retry")) {
                    Task { await session.start() }
                }
                .buttonStyle(BottleButtonStyle())
            }
        }
    }
}

private struct ProfileCard: View {
    let profile: Profile

    var body: some View {
        PaperSheet(tinted: true) {
            Text(L("settings_profile"))
                .font(.system(.headline, design: .serif))
            Text(L("settings_my_gender", genderLabel(profile.gender)) + " · \(profile.birthYear)")
            if !profile.nickname.isEmpty {
                Text(profile.nickname)
            }
            if let status = statusText {
                Text(status)
                    .foregroundStyle(Sea.wax)
            }
        }
    }

    private var statusText: String? {
        if profile.isBanned { return L("status_banned") }
        guard profile.isSuspended() else { return nil }
        if let until = profile.suspendedUntil {
            return L("status_suspended_until", until.formatted(date: .abbreviated, time: .omitted))
        }
        return L("status_suspended_review")
    }
}

func genderLabel(_ code: String) -> String {
    switch code {
    case "M": L("gender_male")
    case "F": L("gender_female")
    default: L("gender_any")
    }
}

#Preview {
    RootView(session: Session(service: nil))
}
