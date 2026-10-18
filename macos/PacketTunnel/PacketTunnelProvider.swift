import Foundation
import NetworkExtension

/// Native macOS Packet Tunnel entry point.
///
/// Do not create system routes or report success until the Go/libbox TUN bridge
/// is active. A successful callback without forwarding IP packets blackholes
/// the user's traffic while incorrectly presenting the VPN as connected.
final class PacketTunnelProvider: NEPacketTunnelProvider {
    override func startTunnel(
        options: [String: NSObject]?,
        completionHandler: @escaping (Error?) -> Void
    ) {
        let error = NSError(
            domain: "app.vetroff.client.macos.PacketTunnel",
            code: 1,
            userInfo: [
                NSLocalizedDescriptionKey:
                    "The VetrOFF native macOS packet-flow bridge is not integrated yet."
            ]
        )
        completionHandler(error)
    }

    override func stopTunnel(
        with reason: NEProviderStopReason,
        completionHandler: @escaping () -> Void
    ) {
        completionHandler()
    }
}
