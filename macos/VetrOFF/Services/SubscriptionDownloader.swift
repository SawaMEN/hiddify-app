import Foundation

enum SubscriptionDownloadError: LocalizedError, Equatable {
    case insecureURL
    case privateHost
    case invalidResponse
    case httpStatus(Int)
    case oversized

    var errorDescription: String? {
        switch self {
        case .insecureURL: "Подписка должна использовать HTTPS без логина в URL."
        case .privateHost: "Адреса локальной сети и localhost запрещены для подписок."
        case .invalidResponse: "Сервер подписки вернул некорректный ответ."
        case .httpStatus(let status): "Сервер подписки ответил HTTP \(status)."
        case .oversized: "Подписка превышает допустимые 8 МиБ."
        }
    }
}

private final class SubscriptionRedirectGuard: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        guard let url = request.url,
              (try? SubscriptionDownloader.validate(url)) != nil,
              url.host?.lowercased() == task.originalRequest?.url?.host?.lowercased()
        else {
            completionHandler(nil)
            return
        }
        completionHandler(request)
    }
}

enum SubscriptionDownloader {
    /// Reject cleartext HTTP, credentials embedded in URLs, loopback and
    /// IP literals. HTTPS certificate verification remains the OS default.
    /// This URL precheck is not a DNS-rebinding-proof network firewall.
    static func validate(_ url: URL) throws {
        guard url.scheme?.lowercased() == "https",
              url.user == nil, url.password == nil,
              url.fragment == nil, let host = url.host, !host.isEmpty
        else { throw SubscriptionDownloadError.insecureURL }

        let normalized = host.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
        if normalized == "localhost" || normalized == "localhost.localdomain" ||
            normalized.hasSuffix(".localhost") || normalized.hasSuffix(".local") ||
            normalized.hasSuffix(".internal") || normalized.hasSuffix(".lan") ||
            normalized == "0.0.0.0" || normalized.contains(":") {
            throw SubscriptionDownloadError.privateHost
        }

        // Block canonical and abbreviated numeric IPv4 hosts such as
        // 127.1 and 2130706433, which CFNetwork may interpret as loopback.
        if normalized.allSatisfy({ $0.isNumber || $0 == "." }) ||
            normalized.hasPrefix("0x") {
            throw SubscriptionDownloadError.privateHost
        }
    }

    static func fetch(_ source: String) async throws -> String {
        guard let url = URL(string: source.trimmingCharacters(in: .whitespacesAndNewlines))
        else { throw SubscriptionDownloadError.insecureURL }
        try validate(url)
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 15
        configuration.timeoutIntervalForResource = 30
        configuration.httpShouldSetCookies = false
        configuration.httpCookieAcceptPolicy = .never
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData

        let session = URLSession(
            configuration: configuration, delegate: SubscriptionRedirectGuard(),
            delegateQueue: nil
        )
        defer { session.invalidateAndCancel() }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("VetrOFF-macOS/0.1", forHTTPHeaderField: "User-Agent")
        request.setValue("application/json, text/yaml, text/plain, */*", forHTTPHeaderField: "Accept")

        // A download task streams the response into a temporary file,
        // avoiding unbounded in-memory accumulation before the 8 MiB check.
        let (temporary, response) = try await session.download(for: request)
        guard let http = response as? HTTPURLResponse,
              let finalURL = http.url
        else { throw SubscriptionDownloadError.invalidResponse }
        try validate(finalURL)
        guard http.statusCode == 200 else {
            throw SubscriptionDownloadError.httpStatus(http.statusCode)
        }
        let attributes = try FileManager.default.attributesOfItem(atPath: temporary.path)
        guard let size = attributes[.size] as? NSNumber,
              size.intValue <= ProfileSource.maxBytes
        else { throw SubscriptionDownloadError.oversized }

        let data = try Data(contentsOf: temporary)
        return try ProfileSource.decodeSubscription(data)
    }
}
