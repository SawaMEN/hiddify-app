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
            let prefix = "\(scheme)://import/"
            guard text.lowercased().hasPrefix(prefix) else { throw ProfileSourceError.unsupportedFormat }
            let payload = String(text.dropFirst(prefix.count))
            // A raw nested URL must retain its percent-encoded query token.
            let source = payload.lowercased().hasPrefix("https://")
                ? payload : (payload.removingPercentEncoding ?? payload)
            guard let target = URL(string: source) else { throw ProfileSourceError.invalidSubscription }
            try SubscriptionDownloader.validate(target)
            return source
        }
        if (try? ProfileSource.classify(text)) != nil { return text }
        return try ProfileSource.decodeSubscription(Data(text.utf8))
    }
}
