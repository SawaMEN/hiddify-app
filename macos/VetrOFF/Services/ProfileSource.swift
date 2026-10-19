import Foundation

enum ProfileSourceError: LocalizedError, Equatable {
    case empty
    case tooLarge
    case invalidUTF8
    case invalidJSON
    case unsupportedFormat
    case invalidSubscription

    var errorDescription: String? {
        switch self {
        case .empty: "Пустой профиль."
        case .tooLarge: "Размер профиля превышает 8 МиБ."
        case .invalidUTF8: "Файл должен содержать корректный UTF-8."
        case .invalidJSON: "Неверный формат JSON."
        case .unsupportedFormat: "Поддерживаются JSON, Clash YAML и ссылки серверов из Go-ядра."
        case .invalidSubscription: "Подписка не содержит поддерживаемой конфигурации."
        }
    }
}

enum ProfileSourceKind: String, Codable, Sendable {
    case json
    case yaml
    case proxyURI
    case subscriptionURL

    var label: String {
        switch self {
        case .json: "JSON"
        case .yaml: "Clash YAML"
        case .proxyURI: "Ссылки серверов"
        case .subscriptionURL: "URL подписки"
        }
    }
}

struct ProfileServer: Identifiable, Equatable, Sendable {
    let index: Int
    let scheme: String
    let rawURI: String

    var id: Int { index }

    /// UI must never expose host credentials or complete subscription URLs.
    var title: String { "Сервер \(index + 1) · \(scheme.uppercased())" }
}

enum ProfileSource {
    static let maxBytes = 8 * 1024 * 1024
    static let maxServers = 4_096

    // Display-only URI identification. Actual transport parsing/validation
    // is delegated unchanged to pinned hiddify-core, never recreated in Swift.
    private static let serverSchemes: Set<String> = [
        "vless", "vmess", "trojan", "ss", "ssr",
        "hysteria2", "hy2", "hysteria", "hy", "tuic",
        "naive", "naive+https", "naive+quic",
        "wireguard", "wg", "awg", "warp", "anytls",
        "ssh", "socks", "socks5", "trusttunnel", "sudoku",
        "pingtunnel", "openflux", "olcrtc", "dnstt"
    ]

    static func classify(_ source: String) throws -> ProfileSourceKind {
        let text = source.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "\u{FEFF}"))
        guard !text.isEmpty else { throw ProfileSourceError.empty }
        guard source.utf8.count <= maxBytes else { throw ProfileSourceError.tooLarge }

        if text.first == "{" || text.first == "[" {
            guard let bytes = text.data(using: .utf8),
                  let object = try? JSONSerialization.jsonObject(with: bytes),
                  object is [String: Any] || object is [Any]
            else { throw ProfileSourceError.invalidJSON }
            return .json
        }

        // Clash YAML is parsed by the Go core; Swift does not select its
        // outbounds independently because dependencies would be lost.
        if text.split(separator: "\n").contains(where: {
            let line = $0.trimmingCharacters(in: .whitespaces)
            return line == "proxies:" || line.hasPrefix("proxies: ")
        }) {
            return .yaml
        }

        if !servers(in: text).isEmpty {
            return .proxyURI
        }

        if let url = URL(string: text),
           url.scheme?.lowercased() == "https", url.host != nil,
           !text.contains("\n") {
            return .subscriptionURL
        }
        throw ProfileSourceError.unsupportedFormat
    }

    static func servers(in content: String) -> [ProfileServer] {
        let lines = content.split(separator: "\n", omittingEmptySubsequences: false)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !$0.hasPrefix("#") && !$0.hasPrefix("//") }
        guard !lines.isEmpty, lines.count <= maxServers else { return [] }
        var result: [ProfileServer] = []
        for line in lines {
            guard let schemeEnd = line.range(of: "://"),
                  !line.contains("\r") else { return [] }
            let scheme = String(line[..<schemeEnd.lowerBound]).lowercased()
            guard serverSchemes.contains(scheme) else { return [] }
            result.append(ProfileServer(index: result.count, scheme: scheme, rawURI: line))
        }
        return result
    }

    static func decodeSubscription(_ data: Data) throws -> String {
        let content = try decodeUTF8(data)
        if let kind = try? classify(content), kind != .subscriptionURL {
            return content
        }
        // Most share-link subscriptions are Base64 encoded text lists.
        // Decode only if the resulting payload is recognizable; do not
        // silently reinterpret malformed data as one arbitrary URI.
        let compact = content.components(separatedBy: .whitespacesAndNewlines).joined()
        guard compact.utf8.count <= maxBytes * 2,
              let decoded = Data(base64Encoded: compact, options: .ignoreUnknownCharacters),
              let plaintext = try? decodeUTF8(decoded),
              let kind = try? classify(plaintext), kind != .subscriptionURL
        else { throw ProfileSourceError.invalidSubscription }
        return plaintext
    }

    static func decodeUTF8(_ data: Data) throws -> String {
        guard data.count <= maxBytes else { throw ProfileSourceError.tooLarge }
        guard let text = String(data: data, encoding: .utf8) else {
            throw ProfileSourceError.invalidUTF8
        }
        return text
    }
}
