import Foundation
@preconcurrency import NetworkExtension
import Observation
import Security

/// The system VPN status is authoritative. Only a core-integrated, correctly
/// provisioned app can start the embedded Network Extension.
@MainActor
@Observable
final class VPNController {
    static let providerBundleID = "app.vetroff.client.macos.PacketTunnel"

    private(set) var status: NEVPNStatus = .invalid
    private(set) var isInstalled = false
    private(set) var isBusy = false
    private(set) var connectedAt: Date?
    private(set) var events: [VPNEvent] = []
    var errorMessage: String?
    private(set) var diagnosticMessage = "Статус ядра не запрашивался."

    @ObservationIgnored private var manager: NETunnelProviderManager?
    @ObservationIgnored private var statusObserver: NSObjectProtocol?

    var statusText: String {
        switch status {
        case .invalid: "Не настроено"
        case .disconnected: "Отключено"
        case .connecting: "Подключение"
        case .connected: "Подключено"
        case .reasserting: "Восстановление сети"
        case .disconnecting: "Отключение"
        @unknown default: "Неизвестное состояние"
        }
    }

    var canStop: Bool {
        switch status {
        case .connecting, .connected, .reasserting: true
        default: false
        }
    }

    private var hasVPNEntitlement: Bool {
        guard let task = SecTaskCreateFromSelf(kCFAllocatorDefault),
              let values = SecTaskCopyValueForEntitlement(
                task, "com.apple.developer.networking.networkextension" as CFString, nil
              ) as? [String] else { return false }
        return values.contains("packet-tunnel-provider")
    }

    func load() async {
        // Ad-hoc previews can open the UI but cannot access VPN preferences.
        // Do not show a permissions error on every launch of such a preview.
        guard hasVPNEntitlement else { return }
        guard !isBusy else { return }
        isBusy = true
        defer { isBusy = false }
        do {
            let available = try await NETunnelProviderManager.loadAllFromPreferences()
            manager = available.first { item in
                (item.protocolConfiguration as? NETunnelProviderProtocol)?
                    .providerBundleIdentifier == Self.providerBundleID
            }
            isInstalled = manager != nil
            observeConnection()
            refreshStatus()
        } catch {
            errorMessage = "Не удалось прочитать настройки VPN: \(error.localizedDescription)"
        }
    }

    func install() async {
        guard hasVPNEntitlement else {
            errorMessage = "Для VPN нужна сборка с подписью Apple Developer ID и разрешением Network Extension."
            return
        }
        guard !isBusy else { return }
        isBusy = true
        defer { isBusy = false }

        do {
            // Load existing preferences before creating anything, so reopening
            // the app does not install duplicate VPN configurations.
            let existing = try await NETunnelProviderManager.loadAllFromPreferences()
            let activeManager = existing.first { item in
                (item.protocolConfiguration as? NETunnelProviderProtocol)?
                    .providerBundleIdentifier == Self.providerBundleID
            } ?? NETunnelProviderManager()

            let configuration = NETunnelProviderProtocol()
            configuration.providerBundleIdentifier = Self.providerBundleID
            configuration.serverAddress = "VetrOFF"
            activeManager.localizedDescription = "VetrOFF"
            activeManager.protocolConfiguration = configuration
            activeManager.isEnabled = true
            try await activeManager.saveToPreferences()
            try await activeManager.loadFromPreferences()

            manager = activeManager
            isInstalled = true
            observeConnection()
            refreshStatus()
            errorMessage = nil
        } catch {
            errorMessage = "macOS не смогла установить VPN-конфигурацию: \(error.localizedDescription)"
        }
    }

    func stop() {
        guard canStop else { return }
        manager?.connection.stopVPNTunnel()
        refreshStatus()
    }

    private var hasSharedKeychain: Bool {
        guard let task = SecTaskCreateFromSelf(kCFAllocatorDefault),
              let groups = SecTaskCopyValueForEntitlement(
                task, "keychain-access-groups" as CFString, nil
              ) as? [String] else { return false }
        return groups.contains { $0.hasSuffix(".app.vetroff.client.macos.shared") }
    }

    var isCoreReady: Bool {
        #if VETROFF_NATIVE_CORE
        hasVPNEntitlement && hasSharedKeychain
        #else
        false
        #endif
    }

    var availabilityMessage: String? {
        #if VETROFF_NATIVE_CORE
        guard hasVPNEntitlement && hasSharedKeychain else {
            return "Эта сборка позволяет управлять подписками. Для VPN установите подписанную сборку с разрешением Apple Network Extension."
        }
        return nil
        #else
        return "В этой сборке отсутствует сетевое ядро. Установите полную macOS-сборку VetrOFF для подключения VPN."
        #endif
    }

    func connect(profileID: UUID) {
        guard isCoreReady else {
            errorMessage = availabilityMessage
            return
        }
        guard let manager, isInstalled else {
            errorMessage = "Сначала установите VPN-конфигурацию в настройках macOS."
            return
        }
        guard status == .disconnected else {
            errorMessage = "VPN не находится в состоянии «Отключено»."
            return
        }

        do {
            // Only an opaque UUID crosses NetworkExtension IPC; the extension
            // reads the secret profile itself through its signed Keychain group.
            try manager.connection.startVPNTunnel(
                options: TunnelStartRequest(profileID: profileID).options
            )
            errorMessage = nil
            refreshStatus()
        } catch {
            errorMessage = "Ошибка запуска VPN: \(error.localizedDescription)"
        }
    }

    func queryTunnelDiagnostics() {
        guard let session = manager?.connection as? NETunnelProviderSession else {
            diagnosticMessage = "VPN-конфигурация не установлена."
            return
        }
        guard status == .connected || status == .connecting || status == .reasserting else {
            diagnosticMessage = "Проверка расширения доступна только при запуске VPN."
            return
        }
        do {
            try session.sendProviderMessage(TunnelDiagnosticsProtocol.statusRequest) { [weak self] data in
                let reply = data.flatMap { try? JSONDecoder().decode(TunnelDiagnosticReply.self, from: $0) }
                Task { @MainActor [weak self] in
                    if let reply {
                        let linked = reply.coreLinked ? "загружено" : "отсутствует"
                        self?.diagnosticMessage = "Go: \(linked), стадия: \(reply.lifecycleState)."
                    } else {
                        self?.diagnosticMessage = "Packet Tunnel не ответил на запрос диагностики."
                    }
                }
            }
        } catch {
            diagnosticMessage = "Ошибка диагностики Network Extension: \(error.localizedDescription)"
        }
    }

    private func observeConnection() {
        if let statusObserver {
            NotificationCenter.default.removeObserver(statusObserver)
        }
        guard let connection = manager?.connection else { return }
        statusObserver = NotificationCenter.default.addObserver(
            forName: .NEVPNStatusDidChange,
            object: connection,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.refreshStatus()
            }
        }
    }

    private func refreshStatus() {
        let next = manager?.connection.status ?? .invalid
        let changed = next != status
        status = next
        connectedAt = manager?.connection.connectedDate
        if changed {
            events.append(VPNEvent(date: Date(), message: statusText))
            if events.count > 100 { events.removeFirst(events.count - 100) }
        }
    }
}

struct VPNEvent: Identifiable {
    let id = UUID()
    let date: Date
    let message: String
}
