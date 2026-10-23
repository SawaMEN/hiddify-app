import SwiftUI

/// Native controls retain keyboard and VoiceOver behavior; the palette and
/// original artwork carry the same VetrOFF identity as the existing client.
enum ClientTheme {
    static let accent = Color(red: 0.15, green: 0.79, blue: 0.93)
    static let mint = Color(red: 0.49, green: 0.90, blue: 0.82)
    static let background = Color(red: 0.035, green: 0.071, blue: 0.098)
    static let surface = Color(red: 0.09, green: 0.14, blue: 0.18)
}

struct ClientCard<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        content.padding(20).frame(maxWidth: .infinity, alignment: .leading)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22))
            .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(.primary.opacity(0.08)))
    }
}

struct ClientBadge: View {
    let symbol: String
    var color: Color = ClientTheme.accent
    var body: some View {
        Image(systemName: symbol).font(.system(size: 19, weight: .semibold))
            .foregroundStyle(color).frame(width: 44, height: 44)
            .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 14))
            .accessibilityHidden(true)
    }
}
