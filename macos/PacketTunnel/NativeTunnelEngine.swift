import Foundation
@preconcurrency import NetworkExtension

#if canImport(HiddifyCore)
import HiddifyCore

/// The extension owns exactly one Go service and an ordered macOS settings
/// teardown. No Flutter, helper daemon or per-packet Swift proxy is involved.
final class NativeTunnelEngine: @unchecked Sendable {
    private let queue = DispatchQueue(label: "app.vetroff.client.macos.core", qos: .userInitiated)
    private let tunnel: NEPacketTunnelProvider
    private let lifecycle = TunnelLifecycle()
    private let preflight = NativeProfilePreflight()

    // These fields are accessed ONLY from the serial worker queue.
    private var platform: CorePlatformInterface?
    private var settingsCoordinator: TunnelSettingsCoordinator?
    private var setupAttempted = false
    private var coreStopFailed = false
    private var teardownInProgress = false
    private var teardownWatchdogFired = false
    private var failedStart: (completion: NativeCompletion, error: Error)?
    private var stopCallbacks: [NativeCompletion] = []

    init(tunnel: NEPacketTunnelProvider) {
        self.tunnel = tunnel
    }

    var diagnosticLifecycleState: String { lifecycle.snapshot.rawValue }

    func start(profileContent: String, completion: @escaping (Error?) -> Void) {
        let finished = NativeCompletion(completion)
        do {
            try lifecycle.beginStart()
        } catch {
            finished.call(error)
            return
        }

        queue.async { [self] in
            do {
                try lifecycle.checkStarting()
                // Parse and validate with the pinned Go core BEFORE creating
                // the VPN interface or installing any system routes.
                try preflight.validate(profileContent, lifecycle: lifecycle)
                try lifecycle.checkStarting()

                let coordinator = TunnelSettingsCoordinator { [tunnel] settings, handler in
                    tunnel.setTunnelNetworkSettings(settings, completionHandler: handler)
                }
                settingsCoordinator = coordinator

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
                options.mode = 0 // hcore.SetupMode_OLD; no gRPC command server.
                options.memoryLimit = 192 * 1024 * 1024
                options.fixAndroidStack = false

                let callback = CorePlatformInterface(
                    settingsCoordinator: coordinator, lifecycle: lifecycle
                )
                platform = callback
                try lifecycle.checkStarting()
                setupAttempted = true

                var setupError: NSError?
                guard MobileSetup(options, callback, &setupError) else {
                    throw setupError ?? NSError(domain: "VetrOFF.Go", code: 1)
                }
                try lifecycle.checkStarting()
                var startError: NSError?
                guard MobileStartPacketTunnel(profileContent, &startError) else {
                    throw startError ?? NSError(domain: "VetrOFF.Go", code: 2)
                }
                guard callback.hasOpenedTunnel else {
                    throw NSError(
                        domain: "app.vetroff.client.macos.PacketTunnel",
                        code: 8,
                        userInfo: [NSLocalizedDescriptionKey:
                            "Go core returned success without opening a macOS TUN interface."]
                    )
                }
                try lifecycle.didStart()
                finished.call(nil)
            } catch {
                failedStart = (finished, error)
                beginTeardown()
            }
        }
    }

    func stop(completion: @escaping () -> Void) {
        let finished = NativeCompletion { _ in completion() }
        // Cancels synchronous Go/OpenTun startup without waiting on the worker.
        _ = lifecycle.requestStop()
        preflight.cancel()
        queue.async { [self] in
            // A first watchdog may have already released outstanding stop
            // completions while macOS is still holding the settings callback.
            // Repeated stop requests must not hang forever behind that event.
            if teardownInProgress && teardownWatchdogFired {
                finished.call(nil)
                return
            }
            stopCallbacks.append(finished)
            if teardownInProgress { return }
            if lifecycle.snapshot == .idle {
                finishStops()
            } else {
                beginTeardown()
            }
        }
    }

    private func beginTeardown() {
        guard !teardownInProgress else { return }
        teardownInProgress = true
        teardownWatchdogFired = false
        coreStopFailed = false
        _ = lifecycle.requestStop()

        if setupAttempted {
            var error: NSError?
            if !MobileStop(&error) {
                // Even if macOS removes routes, a failed Go stop may leave
                // a live core instance. Reject reconnects in this provider.
                coreStopFailed = true
            }
            setupAttempted = false
        }
        platform?.reset()
        platform = nil

        let cleared: (Error?) -> Void = { [self] error in
            queue.async { [self] in
                guard teardownInProgress else { return }
                // A failed settings removal MUST NOT make the service idle:
                // no retry may start until macOS has cleared old routes.
                if error == nil && !coreStopFailed {
                    lifecycle.finish()
                }
                teardownInProgress = false
                teardownWatchdogFired = false
                settingsCoordinator = nil
                if let failedStart {
                    self.failedStart = nil
                    failedStart.completion.call(failedStart.error)
                }
                finishStops()
            }
        }

        if let settingsCoordinator {
            settingsCoordinator.clear(completion: cleared)
        } else {
            tunnel.setTunnelNetworkSettings(nil, completionHandler: cleared)
        }

        // NetworkExtension should always call the removal completion, but a
        // stalled OS callback must not block provider shutdown indefinitely.
        // The lifecycle deliberately remains "stopping" until real cleanup.
        queue.asyncAfter(deadline: .now() + .seconds(30)) { [self] in
            guard teardownInProgress else { return }
            teardownWatchdogFired = true
            if let failedStart {
                self.failedStart = nil
                failedStart.completion.call(failedStart.error)
            }
            finishStops()
        }
    }

    private func finishStops() {
        let callbacks = stopCallbacks
        stopCallbacks.removeAll()
        for callback in callbacks { callback.call(nil) }
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
