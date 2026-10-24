import Foundation

/// Catalogs configuration documents without translating protocols. Effective
/// JSON retains dialer dependencies and is parsed/validated by the same Go core.
enum ProfileJSONCatalog {
    private static let utility: Set<String> = ["direct", "block", "dns", "selector", "urltest", "url-test", "reject"]
    private static let nativeTypes: Set<String> = ProfileSource.serverSchemes.union([
        "shadowsocks", "shadowtls", "wireguard_legacy", "hysteria2_legacy", "http", "socks",
        "masque", "masque-client", "tailscale", "tailcat", "cloudflared", "openvpn", "openconnect",
        "hiddify", "tunnel_client", "tunnel_server"
    ])

    static func servers(_ content: String) -> [ProfileServer]? {
        let prefix = content.trimmingCharacters(in: .whitespacesAndNewlines).prefix(1)
        guard ["{", "[", "/"].contains(prefix),
              let bytes = ProfileSource.stripJSONComments(content).data(using: .utf8),
              let value = try? JSONSerialization.jsonObject(with: bytes) else { return nil }
        if let array = value as? [[String: Any]] {
            guard array.count <= ProfileSource.maxServers else { return [] }
            return array.enumerated().map { index, document in
                let type = document["type"] as? String ?? "json"
                return ProfileServer(index: index, scheme: type, rawURI: encode(document) ?? content,
                    displayName: name(document) ?? "Конфигурация \(index + 1)",
                    isSupported: type == "json" || nativeTypes.contains(type))
            }
        }
        guard let document = value as? [String: Any] else { return [] }
        let outbounds = document["outbounds"] as? [[String: Any]] ?? []
        let endpoints = document["endpoints"] as? [[String: Any]] ?? []
        let all = outbounds + endpoints
        if all.isEmpty {
            let type = document["type"] as? String ?? "json"
            return [ProfileServer(index: 0, scheme: type, rawURI: content,
                displayName: name(document), isSupported: nativeTypes.contains(type))]
        }
        let nodes = all.filter { !utility.contains(($0["type"] as? String ?? "").lowercased()) }
        guard nodes.count <= ProfileSource.maxServers else { return [] }
        return nodes.enumerated().map { index, node in
            let type = node["type"] as? String ?? "json"
            let effective = isolate(node: node, document: document, all: all)
            return ProfileServer(index: index, scheme: type, rawURI: effective ?? content,
                displayName: name(node) ?? "Сервер \(index + 1)",
                isSupported: nativeTypes.contains(type) && effective != nil)
        }
    }

    private static func name(_ node: [String: Any]) -> String? {
        for key in ["remarks", "name", "ps", "tag"] {
            if let name = SubscriptionMetadata.cleanTitle(node[key] as? String) { return name }
        }
        return nil
    }

    private static func isolate(node: [String: Any], document: [String: Any], all: [[String: Any]]) -> String? {
        var included: [[String: Any]] = [node]
        var visited = Set<String>()
        if let tag = node["tag"] as? String { visited.insert(tag) }
        var cursor = 0
        while cursor < included.count {
            guard included.count <= ProfileSource.maxServers else { return nil }
            let item = included[cursor]
            for key in ["detour", "dialer-proxy"] {
                if let tag = item[key] as? String, !visited.contains(tag) {
                    guard let dependency = all.first(where: { $0["tag"] as? String == tag }),
                          !["selector", "urltest", "url-test"].contains(dependency["type"] as? String ?? "") else { return nil }
                    visited.insert(tag); included.append(dependency)
                }
            }
            cursor += 1
        }
        // Hidden dependencies must remain dialable without becoming extra
        // candidates in the core's automatic balancer.
        var mapping: [String: String] = [:]
        for dependency in included.dropFirst() {
            if let tag = dependency["tag"] as? String { mapping[tag] = "\(tag) §hide§" }
        }
        for index in included.indices {
            if index > 0, let tag = included[index]["tag"] as? String { included[index]["tag"] = mapping[tag] }
            for key in ["detour", "dialer-proxy"] {
                if let tag = included[index][key] as? String, let replacement = mapping[tag] { included[index][key] = replacement }
            }
        }
        // The packet engine owns macOS TUN/DNS/routes. A node selection
        // contains its dependency graph, not rules referencing removed nodes.
        var result: [String: Any] = [:]
        let endpointTypes: Set<String> = ["wireguard", "awg", "masque-client", "tailscale"]
        result["outbounds"] = included.filter { !endpointTypes.contains($0["type"] as? String ?? "") }
        result["endpoints"] = included.filter { endpointTypes.contains($0["type"] as? String ?? "") }
        return encode(result)
    }

    private static func encode(_ object: [String: Any]) -> String? {
        guard let bytes = try? JSONSerialization.data(withJSONObject: object, options: [.sortedKeys]),
              let text = String(data: bytes, encoding: .utf8) else { return nil }
        return text
    }
}
