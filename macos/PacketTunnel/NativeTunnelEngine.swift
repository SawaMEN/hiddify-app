import Foundation
@preconcurrency import NetworkExtension

#if canImport(HiddifyCore)
import HiddifyCore

/// The Go lifecycle is serialized on one native worker queue and stays
/// strictly inside the macOS Packet Tunnel process.
final class NativeTunnelEngine: @unchecked Sendable {
    private let queue = DispatchQueue(label: "app.vetroff.client.macos.core", qos: .userInitiated)
    private let tunnel: NEPacketTunnelProvider
    private var platform: CorePlatformInterface?

    init(tunnel: NEPacketTunnelProvider) {
        self.tunnel = tunnel
    }

    func start(profileContent: String, completion: @escaping (Error?) -> Void) {
        let finished = NativeCompletion(completion)
        queue.async { [self] in
            do {
                let support = FileManager.default.urls(
                    for: .applicationSupportDirectory,
                    in: .userDomainMask
                )[0].appendingPathComponent("VetrOFFPacketTunnel", isDirectory: true)
                let working = support.appendingPathComponent("working", isDirectory: true)
                let temporary = support.appendingPathComponent("tmp", isDirectory: true)
                try FileManager.default.createDirectory(at: working, withIntermediateDirectories: true)
                try FileManager.default.createDirectory(at: temporary, withIntermediateDirectories: true)

                let options = MobileSetupOptions()
                options.basePath = support.path
                options.workingDir = working.path
                options.tempDir = temporary.path
                options.listen = "127.0.0.1:0"
                options.secret = ""
                options.debug = false
                options.mode = 0 // hcore.SetupMode_OLD: NO additional gRPC listener.
                options.memoryLimit = 192 * 1024 * 1024
                options.fixAndroidStack = false

                let callback = CorePlatformInterface(tunnel: tunnel)
                platform = callback
                var setupError: NSError?
                guard MobileSetup(options, callback, &setupError) else {
                    throw setupError ?? NSError(domain: "VetrOFF.Go", code: 1)
                }
                var startError: NSError?
                guard MobileStart("", profileContent, &startError) else {
                    throw startError ?? NSError(domain: "VetrOFF.Go", code: 2)
                }
                finished.call(nil)
            } catch {
                platform?.reset()
                platform = nil
                // best effort Go cleanup after partial setup/start
                var stopError: NSError?
                _ = MobileStop(&stopError)
                finished.call(error)
            }
        }
    }

    func stop(completion: @escaping () -> Void) {
        let finished = NativeCompletion { _ in completion() }
        queue.async { [self] in
            var stopError: NSError?
            _ = MobileStop(&stopError)
            platform?.reset()
            platform = nil
            finished.call(nil)
        }
    }
}

/// Completion handlers are sent exactly once, without crossing the
/// Swift 6 non-Sendable boundary directly.
private final class NativeCompletion: @unchecked Sendable {
    private let lock = NSLock()
    private var callback: ((Error?) -> Void)?

    init(_ callback: @escaping (Error?) -> Void) {
        self.callback = callback
    }

    func call(_ error: Error?) {
        lock.lock()
        let current = callback
        callback = nil
        lock.unlock()
        current?(error)
    }
}
#endif
