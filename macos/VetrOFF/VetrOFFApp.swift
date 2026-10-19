import AppKit
import SwiftUI

@main
struct VetrOFFApp: App {
    // Shared native state: one VPN manager for the window and menu bar.
    @State private var vpn = VPNController()
    @State private var profiles = ProfileStore()

    var body: some Scene {
        WindowGroup(id: "main") {
            ContentView(vpn: vpn, profiles: profiles)
                .frame(minWidth: 760, minHeight: 520)
        }
        .defaultSize(width: 920, height: 640)
        .windowResizability(.contentMinSize)

        MenuBarExtra("VetrOFF", systemImage: "network") {
            VetrOFFMenuBar(vpn: vpn)
        }
    }
}

private struct VetrOFFMenuBar: View {
    let vpn: VPNController

    @Environment(\.openWindow) private var openWindow

    var body: some View {
        Text("VetrOFF — \(vpn.statusText)")
        Button("Открыть VetrOFF") {
            openWindow(id: "main")
            NSApp.activate(ignoringOtherApps: true)
        }
        if vpn.canStop {
            Button("Отключить VPN") { vpn.stop() }
        }
        Divider()
        Button("Завершить VetrOFF") {
            NSApp.terminate(nil)
        }
    }
}
