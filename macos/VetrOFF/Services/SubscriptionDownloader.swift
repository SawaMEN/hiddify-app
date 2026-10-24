import Foundation
import Darwin

enum SubscriptionDownloadError: LocalizedError, Equatable {
    case insecureURL, privateHost, invalidResponse, httpStatus(Int), oversized, nestedLimit
    var errorDescription: String? {
        switch self {
        case .insecureURL: "Нужен корректный HTTP/HTTPS-адрес подписки без логина в URL."
        case .privateHost: "Адрес подписки ведёт на localhost или в локальную сеть."
        case .invalidResponse: "Вместо конфигурации сервер вернул страницу сайта или некорректный ответ. Проверьте ссылку подписки."
        case .httpStatus(let status): "Сервер подписки ответил HTTP \(status)."
        case .oversized: "Подписка превышает допустимые 8 МиБ."
        case .nestedLimit: "Превышен предел вложенных подписок (16 адресов, глубина 2) или обнаружен цикл."
        }
    }
}

struct SubscriptionPayload: Sendable {
    let content: String
    let metadata: SubscriptionMetadata
}

private final class SubscriptionRedirectGuard: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
        guard let url = request.url, (try? SubscriptionDownloader.validate(url)) != nil,
              !(response.url?.scheme == "https" && url.scheme == "http") else {
            completionHandler(nil); return
        }
        completionHandler(request)
    }
}

enum SubscriptionDownloader {
    static func validate(_ url: URL) throws {
        guard ["https", "http"].contains(url.scheme?.lowercased() ?? ""),
              url.user == nil, url.password == nil, url.fragment == nil,
              let host = url.host, !host.isEmpty,
              url.port.map({ (1...65535).contains($0) }) ?? true else { throw SubscriptionDownloadError.insecureURL }
        let name = host.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: ".[]"))
        if name == "localhost" || name == "localhost.localdomain" || name.hasSuffix(".localhost") ||
            name.hasSuffix(".local") || name.hasSuffix(".internal") || name.hasSuffix(".lan") {
            throw SubscriptionDownloadError.privateHost
        }
        if name.contains(":") {
            var address = in6_addr()
            guard inet_pton(AF_INET6, name, &address) == 1 else { throw SubscriptionDownloadError.privateHost }
            let bytes = withUnsafeBytes(of: address) { Array($0) }
            let mapped = bytes.prefix(10).allSatisfy { $0 == 0 } && bytes[10] == 255 && bytes[11] == 255
            if bytes.allSatisfy({ $0 == 0 }) || bytes.dropLast().allSatisfy({ $0 == 0 }) ||
                bytes[0] & 0xfe == 0xfc || (bytes[0] == 0xfe && bytes[1] & 0xc0 == 0x80) || bytes[0] == 0xff || mapped {
                throw SubscriptionDownloadError.privateHost
            }
        } else if name.allSatisfy({ $0.isNumber || $0 == "." }) || name.hasPrefix("0x") {
            let parts = name.split(separator: ".").compactMap { UInt8($0) }
            guard parts.count == 4, name.split(separator: ".").count == 4 else { throw SubscriptionDownloadError.privateHost }
            let a = parts[0], b = parts[1]
            if a == 0 || a == 10 || a == 127 || a >= 224 || (a == 169 && b == 254) ||
                (a == 172 && (16...31).contains(b)) || (a == 192 && b == 168) || (a == 100 && (64...127).contains(b)) {
                throw SubscriptionDownloadError.privateHost
            }
        }
    }

    static func fetch(_ source: String) async throws -> SubscriptionPayload {
        let downloaded = try await download(source)
        var seen = Set([source]), count = 0
        let expanded = try await expand(downloaded.content, depth: 0, seen: &seen, count: &count)
        let content = try ProfileSource.decodeSubscription(Data(expanded.utf8))
        return SubscriptionPayload(content: content, metadata: downloaded.metadata)
    }

    private static func expand(_ input: String, depth: Int, seen: inout Set<String>, count: inout Int) async throws -> String {
        let decoded = ProfileSource.base64(input).flatMap { String(data: $0, encoding: .utf8) } ?? input
        // Never scan JSON/YAML/INI structure for network addresses.
        if let kind = try? ProfileSource.classify(decoded), [.json, .yaml, .wireguard].contains(kind) { return decoded }
        var output: [String] = [], bytes = 0
        for original in decoded.components(separatedBy: .newlines) {
            try Task.checkCancellation()
            let line = original.trimmingCharacters(in: .whitespacesAndNewlines)
            var value = original
            if let url = URL(string: line), ["http", "https"].contains(url.scheme ?? ""), url.user == nil {
                guard depth < 2, count < 16, seen.insert(line).inserted else { throw SubscriptionDownloadError.nestedLimit }
                count += 1
                let nested = try await download(line)
                value = try await expand(nested.content, depth: depth + 1, seen: &seen, count: &count)
            }
            bytes += value.utf8.count + 1
            guard bytes <= ProfileSource.maxBytes else { throw SubscriptionDownloadError.oversized }
            output.append(value)
        }
        return output.joined(separator: "\n")
    }

    private static func download(_ source: String) async throws -> SubscriptionPayload {
        guard let url = URL(string: source.trimmingCharacters(in: .whitespacesAndNewlines)) else { throw SubscriptionDownloadError.insecureURL }
        try validate(url)
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 15
        configuration.timeoutIntervalForResource = 30
        configuration.httpShouldSetCookies = false
        configuration.httpCookieAcceptPolicy = .never
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        let session = URLSession(configuration: configuration, delegate: SubscriptionRedirectGuard(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        var request = URLRequest(url: url)
        // Panels use this client prefix to return a subscription rather than HTML.
        request.setValue("Hiddify/2.0 VetrOFF-macOS", forHTTPHeaderField: "User-Agent")
        request.setValue("application/json, application/yaml, text/yaml, text/plain, */*", forHTTPHeaderField: "Accept")
        let (temporary, response) = try await session.download(for: request)
        defer { try? FileManager.default.removeItem(at: temporary) }
        guard let http = response as? HTTPURLResponse, let finalURL = http.url else { throw SubscriptionDownloadError.invalidResponse }
        try validate(finalURL)
        guard http.statusCode == 200 else { throw SubscriptionDownloadError.httpStatus(http.statusCode) }
        let attributes = try FileManager.default.attributesOfItem(atPath: temporary.path)
        guard let size = attributes[.size] as? NSNumber, size.intValue <= ProfileSource.maxBytes else { throw SubscriptionDownloadError.oversized }
        let raw = try ProfileSource.decodeUTF8(Data(contentsOf: temporary))
        let content = ProfileSource.base64(raw).flatMap { String(data: $0, encoding: .utf8) } ?? raw
        let prefix = content.trimmingCharacters(in: .whitespacesAndNewlines).prefix(100).lowercased()
        guard !prefix.hasPrefix("<!doctype html"), !prefix.hasPrefix("<html") else { throw SubscriptionDownloadError.invalidResponse }
        var headers: [String: String] = [:]
        for (key, value) in http.allHeaderFields { if let key = key as? String { headers[key] = String(describing: value) } }
        return SubscriptionPayload(content: content, metadata: .parse(headers: headers, content: content))
    }
}
