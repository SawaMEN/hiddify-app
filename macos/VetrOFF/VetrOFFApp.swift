import AppKit
import NetworkExtension
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
            VetrOFFMenuBar(vpn: vpn, profiles: profiles)
        }
    }
}

private struct VetrOFFMenuBar: View {
    let vpn: VPNController
    let profiles: ProfileStore

    @Environment(\.openWindow) private var openWindow
    @AppStorage("macos.selectedProfileID.v1") private var selectedProfileID = ""
    @AppStorage("macos.autoRefreshSubscriptions") private var autoRefreshSubscriptions = true

    private var selectedID: UUID? { UUID(uuidString: selectedProfileID) }

    var body: some View {
        Text("VetrOFF — \(vpn.statusText)")

        Menu("Выбранный профиль") {
            if profiles.profiles.isEmpty {
                Text("Профилей пока нет")
            }
            ForEach(profiles.profiles) { profile in
                Button {
                    selectedProfileID = profile.id.uuidString
                } label: {
                    if selectedID == profile.id {
                        Label(profile.name, systemImage: "checkmark")
                    } else {
                        Text(profile.name)
                    }
                }
                .disabled(vpn.canStop)
            }
        }

        if let selectedID, vpn.isCoreReady, vpn.isInstalled,
           vpn.status == .disconnected,
           profiles.isReadyForDebugConnect(selectedID) {
            Button("Тестировать VPN (Debug)") {
                vpn.connect(profileID: selectedID)
            }
            .disabled(profiles.refreshing.contains(selectedID))
        }
        if vpn.canStop {
            Button("Отключить VPN") { vpn.stop() }
        }
        if autoRefreshSubscriptions && !vpn.canStop &&
            !profiles.profiles.filter({ $0.format == .subscriptionURL }).isEmpty {
            Button("Обновить подписки") {
                Task { await profiles.refreshStaleSubscriptions(maxAge: 0) }
            }
        }
        Divider()
        Button("Открыть VetrOFF") {
            openWindow(id: "main")
            NSApp.activate(ignoringOtherApps: true)
        }
        Button("Завершить VetrOFF") {
            NSApp.terminate(nil)
        }
    }
}
