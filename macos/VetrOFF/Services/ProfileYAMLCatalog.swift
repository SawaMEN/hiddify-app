import Foundation
import Yams

enum ProfileYAMLCatalog {
    static func servers(_ content: String) -> [ProfileServer]? {
        guard content.contains("proxies:"), !content.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("{"),
              let root = try? Yams.load(yaml: content) as? [String: Any],
              let nodes = root["proxies"] as? [[String: Any]],
              nodes.count <= ProfileSource.maxServers else { return nil }
        return nodes.enumerated().map { index, node in
            var included = [node]
            var visited = Set<String>()
            if let name = node["name"] as? String { visited.insert(name) }
            var cursor = 0, valid = true
            while cursor < included.count {
                if let tag = included[cursor]["dialer-proxy"] as? String, !visited.contains(tag) {
                    if let dependency = nodes.first(where: { $0["name"] as? String == tag }) {
                        included.append(dependency); visited.insert(tag)
                    } else { valid = false; break }
                }
                cursor += 1
            }
            var mapping: [String: String] = [:]
            for item in included.dropFirst() {
                if let name = item["name"] as? String { mapping[name] = "\(name) §hide§" }
            }
            for i in included.indices {
                if i > 0, let name = included[i]["name"] as? String { included[i]["name"] = mapping[name] }
                if let name = included[i]["dialer-proxy"] as? String, let replacement = mapping[name] {
                    included[i]["dialer-proxy"] = replacement
                }
            }
            var selected = root
            selected["proxies"] = included
            // Group references to unselected nodes cannot survive selection.
            selected.removeValue(forKey: "proxy-groups")
            selected.removeValue(forKey: "rules")
            let effective = try? Yams.dump(object: selected, sortKeys: true)
            return ProfileServer(index: index, scheme: node["type"] as? String ?? "yaml",
                rawURI: effective ?? content, displayName: node["name"] as? String,
                isSupported: valid && effective != nil)
        }
    }
}
