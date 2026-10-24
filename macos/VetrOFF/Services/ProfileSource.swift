import Foundation

enum ProfileSourceError: LocalizedError, Equatable {
    case empty
    case tooLarge
    case invalidUTF8
    case invalidJSON
    case unsupportedFormat
    case invalidSubscription
    case invalidLine(Int)

    var errorDescription: String? {
        switch self {
        case .empty: "Пустой профиль."
        case .tooLarge: "Размер профиля превышает 8 МиБ."
        case .invalidUTF8: "Файл должен содержать корректный UTF-8."
        case .invalidJSON: "Неверный формат JSON."
        case .unsupportedFormat: "Поддерживаются JSON, Clash YAML и ссылки серверов из Go-ядра."
        case .invalidSubscription: "Ответ не является конфигурацией: поддерживаются ссылки серверов, Base64, sing-box JSON, Clash YAML и WireGuard/AmneziaWG."
        case .invalidLine(let line): "Неверная строка конфигурации: \(line). Исходный профиль не изменён."
        }
    }
}

enum ProfileSourceKind: String, Codable, Sendable {
    case json
    case yaml
    case proxyURI
    case subscriptionURL
    case wireguard

    var label: String {
        switch self {
        case .json: "JSON"
        case .yaml: "Clash YAML"
        case .proxyURI: "Ссылки серверов"
        case .subscriptionURL: "Подписка"
        case .wireguard: "WireGuard / AmneziaWG"
        }
    }
}

struct ProfileServer: Identifiable, Equatable, Sendable {
    let index: Int
    let scheme: String
    let rawURI: String
    var displayName: String? = nil
    var isSupported: Bool = true

    var selectable: Bool { isSupported }

    var id: Int { index }

    /// UI must never expose host credentials or complete subscription URLs.
    var title: String {
        if let name = SubscriptionMetadata.cleanTitle(displayName) { return "\(name) · \(scheme.uppercased())" }
        if scheme == "olcrtc" {
            // Its fragment contains a secret encryption key before '$'.
            let name = rawURI.split(separator: "$", maxSplits: 1).dropFirst().first.map(String.init)
            return "\(SubscriptionMetadata.cleanTitle(name) ?? "olcRTC") · OLCRTC"
        }
        if ["vmess", "svmess", "xvmess"].contains(scheme),
           let marker = rawURI.range(of: "://"),
           let bytes = ProfileSource.base64(String(rawURI[marker.upperBound...]).components(separatedBy: "#")[0]),
           let json = try? JSONSerialization.jsonObject(with: bytes) as? [String: Any],
           let title = SubscriptionMetadata.cleanTitle(json["ps"] as? String) {
            return "\(title) · \(scheme.uppercased())"
        }
        // URI fragments commonly contain user-friendly names. Never display
        // raw host, credentials or query text as fallback node labels.
        if let marker = rawURI.lastIndex(of: "#") {
            let encoded = String(rawURI[rawURI.index(after: marker)...])
            let decoded = encoded.removingPercentEncoding ?? encoded
            let safe = decoded.trimmingCharacters(in: .whitespacesAndNewlines)
            if !safe.isEmpty && safe.count <= 48 &&
                !safe.contains("://") && !safe.contains("@") &&
                !safe.contains("?") && !safe.contains("&") &&
                !safe.contains("=") && !safe.contains("/") &&
                !safe.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }) {
                return "\(safe) · \(scheme.uppercased())"
            }
        }
        return "Сервер \(index + 1) · \(scheme.uppercased())"
    }
}

enum ProfileSource {
    static let maxBytes = 8 * 1024 * 1024
    static let maxServers = 4_096

    // Display-only URI identification. Actual transport parsing/validation
    // is delegated unchanged to pinned hiddify-core, never recreated in Swift.
    // Mirrors the parsers in pinned hiddify-core panel_links.go/paneluri and
    // ray2sing. Unknown schemes stay visible and are never silently discarded.
    static let serverSchemes: Set<String> = [
        "vless", "vmess", "trojan", "svless", "svmess", "strojan",
        "xvless", "xvmess", "xtrojan", "xdirect", "direct", "ss", "ssconf",
        "hysteria2", "hy2", "hysteria", "hy", "tuic", "naive", "naive+https", "naive+quic",
        "wireguard", "wg", "awg", "vpn", "warp", "anytls", "snell", "mieru", "mierus",
        "ssh", "socks", "socks5", "phttp", "phttps", "http", "https", "psiphon",
        "tt", "trusttunnel", "sudoku", "fptn", "pingtunnel", "openflux", "olcrtc", "dnstt"
    ]

