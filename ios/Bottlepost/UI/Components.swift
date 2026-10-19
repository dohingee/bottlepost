import SwiftUI

/** A band of sea at the bottom of its frame; crest is the band height as a fraction (Sea.kt seaWavePath). */
struct SeaBand: Shape {
    var phase: Double
    var crest: Double
    var amplitude: Double = 10
    var waves: Double = 1.5

    func path(in rect: CGRect) -> Path {
        var p = Path()
        let top = rect.height * (1 - crest)
        let wavelength = rect.width / waves
        p.move(to: CGPoint(x: 0, y: rect.height))
        var x = 0.0
        while x <= rect.width + 1 {
            p.addLine(to: CGPoint(x: x, y: top + amplitude * sin(2 * .pi * (x / wavelength + phase))))
            x += 4
        }
        p.addLine(to: CGPoint(x: rect.width, y: rect.height))
        p.closeSubpath()
        return p
    }
}

/** The red-and-blue diagonal edge of an airmail envelope. */
struct AirmailStripe: View {
    var faded = false

    var body: some View {
        Canvas { ctx, size in
            let w = 14.0
            var x = -size.height
            var i = 0
            while x < size.width {
                var p = Path()
                p.move(to: CGPoint(x: x, y: size.height))
                p.addLine(to: CGPoint(x: x + size.height, y: 0))
                p.addLine(to: CGPoint(x: x + size.height + w * 0.6, y: 0))
                p.addLine(to: CGPoint(x: x + w * 0.6, y: size.height))
                p.closeSubpath()
                ctx.fill(p, with: .color((i % 2 == 0 ? Sea.wax : Sea.deep).opacity(faded ? 0.35 : 0.9)))
                x += w
                i += 1
            }
        }
        .frame(height: 5)
    }
}

/** Unread marker: a red wax seal. */
struct WaxSeal: View {
    let count: Int

    var body: some View {
        Text(sealText(count))
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(Sea.paper)
            .frame(width: 24, height: 24)
            .background(Circle().fill(RadialGradient(colors: [Color(hex: 0xD0604F), Sea.wax], center: .center, startRadius: 0, endRadius: 12)))
    }
}

/** A round seal with the partner's initial, its colour fixed per conversation. */
struct PartnerSeal: View {
    let name: String
    let key: String
    var faded = false

    private static let tones = [Sea.deep, Sea.teal, Sea.wax, Color(hex: 0x8A6A3B), Color(hex: 0x5B4E8C), Color(hex: 0x3F7D5C)]

    var body: some View {
        let tone = faded ? Sea.paperEdge : Self.tones[Int(stableHash(key) % UInt64(Self.tones.count))]
        Text(String(name.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased())
            .font(.system(size: 18, weight: .bold, design: .serif))
            .foregroundStyle(faded ? Sea.inkText.opacity(0.6) : Sea.paper)
            .frame(width: 44, height: 44)
            .background(Circle().fill(RadialGradient(colors: [tone.opacity(0.75), tone], center: .center, startRadius: 0, endRadius: 22)))
            .overlay(Circle().stroke(Sea.paper.opacity(0.7), lineWidth: 2))
    }
}

/** FNV-1a: String.hashValue changes between launches, the seal colour must not. */
func stableHash(_ s: String) -> UInt64 {
    s.utf8.reduce(14_695_981_039_346_656_037) { ($0 ^ UInt64($1)) &* 1_099_511_628_211 }
}

/** The app's bottle, gently bobbing and tilting. */
struct BobbingBottle: View {
    var size: CGFloat

    var body: some View {
        TimelineView(.animation) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate
            Image("Bottle")
                .resizable()
                .frame(width: size, height: size)
                .offset(y: pingPong(t, period: 1.7, from: -4, to: 4))
                .rotationEffect(.degrees(pingPong(t, period: 2.5, from: -6, to: 6)))
        }
        .accessibilityHidden(true)
    }
}

/** A small night sea with a bottle drifting across it, for "your letter is on its way" (Sea.kt). */
struct DriftingBottleScene: View {
    var height: CGFloat = 112

