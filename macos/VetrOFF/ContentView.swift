import SwiftUI

private enum Page: String, CaseIterable, Identifiable {
    case home
    case profiles
    case settings
    case diagnostics

    var id: Self { self }

    var title: String {
        switch self {
        case .home: "Главная"
        case .profiles: "Профили"
        case .settings: "Настройки"
        case .diagnostics: "Диагностика"
        }
    }

    var symbol: String {
        switch self {
        case .home: "network"
        case .profiles: "square.stack.3d.up"
        case .settings: "gearshape"
        case .diagnostics: "waveform.path.ecg"
        }
    }
}

struct ContentView: View {
    @State private var page: Page? = .home

    var body: some View {
        NavigationSplitView {
            List(selection: $page) {
                ForEach(Page.allCases) { item in
                    Label(item.title, systemImage: item.symbol)
                        .tag(item)
                }
            }
            .navigationTitle("VetrOFF")
            .navigationSplitViewColumnWidth(min: 180, ideal: 210)
        } detail: {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    switch page ?? .home {
                    case .home: home
                    case .profiles: profiles
                    case .settings: settings
                    case .diagnostics: diagnostics
                    }
                }
                .frame(maxWidth: 640, alignment: .leading)
                .padding(28)
                .frame(maxWidth: .infinity, alignment: .top)
            }
        }
    }

    private var home: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Подключение")
                .font(.largeTitle.bold())

            GroupBox {
                VStack(alignment: .leading, spacing: 14) {
                    Label("Не подключено", systemImage: "circle.dotted")
                        .font(.title3)

                    Text("Пользовательский интерфейс и Packet Tunnel Extension созданы. VPN-ядро ещё не подключено к системному packetFlow.")
                        .foregroundStyle(.secondary)

                    Button("Подключить") {}
                        .buttonStyle(.borderedProminent)
                        .disabled(true)

                    Text("Подключение будет доступно только после проверки реального прохождения трафика. Приложение не сообщает о фиктивном VPN-соединении.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(8)
            }
        }
    }

    private var profiles: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Профили")
                .font(.largeTitle.bold())
            ContentUnavailableView(
                "Профили ещё не импортированы",
                systemImage: "square.stack.3d.up.slash",
                description: Text("Нативный импорт и хранилище профилей будут добавлены после подключения ядра, чтобы не менять формат существующих подписок.")
            )
        }
    }

    private var settings: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Настройки")
                .font(.largeTitle.bold())
            LabeledContent("Платформа", value: "macOS")
            LabeledContent("Архитектура", value: "Apple Silicon (arm64)")
            LabeledContent("Интерфейс", value: "SwiftUI")
            LabeledContent("Системный туннель", value: "Network Extension")
        }
    }

    private var diagnostics: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Диагностика")
                .font(.largeTitle.bold())
            LabeledContent("Система", value: ProcessInfo.processInfo.operatingSystemVersionString)
            LabeledContent("Архитектура сборки", value: architecture)
            LabeledContent("VPN-движок", value: "Не инициализирован")
            Text("Для диагностических проверок трафика потребуется реальный адаптер libbox ↔ NEPacketTunnelFlow.")
                .foregroundStyle(.secondary)
        }
    }

    private var architecture: String {
        #if arch(arm64)
        "arm64"
        #else
        "Неподдерживаемая архитектура"
        #endif
    }
}
