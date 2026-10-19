import SwiftUI

/** "Letter in a bottle" palette, same values as android/.../ui/common/Sea.kt and the app icon. */
enum Sea {
    static let night = Color(hex: 0x13283F)
    static let deep = Color(hex: 0x1F4A73)
    static let teal = Color(hex: 0x2F7A86)
    static let waveBack = Color(hex: 0x3B93A0)
    static let waveFront = Color(hex: 0x5FB8BE)
    static let foam = Color(hex: 0xDCEBEA)
    static let moon = Color(hex: 0xF6E7C1)
    static let paper = Color(hex: 0xFBF7F0)
    static let parchment = Color(hex: 0xF3E6CC)
    static let paperEdge = Color(hex: 0xE2D3B5)
    static let rule = Color(hex: 0xD9CDB6)
    static let inkText = Color(hex: 0x243447)
    static let cork = Color(hex: 0xC9A66B)
    static let wax = Color(hex: 0xB5473A)

    static let nightGradient = LinearGradient(colors: [night, deep], startPoint: .top, endPoint: .bottom)
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

/** Sine band used for the wave edge under the top bar and the sea at the bottom of screens. */
struct WaveShape: Shape {
    var phase: Double = 0
    var waves: Double = 3
    /** Wave height as a fraction of the shape height. */
    var amplitude: Double = 0.5

    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.minX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        let wavelength = rect.width / waves
        var x = rect.maxX
        while x >= rect.minX - 1 {
            let y = rect.height * (1 - amplitude) + rect.height * amplitude * sin(2 * .pi * (x / wavelength + phase))
            p.addLine(to: CGPoint(x: x, y: rect.minY + y))
            x -= 4
        }
        p.closeSubpath()
        return p
    }
}

/** Night-sea title band with a wave edge underneath, the iOS twin of SeaTopBar. */
struct SeaTopBar<Trailing: View>: View {
    let title: String
    var onBack: (() -> Void)?
    @ViewBuilder var trailing: Trailing

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                if let onBack {
                    Button(action: onBack) {
                        Image(systemName: "chevron.backward").font(.title3.weight(.semibold))
                    }
                    .accessibilityLabel(L("action_back"))
                }
                Text(title)
                    .font(.system(.title2, design: .serif).weight(.semibold))
                    .lineLimit(1)
                Spacer()
                trailing
            }
            .foregroundStyle(Sea.paper)
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(Sea.nightGradient.ignoresSafeArea(edges: .top))
            WaveShape().fill(Sea.deep).frame(height: 10)
        }
    }
}

extension SeaTopBar where Trailing == EmptyView {
    init(title: String, onBack: (() -> Void)? = nil) {
        self.init(title: title, onBack: onBack) { EmptyView() }
    }
}

/** Screen background: warm paper fading into sea foam (night water in dark mode). */
struct SeaBackdrop: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        LinearGradient(
            colors: scheme == .dark ? [Color(hex: 0x121821), Sea.night] : [Sea.paper, Sea.foam],
            startPoint: .top, endPoint: .bottom
        )
        .ignoresSafeArea()
    }
}

/** A sheet of letter paper: always paper-colored with dark ink, also in dark mode. */
struct PaperSheet<Content: View>: View {
    var tinted = false
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) { content }
            .foregroundStyle(Sea.inkText)
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                UnevenRoundedRectangle(topLeadingRadius: 4, bottomLeadingRadius: 14, bottomTrailingRadius: 4, topTrailingRadius: 14)
                    .fill(tinted ? Sea.parchment : Sea.paper)
                    .stroke(Sea.paperEdge, lineWidth: 1)
                    .shadow(color: .black.opacity(0.12), radius: 3, y: 2)
            )
    }
}

/** Primary action: a pill from deep sea to teal. */
struct BottleButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(Sea.paper)
            .padding(.horizontal, 28)
            .padding(.vertical, 12)
            .background(Capsule().fill(LinearGradient(colors: [Sea.deep, Sea.teal], startPoint: .leading, endPoint: .trailing)))
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}
