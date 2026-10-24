import SwiftUI

enum ClientTheme {
    static let accent = Color(red: 0.12, green: 0.83, blue: 0.97)
    static let mint = Color(red: 0.46, green: 0.96, blue: 0.77)
    static let violet = Color(red: 0.59, green: 0.40, blue: 0.98)
    static let background = Color(red: 0.025, green: 0.043, blue: 0.078)
    static let surface = Color(red: 0.055, green: 0.09, blue: 0.145)
}

struct ClientCanvas: View {
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        ZStack {
            scheme == .dark ? ClientTheme.background : Color(red: 0.94, green: 0.97, blue: 0.99)
            RadialGradient(colors: [ClientTheme.accent.opacity(scheme == .dark ? 0.13 : 0.09), .clear],
                           center: .topTrailing, startRadius: 20, endRadius: 700)
            RadialGradient(colors: [ClientTheme.violet.opacity(0.10), .clear],
                           center: .bottomLeading, startRadius: 0, endRadius: 550)
        }.ignoresSafeArea()
    }
}

struct ClientCard<Content: View>: View {
    @Environment(\.colorScheme) private var scheme
    @ViewBuilder var content: Content
    var body: some View {
        content.padding(22).frame(maxWidth: .infinity, alignment: .leading)
            .background {
                RoundedRectangle(cornerRadius: 24)
                    .fill(scheme == .dark ? ClientTheme.surface.opacity(0.86) : Color.white.opacity(0.9))
                    .overlay(alignment: .topLeading) {
                        RoundedRectangle(cornerRadius: 24).fill(LinearGradient(
                            colors: [ClientTheme.accent.opacity(0.055), .clear, ClientTheme.violet.opacity(0.045)],
                            startPoint: .topLeading, endPoint: .bottomTrailing))
                    }
            }
            .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(LinearGradient(
                colors: [ClientTheme.accent.opacity(0.25), .primary.opacity(0.05), ClientTheme.violet.opacity(0.17)],
                startPoint: .topLeading, endPoint: .bottomTrailing)))
            .shadow(color: .black.opacity(scheme == .dark ? 0.14 : 0.035), radius: 18, y: 8)
    }
}

struct ClientBadge: View {
    let symbol: String
    var color: Color = ClientTheme.accent
    var body: some View {
        Image(systemName: symbol).font(.system(size: 19, weight: .semibold))
            .foregroundStyle(color).frame(width: 44, height: 44)
            .background(color.opacity(0.10), in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(color.opacity(0.17)))
            .accessibilityHidden(true)
    }
}

struct ClientPrimaryButton: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.system(size: 13, weight: .bold, design: .rounded))
            .padding(.horizontal, 20).padding(.vertical, 12)
            .foregroundStyle(Color(red: 0.015, green: 0.10, blue: 0.15))
            .background(LinearGradient(colors: [ClientTheme.mint, ClientTheme.accent],
                                      startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: 14))
            .opacity(configuration.isPressed ? 0.75 : 1)
    }
}

struct ConnectionArtwork: View {
    let connected: Bool
    let busy: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            ZStack {
                Circle().fill(RadialGradient(colors: [ClientTheme.accent.opacity(0.16), .clear],
                                             center: .center, startRadius: 25, endRadius: 110))
                Circle().strokeBorder(ClientTheme.accent.opacity(0.10), lineWidth: 1).padding(3)
                Circle().strokeBorder(AngularGradient(colors: [.clear, ClientTheme.accent.opacity(0.75),
                    ClientTheme.violet.opacity(0.55), .clear], center: .center), lineWidth: 2).padding(18)
                Image("BrandMark").resizable().scaledToFit().frame(width: 128, height: 128)
                    .shadow(color: ClientTheme.accent.opacity(0.18), radius: 20)
                Image(systemName: "power").font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(connected ? ClientTheme.mint : ClientTheme.accent)
                    .frame(width: 40, height: 40).background(ClientTheme.surface, in: Circle())
                    .overlay(Circle().strokeBorder(ClientTheme.accent.opacity(0.5)))
                    .offset(x: 60, y: 60)
            }.frame(width: 208, height: 208).contentShape(Circle())
        }.buttonStyle(.plain).disabled(busy)
        .accessibilityLabel(connected ? "Отключить VPN" : "Подключить VPN")
    }
}
