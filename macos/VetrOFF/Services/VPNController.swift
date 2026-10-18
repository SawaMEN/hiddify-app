import Foundation
import NetworkExtension
import Observation

/// System VPN preferences only. The transport stays disabled until the
/// in-extension libbox adapter has passed end-to-end packet tests.
@MainActor
@Observable
final class VPNController {
    static let providerBundleID = "app.vetroff.client.macos.PacketTunnel"

    private(set) var status: NEVPNStatus = .invalid
    private(set) var isInstalled = false
    private(set) var isBusy = false
    var errorMessage: String?

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

    func load() async {
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

    // Never start an unimplemented tunnel: reporting success would silently
    // blackhole traffic. This gate is removed ONLY when the in-extension core
    // lifecycle and packet forwarding are proven.
    var isCoreReady: Bool { false }

    func connect() {
        errorMessage = "Сетевое ядро ещё не подключено к Packet Tunnel. Подключение отключено, чтобы не нарушать доступ к сети."
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
        status = manager?.connection.status ?? .invalid
    }
}
