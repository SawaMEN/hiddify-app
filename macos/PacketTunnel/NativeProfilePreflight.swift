import Foundation

#if canImport(HiddifyCore)
import HiddifyCore

/// Uses the exact same Go parser and sing-box validators as the underlying
/// protocol engine, before any Network Extension routes can be installed.
/// It never copies a converted configuration to disk or starts an RPC server.
final class NativeProfilePreflight: @unchecked Sendable {
    private let lock = NSLock()
    private var validationID: String?

    func validate(_ content: String, lifecycle: TunnelLifecycle) throws {
        try lifecycle.checkStarting()
        let id = UUID().uuidString

        lock.lock()
        validationID = id
        lock.unlock()
        defer {
            MobileFinishProfileValidation(id)
            lock.lock()
            if validationID == id { validationID = nil }
            lock.unlock()
        }

        var beginError: NSError?
        guard MobileBeginProfileValidation(id, &beginError) else {
            throw NSError(
                domain: "app.vetroff.client.macos.ProfilePreflight", code: 1,
                userInfo: [NSLocalizedDescriptionKey:
                    "Не удалось начать проверку профиля средствами Go-ядра."]
            )
        }
        try lifecycle.checkStarting()

        var parseError: NSError?
        guard MobileValidateProfile(id, content, "", &parseError) else {
            // Never include the raw Go parser error: it may quote a
            // credential-bearing URI or an embedded subscription token.
            throw NSError(
                domain: "app.vetroff.client.macos.ProfilePreflight", code: 2,
                userInfo: [NSLocalizedDescriptionKey:
                    "Go-ядро отклонило конфигурацию сервера или подписки."]
            )
        }
        try lifecycle.checkStarting()
    }

    func cancel() {
        lock.lock()
        let id = validationID
        lock.unlock()
        if let id { MobileCancelProfileValidation(id) }
    }
}
#endif
