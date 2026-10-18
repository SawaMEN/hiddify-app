import Foundation

enum ProfileSourceError: LocalizedError, Equatable {
    case empty
    case tooLarge
    case invalidUTF8
    case invalidJSON
    case unsupportedFormat

    var errorDescription: String? {
        switch self {
        case .empty: "Пустой профиль."
        case .tooLarge: "Размер профиля превышает 8 МиБ."
        case .invalidUTF8: "Файл должен содержать корректный UTF-8."
        case .invalidJSON: "Неверный формат JSON."
        case .unsupportedFormat: "Пока поддерживается сохранение JSON, URI профилей и URL подписок."
        }
    }
}

enum ProfileSourceKind: String, Codable, Sendable {
    case json
    case proxyURI
    case subscriptionURL

    var label: String {
        switch self {
        case .json: "JSON"
        case .proxyURI: "Ссылка на сервер"
        case .subscriptionURL: "URL подписки"
        }
    }
}

enum ProfileSource {
    static let maxBytes = 8 * 1024 * 1024

    /// Validation here is intentionally format-only, not a claim the VPN
    /// engine can connect using the source yet. Raw source is kept unchanged.
    static func classify(_ source: String) throws -> ProfileSourceKind {
        let input = source.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !input.isEmpty else { throw ProfileSourceError.empty }
        guard source.utf8.count <= maxBytes else { throw ProfileSourceError.tooLarge }

        let text = input.trimmingCharacters(in: CharacterSet(charactersIn: "\u{FEFF}"))
        if text.first == "{" || text.first == "[" {
            guard let bytes = text.data(using: .utf8),
                  let object = try? JSONSerialization.jsonObject(with: bytes),
                  object is [String: Any] || object is [Any]
            else { throw ProfileSourceError.invalidJSON }
            return .json
        }

        let lower = text.lowercased()
        let serverSchemes = [
            "vless://", "vmess://", "trojan://", "ss://", "ssr://",
            "hysteria2://", "hy2://", "hysteria://", "tuic://",
            "naive://", "naive+https://", "naive+quic://",
            "wireguard://", "wg://", "awg://", "anytls://",
            "ssh://", "trusttunnel://", "sudoku://",
            "pingtunnel://", "openflux://"
        ]
        if serverSchemes.contains(where: { lower.hasPrefix($0) }) {
            return .proxyURI
        }
        if lower.hasPrefix("https://") || lower.hasPrefix("http://") {
            guard let url = URL(string: text),
                  url.host != nil
            else { throw ProfileSourceError.unsupportedFormat }
            return .subscriptionURL
        }
        throw ProfileSourceError.unsupportedFormat
    }

    static func decodeUTF8(_ data: Data) throws -> String {
        guard data.count <= maxBytes else { throw ProfileSourceError.tooLarge }
        guard let result = String(data: data, encoding: .utf8) else {
            throw ProfileSourceError.invalidUTF8
        }
        return result
    }
}
