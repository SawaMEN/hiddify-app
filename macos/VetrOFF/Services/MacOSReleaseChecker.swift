import Foundation
import Observation

/// No updater daemon, code injection or automatic binary execution.
/// Only announces a newer signed macOS release and opens its GitHub page.
struct MacOSVersion: Comparable, Equatable, Sendable {
    let major: Int
    let minor: Int
    let patch: Int

    init?(_ raw: String) {
        let parts = raw.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3,
              parts.allSatisfy({ !$0.isEmpty && $0.allSatisfy(\.isNumber) }),
              let major = Int(parts[0]), let minor = Int(parts[1]),
              let patch = Int(parts[2])
        else { return nil }
        self.major = major
        self.minor = minor
        self.patch = patch
    }

    static func < (lhs: Self, rhs: Self) -> Bool {
        (lhs.major, lhs.minor, lhs.patch) < (rhs.major, rhs.minor, rhs.patch)
    }
}

struct MacOSSignedRelease: Decodable {
    struct Asset: Decodable { let name: String }
    let tag_name: String
    let draft: Bool
    let prerelease: Bool
    let assets: [Asset]
}

enum MacOSUpdatePolicy {
    static let apiURL = URL(
        string: "https://api.github.com/repos/SawaMEN/hiddify-app/releases?per_page=20"
    )!
    static let assetName = "VetrOFF-macOS-arm64.dmg"
    private static let versionRegex = try! NSRegularExpression(
        pattern: #"^macos-v([0-9]+\.[0-9]+\.[0-9]+)-[0-9a-f]{12}$"#
    )

    static func latestNewer(
        _ releases: [MacOSSignedRelease], current: MacOSVersion
    ) -> (version: MacOSVersion, url: URL)? {
        var latest: (version: MacOSVersion, url: URL)?
        for release in releases {
            guard !release.draft, release.assets.count == 1,
                  release.assets[0].name == assetName
            else { continue }
            let tag = release.tag_name
            let range = NSRange(tag.startIndex..<tag.endIndex, in: tag)
            guard let match = versionRegex.firstMatch(in: tag, range: range),
                  let versionRange = Range(match.range(at: 1), in: tag),
                  let version = MacOSVersion(String(tag[versionRange])),
                  version > current,
                  let url = URL(string:
                    "https://github.com/SawaMEN/hiddify-app/releases/tag/\(tag)")
            else { continue }
            if let previous = latest, previous.version >= version { continue }
            latest = (version, url)
        }
        return latest
    }
}

@MainActor
@Observable
final class MacOSReleaseChecker {
    private(set) var checking = false
    private(set) var status = "Проверка обновлений ещё не выполнялась."
    private(set) var availableRelease: URL?

    func check() async {
        guard !checking else { return }
        checking = true
        defer { checking = false }
        availableRelease = nil

        guard let current = MacOSVersion(
            Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        ) else {
            status = "Невозможно определить версию установленного приложения."
            return
        }
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 10
        config.timeoutIntervalForResource = 20
        let session = URLSession(configuration: config)
        defer { session.invalidateAndCancel() }
        do {
            var request = URLRequest(url: MacOSUpdatePolicy.apiURL)
            request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
            request.setValue("VetrOFF-macOS", forHTTPHeaderField: "User-Agent")
            let (temporary, response) = try await session.download(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200
            else { throw URLError(.badServerResponse) }
            let attributes = try FileManager.default.attributesOfItem(atPath: temporary.path)
            guard let bytes = attributes[.size] as? NSNumber,
                  bytes.intValue <= 512 * 1024
            else { throw URLError(.dataLengthExceedsMaximum) }
            let releases = try JSONDecoder().decode(
                [MacOSSignedRelease].self, from: Data(contentsOf: temporary)
            )
            if let newer = MacOSUpdatePolicy.latestNewer(releases, current: current) {
                availableRelease = newer.url
                status = "Доступна новая версия macOS: \(newer.version.major).\(newer.version.minor).\(newer.version.patch)."
            } else {
                status = "Более новой подписанной версии для macOS не найдено."
            }
        } catch {
            status = "Не удалось проверить обновления. Повторите попытку позже."
        }
    }
}