    static func classify(_ source: String) throws -> ProfileSourceKind {
        let text = source.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "\u{FEFF}"))
        guard !text.isEmpty else { throw ProfileSourceError.empty }
        guard source.utf8.count <= maxBytes else { throw ProfileSourceError.tooLarge }

        if text.contains("[Interface]") && text.contains("[Peer]") { return .wireguard }
        let jsonText = ["{", "[", "/"].contains(String(text.prefix(1))) ? stripJSONComments(text) : text
        if jsonText.first == "{" || jsonText.first == "[" {
            guard let bytes = jsonText.data(using: .utf8),
                  let object = try? JSONSerialization.jsonObject(with: bytes),
                  object is [String: Any] || object is [Any]
            else { throw ProfileSourceError.invalidJSON }
            return .json
        }

        // YAML metadata/selection is cataloged natively; protocol conversion
        // remains in Go with the complete selected dependency graph.
        if text.split(separator: "\n").contains(where: {
            let line = $0.trimmingCharacters(in: .whitespaces)
            return line == "proxies:" || line.hasPrefix("proxies: ")
        }) {
            return .yaml
        }

        if let url = URL(string: text),
           ["http", "https"].contains(url.scheme?.lowercased() ?? ""), url.host != nil,
           url.user == nil, !text.contains("\n") { return .subscriptionURL }
        if !uriServers(in: text).isEmpty { return .proxyURI }
        throw ProfileSourceError.unsupportedFormat
    }

    static func servers(in content: String) -> [ProfileServer] {
        if let catalog = ProfileJSONCatalog.servers(content) { return catalog }
        if let catalog = ProfileYAMLCatalog.servers(content) { return catalog }
        if content.contains("[Interface]") && content.contains("[Peer]") {
            return [ProfileServer(index: 0, scheme: "wireguard", rawURI: content, displayName: "WireGuard / AmneziaWG")]
        }
        return uriServers(in: content)
    }

    private static func uriServers(in content: String) -> [ProfileServer] {
        let lines = content.split(separator: "\n").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !$0.hasPrefix("#") && !$0.hasPrefix("//") }
        guard !lines.isEmpty, lines.count <= maxServers else { return [] }
        var result: [ProfileServer] = []
        for line in lines {
            // A complete chain is an indivisible selection; preserve all hops.
            let hops = line.components(separatedBy: " -> ")
            let schemes = hops.compactMap { hop -> String? in
                guard let colon = hop.firstIndex(of: ":") else { return nil }
                let scheme = String(hop[..<colon]).lowercased()
                guard !scheme.isEmpty, scheme.count <= 32,
                      scheme.first?.isLetter == true,
                      scheme.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || "+-.".contains($0)) }),
                      hop.contains("://") || scheme == "fptn" else { return nil }
                return scheme
            }
            guard schemes.count == hops.count else { return [] }
            let scheme = schemes.first!
            result.append(ProfileServer(index: result.count, scheme: scheme, rawURI: line,
                isSupported: schemes.allSatisfy { serverSchemes.contains($0) }))
        }
        return result
    }

    /// Refreshes may reorder nodes. Preserve the actual selected URI, not
    /// an array position that would silently switch the VPN to a new server.
    static func preservingSelection(
        in servers: [ProfileServer], previousURI: String?
    ) -> Int? {
        guard !servers.isEmpty else { return nil }
        guard let previousURI else { return servers.firstIndex(where: \.selectable) }
        return servers.firstIndex(where: { $0.rawURI == previousURI && $0.selectable }) ?? servers.firstIndex(where: \.selectable)
    }

    static func decodeSubscription(_ data: Data) throws -> String {
        let content = try decodeUTF8(data)
        if let kind = try? classify(content), kind != .subscriptionURL {
            return content
        }
        // Most share-link subscriptions are Base64 encoded text lists.
        // Decode only if the resulting payload is recognizable; do not
        // silently reinterpret malformed data as one arbitrary URI.
        guard let decoded = base64(content),
              let plaintext = try? decodeUTF8(decoded),
              let kind = try? classify(plaintext), kind != .subscriptionURL
        else { throw ProfileSourceError.invalidSubscription }
        return plaintext
    }

    static func base64(_ input: String) -> Data? {
        var text = input.components(separatedBy: .whitespacesAndNewlines).joined()
            .replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        let alphabet = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=")
        guard !text.isEmpty, text.utf8.count <= maxBytes * 2, text.count % 4 != 1,
              text.unicodeScalars.allSatisfy({ alphabet.contains($0) }) else { return nil }
        text += String(repeating: "=", count: (4 - text.count % 4) % 4)
        return Data(base64Encoded: text)
    }

    /// JSONC comments are permitted by the Go parser. Keep strings and line
    /// boundaries intact; never treat https:// or escaped quotes as comments.
    static func stripJSONComments(_ input: String) -> String {
        let chars = Array(input)
        var out = "", quoted = false, escaped = false, line = false, block = false, i = 0
        while i < chars.count {
            let c = chars[i], next: Character? = i + 1 < chars.count ? chars[i + 1] : nil
            if line { if c == "\n" { line = false; out.append(c) }; i += 1; continue }
            if block {
                if c == "*" && next == "/" { block = false; i += 2 }
                else { if c == "\n" { out.append(c) }; i += 1 }
                continue
            }
            if quoted {
                out.append(c)
                if escaped { escaped = false }
                else if c == "\\" { escaped = true }
                else if c == "\"" { quoted = false }
            } else if c == "\"" { quoted = true; out.append(c) }
            else if c == "/" && next == "/" { line = true; out.append(" "); i += 2; continue }
            else if c == "/" && next == "*" { block = true; out.append(" "); i += 2; continue }
            else { out.append(c) }
            i += 1
        }
        return out.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Avoid rewriting the shared Keychain for a byte-identical downloaded config.
    /// A successful freshness check can update metadata without touching tunnel secrets.
    static func subscriptionContentChanged(previous: String?, downloaded: String) -> Bool {
        previous != downloaded
    }

    static func decodeUTF8(_ data: Data) throws -> String {
        guard data.count <= maxBytes else { throw ProfileSourceError.tooLarge }
        guard let text = String(data: data, encoding: .utf8) else {
            throw ProfileSourceError.invalidUTF8
        }
        return text.hasPrefix("\u{FEFF}") ? String(text.dropFirst()) : text
    }
}
