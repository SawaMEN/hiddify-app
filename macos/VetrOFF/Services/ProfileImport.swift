import Foundation

/// Accept links produced by the existing VetrOFF/Hiddify subscription flow.
/// The secret URL is never stored in preferences, diagnostics or logs.
enum ProfileImport {
    static func normalize(_ input: String) throws -> String {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard text.utf8.count <= ProfileSource.maxBytes else { throw ProfileSourceError.tooLarge }
        if let url = URL(string: text),
           let scheme = url.scheme?.lowercased(),
           ["hiddify", "vetroff"].contains(scheme) {
            if url.host?.lowercased() == "import", let components = URLComponents(string: text),
               let nested = components.queryItems?.first(where: { $0.name == "url" })?.value {
                return try subscriptionURL(nested)
            }
            let prefix = "\(scheme)://import/"
            guard text.lowercased().hasPrefix(prefix) else { throw ProfileSourceError.unsupportedFormat }
            let payload = String(text.dropFirst(prefix.count))
            // A raw nested URL must retain its percent-encoded query token.
            let source = payload.lowercased().hasPrefix("https://") || payload.lowercased().hasPrefix("http://")
                ? payload : (payload.removingPercentEncoding ?? payload)
            return try subscriptionURL(source)
        }
        if let kind = try? ProfileSource.classify(text) {
            return kind == .subscriptionURL ? try subscriptionURL(text) : text
        }
        return try ProfileSource.decodeSubscription(Data(text.utf8))
    }

    static func suggestedName(_ input: String) -> String? {
        if let components = URLComponents(string: input),
           ["hiddify", "vetroff"].contains(components.scheme?.lowercased() ?? ""),
           let name = components.queryItems?.first(where: { $0.name == "name" })?.value,
           let safe = SubscriptionMetadata.cleanTitle(name) { return safe }
        if let url = URL(string: input),
           ["http", "https", "hiddify", "vetroff"].contains(url.scheme?.lowercased() ?? ""),
           let fragment = url.fragment?.removingPercentEncoding {
            return SubscriptionMetadata.cleanTitle(fragment)
        }
        return nil
    }

    private static func subscriptionURL(_ source: String) throws -> String {
        // Share links may append a display name as #fragment. Fragments are
        // not request data; remove only that suffix, preserving encoded tokens.
        let address = String(source.prefix { $0 != "#" })
        guard let url = URL(string: address) else { throw ProfileSourceError.invalidSubscription }
        try SubscriptionDownloader.validate(url)
        return address
    }
}
