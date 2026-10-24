import Foundation

struct SubscriptionMetadata: Codable, Hashable, Sendable {
    var title: String?
    var upload: Int64?
    var download: Int64?
    var total: Int64?
    var expiresAt: Date?
    var updateHours: Int?
    // Links are kept in Keychain with the content, not preferences: some
    // providers put authentication parameters even in support/web links.
    var website: String?
    var support: String?

    var usedBytes: Int64? {
        guard upload != nil || download != nil else { return nil }
        let (value, overflow) = (upload ?? 0).addingReportingOverflow(download ?? 0)
        return overflow ? Int64.max : value
    }

    static func parse(headers: [String: String] = [:], content: String = "") -> Self {
        let allowed = Set(["profile-title", "content-disposition", "subscription-userinfo",
                           "profile-update-interval", "profile-web-page-url", "support-url"])
        var fields: [String: String] = [:]
        for (key, value) in headers where allowed.contains(key.lowercased()) {
            fields[key.lowercased()] = value
        }
        for original in content.split(separator: "\n").prefix(32) {
            var line = original.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.hasPrefix("#") { line.removeFirst() }
            else if line.hasPrefix("//") { line.removeFirst(2) }
            else { continue }
            guard let colon = line.firstIndex(of: ":") else { continue }
            let key = line[..<colon].trimmingCharacters(in: .whitespaces).lowercased()
            if allowed.contains(key), fields[key] == nil {
                fields[key] = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
            }
        }
        var usage: [String: Int64] = [:]
        for part in (fields["subscription-userinfo"] ?? "").split(separator: ";") {
            let pair = part.split(separator: "=", maxSplits: 1)
            if pair.count == 2, let number = Int64(pair[1].trimmingCharacters(in: .whitespaces)), number >= 0 {
                usage[pair[0].trimmingCharacters(in: .whitespaces).lowercased()] = number
            }
        }
        let interval = fields["profile-update-interval"].flatMap { Int($0.trimmingCharacters(in: .whitespaces)) }
        return Self(
            title: decodeTitle(fields["profile-title"]) ?? dispositionTitle(fields["content-disposition"]),
            upload: usage["upload"], download: usage["download"], total: usage["total"],
            expiresAt: usage["expire"].flatMap { $0 > 0 && $0 <= 253_402_300_799 ? Date(timeIntervalSince1970: Double($0)) : nil },
            updateHours: interval.flatMap { (1...720).contains($0) ? $0 : nil },
            website: webLink(fields["profile-web-page-url"]), support: webLink(fields["support-url"])
        )
    }

    static func cleanTitle(_ input: String?) -> String? {
        guard let input else { return nil }
        let value = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, value.count <= 200,
              !value.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) }),
              !value.contains("://") else { return nil }
        return value
    }

    static func decodeTitle(_ input: String?) -> String? {
        guard let raw = input?.trimmingCharacters(in: .whitespacesAndNewlines) else { return nil }
        if raw.lowercased().hasPrefix("base64:") {
            guard let data = ProfileSource.base64(String(raw.dropFirst(7))),
                  let text = String(data: data, encoding: .utf8) else { return nil }
            return cleanTitle(text)
        }
        return cleanTitle(raw)
    }

    private static func dispositionTitle(_ input: String?) -> String? {
        guard let input else { return nil }
        let fields = input.split(separator: ";").dropFirst().map { $0.trimmingCharacters(in: .whitespaces) }
        if let field = fields.first(where: { $0.lowercased().hasPrefix("filename*=") }),
           let marker = field.range(of: "''") {
            return cleanTitle(String(field[marker.upperBound...]).removingPercentEncoding)
        }
        if let field = fields.first(where: { $0.lowercased().hasPrefix("filename=") }) {
            let name = String(field.dropFirst(9)).trimmingCharacters(in: CharacterSet(charactersIn: "\"'"))
            return cleanTitle(name)
        }
        return nil
    }

    private static func webLink(_ input: String?) -> String? {
        guard let input, input.count <= 2048, let url = URL(string: input),
              url.scheme?.lowercased() == "https", url.host != nil, url.user == nil, url.password == nil else { return nil }
        return input
    }
}
