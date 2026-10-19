import Foundation
@preconcurrency import NetworkExtension

#if canImport(HiddifyCore)
import HiddifyCore

/// Exactly one in-process Go service instance belongs to the extension.
/// No Flutter, separate helper daemon, or localhost proxy is involved.
final class NativeTunnelEngine: @unchecked Sendable {
    private let queue = DispatchQueue(label: "app.vetroff.client.macos.core", qos: .userInitiated)
    private let tunnel: NEPacketTunnelProvider
    private let lifecycle = TunnelLifecycle()

    // Accessed only from queue (except the locked lifecycle).
    private var platform: CorePlatformInterface?

    init(tunnel: NEPacketTunnelProvider) {
        self.tunnel = tunnel
    }

    var diagnosticLifecycleState: String {
        lifecycle.snapshot.rawValue
    }

    func start(profileContent: String, completion: @escaping (Error?) -> Void) {
        let finished = NativeCompletion(completion)
        do {
            try lifecycle.beginStart()
        } catch {
            finished.call(error)
            return
        }

        queue.async { [self] in
            var attemptedSetup = false
            do {
                try lifecycle.checkStarting()
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
                options.mode = 0 // hcore.SetupMode_OLD: no gRPC command server.
                options.memoryLimit = 192 * 1024 * 1024
                options.fixAndroidStack = false

                let callback = CorePlatformInterface(tunnel: tunnel, lifecycle: lifecycle)
                platform = callback
                try lifecycle.checkStarting()
                attemptedSetup = true
                var setupError: NSError?
                guard MobileSetup(options, callback, &setupError) else {
                    throw setupError ?? NSError(domain: "VetrOFF.Go", code: 1)
                }
                try lifecycle.checkStarting()
                var startError: NSError?
                guard MobileStartPacketTunnel(profileContent, &startError) else {
                    throw startError ?? NSError(domain: "VetrOFF.Go", code: 2)
                }
                // The mobile core defaults to EnableTun=false. Even if Go
                // reports success, NetworkExtension must NOT report connected
                // unless the synchronous OpenTun callback returned a real FD.
                guard callback.hasOpenedTunnel else {
                    throw NSError(
                        domain: "app.vetroff.client.macos.PacketTunnel",
                        code: 8,
                        userInfo: [NSLocalizedDescriptionKey:
                            "Go core started without opening the macOS TUN interface."]
                    )
                }
                try lifecycle.didStart() // A stop during Go startup wins.
                finished.call(nil)
            } catch {
                if attemptedSetup {
                    var stopError: NSError?
                    _ = MobileStop(&stopError)
                }
                platform?.reset()
                platform = nil
                // Explicitly release routes even when setup failed halfway.
                // Stay in 'stopping' until macOS confirms route cleanup.
                // Otherwise a new start could race a late nil-settings callback.
                _ = lifecycle.requestStop()
                tunnel.setTunnelNetworkSettings(nil) { [self] _ in
                    lifecycle.finish()
                    finished.call(error)
                }
            }
        }
    }

    func stop(completion: @escaping () -> Void) {
        let finished = NativeCompletion { _ in completion() }
        // Signal cancellation *before* waiting behind the Go start worker.
        let shouldStop = lifecycle.requestStop()
        queue.async { [self] in
            if shouldStop {
                var stopError: NSError?
                _ = MobileStop(&stopError)
            }
            platform?.reset()
            platform = nil
            if shouldStop {
                tunnel.setTunnelNetworkSettings(nil) { [self] _ in
                    lifecycle.finish()
                    finished.call(nil)
                }
            } else {
                // Already idle, or the failed-start cleanup owns route removal.
                finished.call(nil)
            }
        }
    }
}

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
