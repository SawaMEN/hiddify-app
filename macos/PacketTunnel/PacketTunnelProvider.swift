import Foundation
@preconcurrency import NetworkExtension

/// Real native NetworkExtension entry point.
/// System settings are installed only by Go's OpenTun callback.
final class PacketTunnelProvider: NEPacketTunnelProvider {
    #if canImport(HiddifyCore)
    private lazy var core = NativeTunnelEngine(tunnel: self)
    #endif

    override func startTunnel(
        options: [String: NSObject]?,
        completionHandler: @escaping (Error?) -> Void
    ) {
        let content: String
        do {
            let request = try TunnelStartRequest(options: options)
            content = try ProfileVault.connectableSource(id: request.profileID)
        } catch {
            completionHandler(error)
            return
        }

        #if canImport(HiddifyCore)
        core.start(profileContent: content, completion: completionHandler)
        #else
        completionHandler(NSError(
            domain: "app.vetroff.client.macos.PacketTunnel",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey: "HiddifyCore XCFramework is not linked."]
        ))
        #endif
    }

    override func stopTunnel(
        with reason: NEProviderStopReason,
        completionHandler: @escaping () -> Void
    ) {
        #if canImport(HiddifyCore)
        core.stop(completion: completionHandler)
        #else
        completionHandler()
        #endif
    }
    override func handleAppMessage(
        _ messageData: Data,
        completionHandler: ((Data?) -> Void)?
    ) {
        guard TunnelDiagnosticsProtocol.accepts(messageData) else {
            completionHandler?(nil)
            return
        }
        #if canImport(HiddifyCore)
        let reply = TunnelDiagnosticReply(
            coreLinked: NativeCoreProbe.isLinked(),
            lifecycleState: core.diagnosticLifecycleState
        )
        #else
        let reply = TunnelDiagnosticReply(
            coreLinked: false,
            lifecycleState: "unavailable"
        )
        #endif
        completionHandler?(try? JSONEncoder().encode(reply))
    }

}