    var body: some View {
        TimelineView(.animation) { tl in
            let t = tl.date.timeIntervalSinceReferenceDate
            let phase = (t / 5).truncatingRemainder(dividingBy: 1)
            GeometryReader { geo in
                let w = geo.size.width
                ZStack(alignment: .bottomLeading) {
                    Sea.nightGradient
                    Canvas { ctx, size in
                        for (x, y) in [(0.1, 0.2), (0.32, 0.12), (0.58, 0.28), (0.86, 0.16)] {
                            ctx.fill(Path(ellipseIn: CGRect(x: size.width * x - 1.2, y: size.height * y - 1.2, width: 2.4, height: 2.4)),
                                     with: .color(.white.opacity(0.7)))
                        }
                        let moon = CGPoint(x: size.width * 0.86, y: size.height * 0.3)
                        ctx.fill(Path(ellipseIn: CGRect(x: moon.x - 12, y: moon.y - 12, width: 24, height: 24)), with: .color(Sea.moon.opacity(0.9)))
                        ctx.fill(Path(ellipseIn: CGRect(x: moon.x - 5, y: moon.y - 13, width: 20, height: 20)), with: .color(Sea.night))
                    }
                    SeaBand(phase: phase, crest: 0.42, amplitude: 5, waves: 2).fill(Sea.waveBack.opacity(0.7))
                    Image("Bottle")
                        .resizable()
                        .frame(width: height * 0.72, height: height * 0.72)
                        .rotationEffect(.degrees(pingPong(t, period: 2.2, from: -10, to: 10)))
                        .offset(x: w * pingPong(t, period: 9, from: 0.12, to: 0.62),
                                y: pingPong(t, period: 1.5, from: -4, to: 4) - 6)
                    SeaBand(phase: phase + 0.5, crest: 0.24, amplitude: 4, waves: 2.5).fill(Sea.waveFront.opacity(0.6))
                }
            }
        }
        .frame(height: height)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .accessibilityHidden(true)
    }
}

/** Smooth back-and-forth between two values, like Compose's RepeatMode.Reverse with easing. */
func pingPong(_ t: TimeInterval, period: Double, from: Double, to: Double) -> Double {
    let x = (1 - cos(2 * .pi * t / (2 * period))) / 2
    return from + (to - from) * x
}

/** Empty list: a lone bottle and a line of text. */
struct EmptyBottle: View {
    let text: String

    var body: some View {
        VStack(spacing: 16) {
            BobbingBottle(size: 140).opacity(0.9)
            Text(text)
                .font(.system(size: 17, design: .serif))
                .multilineTextAlignment(.center)
                .lineSpacing(6)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/** Ruled writing lines behind text, every lineHeight points. */
struct RuledLines: View {
    var lineHeight: CGFloat = 28
    var color: Color = Sea.rule

    var body: some View {
        Canvas { ctx, size in
            var y = lineHeight - 5
            while y < size.height {
                ctx.fill(Path(CGRect(x: 0, y: y, width: size.width, height: 1)), with: .color(color))
                y += lineHeight
            }
        }
    }
}

/** A row of single-choice chips. */
struct ChoiceChips: View {
    let options: [String]
    @Binding var selection: String
    let label: (String) -> String

    var body: some View {
        FlowRow {
            ForEach(options, id: \.self) { option in
                let selected = option == selection
                Button { selection = option } label: {
                    Text(label(option))
                        .font(.subheadline.weight(selected ? .semibold : .regular))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .foregroundStyle(selected ? Sea.paper : Sea.inkText)
                        .background(Capsule().fill(selected ? Sea.teal : Sea.paper))
                        .overlay(Capsule().stroke(selected ? Sea.teal : Sea.paperEdge))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
    }
}

/** Wraps its children onto new lines when they do not fit. */
struct FlowRow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(subviews, width: proposal.width ?? .infinity)
        return CGSize(width: proposal.width ?? rows.width, height: rows.height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rows = arrange(subviews, width: bounds.width)
        for (i, p) in rows.points.enumerated() {
            subviews[i].place(at: CGPoint(x: bounds.minX + p.x, y: bounds.minY + p.y), proposal: .unspecified)
        }
    }

    private func arrange(_ subviews: Subviews, width: CGFloat) -> (points: [CGPoint], width: CGFloat, height: CGFloat) {
        var points: [CGPoint] = []
        var x = 0.0, y = 0.0, rowHeight = 0.0, maxX = 0.0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > width {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            points.append(CGPoint(x: x, y: y))
            x += size.width + spacing
            maxX = max(maxX, x - spacing)
            rowHeight = max(rowHeight, size.height)
        }
        return (points, maxX, y + rowHeight)
    }
}

/** Bottom message that disappears by itself (Android snackbar). */
struct ToastModifier: ViewModifier {
    @Binding var key: String?

    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            if let key {
                Text(L(key))
                    .font(.callout)
                    .foregroundStyle(Sea.paper)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .background(Capsule().fill(Sea.night.opacity(0.92)))
                    .padding(.horizontal, 24)
                    .padding(.bottom, 90)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .task(id: key) {
                        try? await Task.sleep(for: .seconds(3))
                        withAnimation { self.key = nil }
                    }
            }
        }
        .animation(.easeOut, value: key)
    }
}

extension View {
    func toast(_ key: Binding<String?>) -> some View { modifier(ToastModifier(key: key)) }
}
