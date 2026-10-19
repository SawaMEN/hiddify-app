import AppKit
import NetworkExtension
import SwiftUI
import UniformTypeIdentifiers

private enum Page: String, CaseIterable, Identifiable {
    case home, profiles, settings, diagnostics
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
    let vpn: VPNController
    let profiles: ProfileStore
    @AppStorage("macos.selectedProfileID.v1") private var selectedProfileID = ""

    @State private var showImporter = false
    @State private var showPasteSheet = false
    @State private var draftText = ""
    @State private var draftName = ""
    private var selectedID: UUID? { UUID(uuidString: selectedProfileID) }
    @State private var showMessage: String?

    var body: some View {
        NavigationSplitView {
            List(selection: $page) {
                ForEach(Page.allCases) { item in
                    Label(item.title, systemImage: item.symbol).tag(item)
                }
            }
            .navigationTitle("VetrOFF")
            .navigationSplitViewColumnWidth(min: 180, ideal: 210)
        } detail: {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    switch page ?? .home {
                    case .home: home
                    case .profiles: profilePage
                    case .settings: settings
                    case .diagnostics: diagnostics
                    }
                }
                .frame(maxWidth: 690, alignment: .leading)
                .padding(28)
                .frame(maxWidth: .infinity, alignment: .top)
            }
        }
        .frame(minWidth: 760, minHeight: 520)
        .task {
            await vpn.load()
            if !vpn.canStop {
                await profiles.refreshStaleSubscriptions()
            }
        }
        .sheet(isPresented: $showPasteSheet) { importSheet }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json, .plainText]) { result in
            do {
                let url = try result.get()
                let allowed = url.startAccessingSecurityScopedResource()
                defer { if allowed { url.stopAccessingSecurityScopedResource() } }
                let source = try ProfileSource.decodeUTF8(Data(contentsOf: url))
                Task { @MainActor in addProfile(source, name: url.deletingPathExtension().lastPathComponent) }
            } catch {
                Task { @MainActor in showMessage = error.localizedDescription }
            }
        }
        .alert("VetrOFF", isPresented: Binding(
            get: { showMessage != nil || vpn.errorMessage != nil },
            set: { visible in
                if !visible {
                    showMessage = nil
                    vpn.errorMessage = nil
                }
            }
        )) {
            Button("OK") {
                showMessage = nil
                vpn.errorMessage = nil
            }
        } message: {
            Text(showMessage ?? vpn.errorMessage ?? "")
        }
    }

    private var home: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Подключение").font(.largeTitle.bold())
            GroupBox {
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Image(systemName: "network")
                            .foregroundStyle(vpn.status == .connected ? .green : .secondary)
                        Text(vpn.statusText).font(.title3.weight(.medium))
                        Spacer()
                    }
                    LabeledContent("Выбранный профиль") {
                        Text(profiles.profiles.first(where: { $0.id == selectedID })?.name ?? "Не выбран")
                    }
                    HStack {
                        Button(vpn.isCoreReady ? "Тестировать VPN (Debug)" : "Подключить") {
                            guard let id = selectedID else { return }
                            vpn.connect(profileID: id)
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(
                            !vpn.isCoreReady ||
                            selectedID == nil ||
                            !vpn.isInstalled ||
                            !(selectedID.map { profiles.isReadyForDebugConnect($0) } ?? false)
                        )
                        if vpn.canStop {
                            Button("Отключить") { vpn.stop() }
                        }
                    }
                    Text(
                        vpn.isCoreReady
                        ? "Только Debug: поддерживаются локальные JSON/YAML/URI и заранее обновлённые HTTPS-подписки. Выбор URI передаётся нативному Go-ядру; передача IP-пакетов не подтверждена."
                        : "В обычной сборке подключение отключено до физической проверки передачи IP-пакетов через нативный TUN."
                    )
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(8)
            }
            Text("Проект предназначен для Apple Silicon и не использует Flutter, Dart либо Android-компоненты.")
                .foregroundStyle(.secondary)
        }
    }

    private var profilePage: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text("Профили").font(.largeTitle.bold())
                Spacer()
                Button("Из файла", systemImage: "doc.badge.plus") { showImporter = true }
                Button("Добавить", systemImage: "plus") {
                    draftText = ""
                    draftName = ""
                    showPasteSheet = true
                }
            }
            if profiles.profiles.isEmpty {
                ContentUnavailableView(
                    "Нет профилей",
                    systemImage: "square.stack.3d.up.slash",
                    description: Text("Добавьте JSON, ссылку на сервер или URL подписки. Исходный текст хранится в Keychain.")
                )
            } else {
                ForEach(profiles.profiles) { profile in
                    GroupBox {
                        HStack(spacing: 12) {
                            Image(systemName: selectedID == profile.id ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(selectedID == profile.id ? Color.accentColor : Color.secondary)
                            VStack(alignment: .leading, spacing: 7) {
                                Text(profile.name).font(.headline)
                                Text(profile.format.label).font(.caption).foregroundStyle(.secondary)

                                if profile.format == .subscriptionURL {
                                    if let updated = profile.lastRefreshedAt {
                                        Text("Обновлено: \(updated.formatted(date: .abbreviated, time: .shortened))")
                                            .font(.caption2).foregroundStyle(.secondary)
                                    } else {
                                        Text("Не загружена — обновите подписку")
                                            .font(.caption2).foregroundStyle(.secondary)
                                    }
                                }

                                let servers = profiles.servers(for: profile.id)
                                if !servers.isEmpty {
                                    Picker("Сервер", selection: Binding<Int>(
                                        get: { profile.selectedServerIndex ?? 0 },
                                        set: { index in
                                            do {
                                                try profiles.selectServer(id: profile.id, index: index)
                                            } catch {
                                                showMessage = error.localizedDescription
                                            }
                                        }
                                    )) {
                                        ForEach(servers) { server in
                                            Text(server.title).tag(server.index)
                                        }
                                    }
                                    .labelsHidden()
                                    .frame(maxWidth: 260)
                                    .disabled(vpn.canStop || profiles.refreshing.contains(profile.id))
                                }
                            }
                            Spacer()
                            if profile.format == .subscriptionURL {
                                Button(profiles.refreshing.contains(profile.id) ? "Обновление…" : "Обновить") {
                                    Task {
                                        do {
                                            try await profiles.refresh(profile.id)
                                        } catch {
                                            showMessage = error.localizedDescription
                                        }
                                    }
                                }
                                .disabled(profiles.refreshing.contains(profile.id) || vpn.canStop)
                            }
                            Button("Выбрать") { selectedProfileID = profile.id.uuidString }
                                .disabled(selectedID == profile.id)
                            Button(role: .destructive) {
                                do {
                                    try profiles.delete(profile.id)
                                    if selectedID == profile.id { selectedProfileID = "" }
                                } catch {
                                    showMessage = error.localizedDescription
                                }
                            } label: {
                                Image(systemName: "trash")
                            }
                            .help("Удалить профиль")
                        }
                        .padding(6)
                    }
                }
            }
        }
    }

    private var importSheet: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Добавить профиль").font(.title2.bold())
            TextField("Название (необязательно)", text: $draftName)
            TextEditor(text: $draftText)
                .font(.system(.body, design: .monospaced))
                .frame(minHeight: 200)
                .overlay(RoundedRectangle(cornerRadius: 6).strokeBorder(.quaternary))
            HStack {
                Button("Вставить из буфера") {
                    if let text = NSPasteboard.general.string(forType: .string) {
                        draftText = text
                    }
                }
                Spacer()
                Button("Отмена") { showPasteSheet = false }
                Button("Сохранить") {
                    addProfile(draftText, name: draftName)
                    if showMessage == nil { showPasteSheet = false }
                }
                .buttonStyle(.borderedProminent)
                .disabled(draftText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .padding(24)
        .frame(width: 570, height: 390)
    }

    private var settings: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Настройки").font(.largeTitle.bold())
            GroupBox {
                VStack(alignment: .leading, spacing: 12) {
                    LabeledContent("Платформа", value: "macOS 14+")
                    LabeledContent("Процессор", value: "Apple Silicon ARM64")
                    LabeledContent("Сетевой туннель", value: "Native Network Extension")
                    LabeledContent("Установлен в системе", value: vpn.isInstalled ? "Да" : "Нет")
                    Button(vpn.isInstalled ? "VPN-конфигурация установлена" : "Установить VPN-конфигурацию") {
                        Task { await vpn.install() }
                    }
                    .disabled(vpn.isBusy || vpn.isInstalled)
                    Text("Разрешение macOS на установку VPN не означает, что сетевое ядро уже подключено.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(8)
            }
        }
    }

    private var diagnostics: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Диагностика").font(.largeTitle.bold())
            LabeledContent("Система", value: ProcessInfo.processInfo.operatingSystemVersionString)
            LabeledContent("Архитектура", value: architecture)
            LabeledContent("Системный VPN", value: vpn.statusText)
            LabeledContent("Ядро внутри Packet Tunnel", value: vpn.diagnosticMessage)
            Button("Проверить Network Extension") { vpn.queryTunnelDiagnostics() }
                .disabled(vpn.status == .invalid || vpn.status == .disconnected)
            LabeledContent("Передача пакетов Go/libbox", value: "Пока не подключена")
        }
    }

    private func addProfile(_ raw: String, name: String?) {
        do {
            let record = try profiles.add(raw, name: name)
            selectedProfileID = record.id.uuidString
            showMessage = nil
            if record.format == .subscriptionURL {
                Task {
                    do {
                        try await profiles.refresh(record.id)
                    } catch {
                        showMessage = "Подписка сохранена, но обновление не удалось: \(error.localizedDescription)"
                    }
                }
            }
        } catch {
            showMessage = error.localizedDescription
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
