import AppKit
import NetworkExtension
import SwiftUI
import UniformTypeIdentifiers

private enum Page: String, CaseIterable, Identifiable {
    case home, profiles, servers, settings, diagnostics
    var id: Self { self }
    var title: String {
        switch self {
        case .home: "Главная"
        case .profiles: "Подписки и профили"
        case .servers: "Серверы"
        case .settings: "Настройки"
        case .diagnostics: "Диагностика"
        }
    }
    var symbol: String {
        switch self {
        case .home: "house"
        case .profiles: "square.stack.3d.up"
        case .servers: "globe.europe.africa"
        case .settings: "slider.horizontal.3"
        case .diagnostics: "waveform.path.ecg"
        }
    }
    var subtitle: String {
        switch self {
        case .home: "Ваше подключение под контролем"
        case .profiles: "Все ваши подключения в одном месте"
        case .servers: "Выберите маршрут для подключения"
        case .settings: "Настройте клиент под себя"
        case .diagnostics: "Состояние приложения и системного VPN"
        }
    }
}

private struct ProfileExport: FileDocument {
    static var readableContentTypes: [UTType] { [.plainText] }
    var text: String
    init(text: String) { self.text = text }
    init(configuration: ReadConfiguration) throws {
        text = try ProfileSource.decodeUTF8(configuration.file.regularFileContents ?? Data())
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}

struct ContentView: View {
    let vpn: VPNController
    let profiles: ProfileStore
    @State private var page: Page = .home
    @AppStorage("macos.selectedProfileID.v1") private var selectedProfileID = ""
    @AppStorage("macos.autoRefreshSubscriptions") private var autoRefreshSubscriptions = true
    @AppStorage("macos.refreshHours") private var refreshHours = 12
    @AppStorage("macos.appearance") private var appearance = "dark"
    @State private var showImporter = false
    @State private var showImport = false
    @State private var draftText = ""
    @State private var draftName = ""
    @State private var importError: String?
    @State private var showMessage: String?
    @State private var search = ""
    @State private var releaseChecker = MacOSReleaseChecker()
    @State private var pendingDelete: SavedProfile?
    @State private var renaming: SavedProfile?
    @State private var newName = ""
    @State private var editing: SavedProfile?
    @State private var editText = ""
    @State private var editError: String?
    @State private var onlyAvailable = false
    @State private var exportDocument: ProfileExport?
    @State private var exportName = "profile"
    @State private var showExporter = false
    @State private var probeStatuses: [String: String] = [:]
    @State private var probing: Set<String> = []
    private var selectedID: UUID? { UUID(uuidString: selectedProfileID) }
    private var selected: SavedProfile? { profiles.profiles.first { $0.id == selectedID } }
    private var locked: Bool { vpn.canStop || vpn.isBusy || vpn.status == .disconnecting }
    private var currentPage: Page { page }

    var body: some View {
        HStack(spacing: 0) {
            sidebar
            Divider()
            VStack(spacing: 0) {
                header
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        switch page {
                        case .home: home
                        case .profiles: profilePage
                        case .servers: serverPage
                        case .settings: settings
                        case .diagnostics: diagnostics
                        }
                    }
                    .frame(maxWidth: 860).padding(28).frame(maxWidth: .infinity)
                }
            }
        }
        .background(ClientCanvas())
        .tint(ClientTheme.accent)
        .preferredColorScheme(appearance == "system" ? nil : (appearance == "light" ? .light : .dark))
        .frame(minWidth: 960, minHeight: 640)
        .onAppear {
            if CommandLine.arguments.contains("--launch-smoke-test") {
                FileHandle.standardOutput.write(Data("VETROFF_MAIN_WINDOW_READY\n".utf8))
            }
        }
        .task {
            await vpn.load()
            if CommandLine.arguments.contains("--profile-smoke-test") {
                do { try ProfileSmokeTest.run(store: profiles) }
                catch {
                    FileHandle.standardError.write(Data("VETROFF_PROFILE_STORE_FAILED\n".utf8))
                }
            }
            reconcileSelection()
            while !Task.isCancelled {
                if autoRefreshSubscriptions && !locked {
                    await profiles.refreshStaleSubscriptions(maxAge: Double(refreshHours) * 3600) { !locked }
                }
                do { try await Task.sleep(for: .seconds(60)) } catch { break }
            }
        }
        .onChange(of: profiles.profiles.map(\.id)) { _, _ in reconcileSelection() }
        .onOpenURL { url in beginImport(url.absoluteString) }
        .onReceive(NotificationCenter.default.publisher(for: .clientAddProfile)) { _ in beginImport() }
        .onReceive(NotificationCenter.default.publisher(for: .clientImportClipboard)) { _ in
            beginImport(NSPasteboard.general.string(forType: .string) ?? "")
        }
        .sheet(isPresented: $showImport) { importSheet }
        .sheet(item: $renaming) { profile in renameSheet(profile) }
        .sheet(item: $editing) { profile in editSheet(profile) }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json, .plainText, UTType(filenameExtension: "yaml") ?? .plainText, .data]) { result in
            do { try importFile(result.get()) }
            catch { showMessage = error.localizedDescription }
        }
        .fileExporter(isPresented: $showExporter, document: exportDocument,
                      contentType: .plainText, defaultFilename: exportName) { result in
            if case .failure(let error) = result { showMessage = error.localizedDescription }
            exportDocument = nil
        }
        .confirmationDialog("Удалить профиль?", isPresented: Binding(
            get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }
        ), titleVisibility: .visible) {
            Button("Удалить", role: .destructive) {
                guard let profile = pendingDelete, !locked else { return }
                do { try profiles.delete(profile.id) } catch { showMessage = error.localizedDescription }
                pendingDelete = nil
            }
            Button("Отмена", role: .cancel) { pendingDelete = nil }
        } message: {
            Text("\(pendingDelete?.name ?? "Профиль") и его данные будут удалены с этого Mac.")
        }
        .alert("VetrOFF Client", isPresented: Binding(
            get: { showMessage != nil || vpn.errorMessage != nil },
            set: { if !$0 { showMessage = nil; vpn.errorMessage = nil } }
        )) {
            Button("Понятно") { showMessage = nil; vpn.errorMessage = nil }
        } message: { Text(showMessage ?? vpn.errorMessage ?? "") }
    }

    private var sidebar: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image("BrandMark").resizable().scaledToFit().frame(width: 42, height: 42)
                VStack(alignment: .leading, spacing: 2) {
                    Text("VetrOFF").font(.title2.bold())
                    Text("CLIENT · macOS").font(.system(size: 10, weight: .semibold, design: .rounded))
                        .tracking(2).foregroundStyle(.secondary)
                }
            }.padding(.bottom, 24)
            ForEach(Page.allCases) { item in
                Button {
                    page = item
                    search = ""
                } label: {
                    Label(item.title, systemImage: item.symbol)
                        .font(.system(size: 13, weight: page == item ? .semibold : .regular))
                        .frame(maxWidth: .infinity, alignment: .leading).padding(12)
                        .foregroundStyle(page == item ? ClientTheme.accent : .primary)
                        .background(page == item ? ClientTheme.accent.opacity(0.12) : .clear,
                                    in: RoundedRectangle(cornerRadius: 12))
                }.buttonStyle(.plain)
                .accessibilityAddTraits(page == item ? .isSelected : [])
            }
            Spacer()
            Button { beginImport() } label: {
                Label("Добавить подписку", systemImage: "plus.circle.fill")
                    .frame(maxWidth: .infinity).padding(.vertical, 8)
            }.buttonStyle(ClientPrimaryButton()).disabled(locked)
            HStack(spacing: 8) {
                Circle().fill(vpn.status == .connected ? ClientTheme.mint : .secondary).frame(width: 7, height: 7)
                Text(vpn.statusText).font(.caption).foregroundStyle(.secondary)
            }.padding(.top, 12)
        }
        .frame(width: 200).padding(24).background(ClientTheme.surface.opacity(0.28))
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(currentPage.title).font(.system(size: 28, weight: .bold, design: .rounded))
                Text(currentPage.subtitle).font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer()
            if page == .profiles || page == .servers {
                TextField("Поиск", text: $search).textFieldStyle(.roundedBorder).frame(width: 180)
            }
            Button { beginImport() } label: { Image(systemName: "plus") }
                .help("Добавить подписку или профиль (⌘N)").disabled(locked)
        }.padding(.horizontal, 28).padding(.vertical, 20)
        .background(.clear)
    }

    private var home: some View {
        VStack(spacing: 20) {
            ClientCard {
                VStack(alignment: .leading, spacing: 18) {
                    HStack {
                        Text("VetrOFF / CONNECT").font(.system(size: 10, weight: .bold, design: .monospaced))
                            .tracking(3).foregroundStyle(ClientTheme.accent)
                        Spacer()
                        Label(vpn.statusText, systemImage: vpn.status == .connected ? "checkmark.shield.fill" : "shield")
                            .font(.caption.weight(.medium)).foregroundStyle(vpn.status == .connected ? ClientTheme.mint : .secondary)
                    }
                    HStack(spacing: 22) {
                        VStack(alignment: .leading, spacing: 12) {
                            Text(vpn.canStop ? vpn.statusText : "Ваше подключение.\nВаши правила.")
                                .font(.system(size: 29, weight: .bold, design: .rounded))
                            Text(selected?.name ?? "Добавьте подписку, выберите сервер и подключитесь.")
                                .font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                            Button(vpn.canStop ? "Отключить" : (selected == nil ? "Добавить подписку" : "Подключить"), action: toggleConnection)
                                .buttonStyle(ClientPrimaryButton())
                                .disabled(vpn.isBusy || vpn.status == .disconnecting)
                        }.frame(maxWidth: .infinity, alignment: .leading)
                        ConnectionArtwork(connected: vpn.canStop,
                                          busy: vpn.isBusy || vpn.status == .disconnecting, action: toggleConnection)
                    }
                    if let issue = vpn.availabilityMessage {
                        Label(issue, systemImage: "info.circle").font(.caption2)
                            .foregroundStyle(.secondary).textSelection(.enabled)
                    }
                }

            }
            ClientCard {
                HStack(spacing: 14) {
                    ClientBadge(symbol: "globe.europe.africa")
                    VStack(alignment: .leading, spacing: 5) {
                        Text("Активный профиль").font(.caption).foregroundStyle(.secondary)
                        if profiles.profiles.isEmpty {
                            Text("Пока не добавлен").font(.headline)
                        } else {
                            Picker("Активный профиль", selection: $selectedProfileID) {
                                ForEach(profiles.profiles) { Text($0.name).tag($0.id.uuidString) }
                            }.labelsHidden().disabled(locked)
                        }
                        if let selected,
                           let server = profiles.servers(for: selected.id).first(where: { $0.index == (selected.selectedServerIndex ?? 0) }) {
                            Text(server.title).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                    }
                    Spacer()
                    Button("Серверы", systemImage: "chevron.right") { page = .servers; search = "" }
                        .disabled(selected == nil)
                }
            }
            if let selected { subscriptionDetails(selected) }
            HStack(spacing: 16) {
                quickAction("Подписки", detail: "Профилей: \(profiles.profiles.count)", symbol: "square.stack.3d.up") { page = .profiles }
                quickAction("Из буфера", detail: "Быстрый импорт", symbol: "doc.on.clipboard") {
                    beginImport(NSPasteboard.general.string(forType: .string) ?? "")
                }
            }
        }
    }

    private func quickAction(_ title: String, detail: String, symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ClientCard {
                HStack(spacing: 12) {
                    ClientBadge(symbol: symbol)
                    VStack(alignment: .leading, spacing: 5) {
                        Text(title).font(.headline)
                        Text(detail).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "arrow.up.right").foregroundStyle(.secondary)
                }
            }
        }.buttonStyle(.plain)
    }

    private var profilePage: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Button("Добавить подписку", systemImage: "plus") { beginImport() }
                    .buttonStyle(.borderedProminent).disabled(locked)
                Button("Из файла", systemImage: "doc.badge.plus") { showImporter = true }.disabled(locked)
                Spacer()
                Button("Обновить все", systemImage: "arrow.clockwise") { refreshAll() }
                    .disabled(locked || !profiles.refreshing.isEmpty || !profiles.profiles.contains { $0.format == .subscriptionURL })
            }
            if profiles.profiles.isEmpty {
                ClientCard {
                    ContentUnavailableView {
                        Label("Добавьте первую подписку", systemImage: "link.badge.plus")
                    } description: {
                        Text("Вставьте ссылку от вашего провайдера, ссылки серверов или выберите файл конфигурации.")
                    } actions: {
                        Button("Добавить подписку") { beginImport() }.buttonStyle(.borderedProminent)
                    }
                }
            } else {
                let filtered = profiles.profiles.filter { search.isEmpty || $0.name.localizedCaseInsensitiveContains(search) }
                if filtered.isEmpty { ContentUnavailableView.search(text: search) }
                ForEach(filtered) { profile in profileCard(profile) }
            }
        }
    }

    private func profileCard(_ profile: SavedProfile) -> some View {
        let busy = profiles.refreshing.contains(profile.id)
        let servers = profiles.servers(for: profile.id)
        return ClientCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    ClientBadge(symbol: profile.format == .subscriptionURL ? "link" : "doc.text")
                    VStack(alignment: .leading, spacing: 4) {
                        Text(profile.name).font(.headline).textSelection(.enabled)
                        Text("\(profile.format.label)\(servers.isEmpty ? "" : " · Серверов: \(servers.count)")")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if selectedID == profile.id {
                        Label("Выбран", systemImage: "checkmark.circle.fill")
                            .font(.caption).foregroundStyle(ClientTheme.mint)
                    } else {
                        Button("Выбрать") { selectedProfileID = profile.id.uuidString }
                            .disabled(locked || busy)
                    }
                    Menu {
                        Button("Изменить источник", systemImage: "doc.text") {
                            do { editText = try profiles.source(for: profile.id); editError = nil; editing = profile }
                            catch { showMessage = error.localizedDescription }
                        }
                        Button("Создать копию", systemImage: "doc.on.doc") {
                            do {
                                let copy = try profiles.duplicate(profile.id)
                                if copy.format == .subscriptionURL { refresh(copy, notify: false) }
                            } catch { showMessage = error.localizedDescription }
                        }
                        Button("Название из подписки", systemImage: "text.badge.checkmark") {
                            do { try profiles.restoreAutomaticName(profile.id) }
                            catch { showMessage = error.localizedDescription }
                        }.disabled(profile.format != .subscriptionURL)
                        Button("Переименовать", systemImage: "pencil") { newName = profile.name; renaming = profile }
                        Button("Экспортировать…", systemImage: "square.and.arrow.up") { export(profile) }
                        Divider()
                        Button("Удалить…", systemImage: "trash", role: .destructive) { pendingDelete = profile }
                    } label: { Image(systemName: "ellipsis") }.menuStyle(.borderlessButton).frame(width: 28)
                        .disabled(locked || busy)
                }
                if profile.format == .subscriptionURL {
                    Divider()
                    HStack {
                        if busy {
                            ProgressView().controlSize(.small)
                            Text("Загружаем подписку…").font(.caption)
                        } else {
                            Text(profile.lastRefreshedAt.map { "Обновлено: \($0.formatted(date: .abbreviated, time: .shortened))" }
                                 ?? "Подписка ещё не загружена")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button("Обновить", systemImage: "arrow.clockwise") { refresh(profile) }
                            .disabled(locked || busy)
                    }
                }
                if let error = profiles.refreshErrors[profile.id] {
                    Label(error, systemImage: "exclamationmark.circle").font(.caption).foregroundStyle(.orange)
                }
                subscriptionDetails(profile)
                if !servers.isEmpty {
                    HStack {
                        Picker("Сервер", selection: Binding(
                            get: { profile.selectedServerIndex ?? 0 },
                            set: { selectServer(profile, index: $0) }
                        )) {
                            ForEach(servers.filter(\.selectable)) { Text($0.title).tag($0.index) }
                        }.disabled(locked || busy)
                        Button { selectedProfileID = profile.id.uuidString; page = .servers; search = "" } label: {
                            Image(systemName: "list.bullet")
                        }.help("Показать серверы").disabled(locked || busy)
                    }
                }
            }
        }
    }

    private var serverPage: some View {
        VStack(alignment: .leading, spacing: 16) {
            if let selected {
                ClientCard {
                    Picker("Профиль", selection: $selectedProfileID) {
                        ForEach(profiles.profiles) { Text($0.name).tag($0.id.uuidString) }
                    }.disabled(locked)
                }
                let servers = profiles.servers(for: selected.id)
                if servers.isEmpty {
                    ClientCard {
                        ContentUnavailableView("Список серверов недоступен", systemImage: "network", description:
                            Text(selected.format == .subscriptionURL && selected.lastRefreshedAt == nil
                                 ? "Сначала обновите подписку на странице профилей."
                                 : "Для JSON и Clash YAML используется целая конфигурация. Выбор внутренних групп пока недоступен."))
                        Button("Открыть профиль") { page = .profiles }
                    }
                } else {
                    HStack {
                        Text("Серверов: \(servers.count)").font(.caption).foregroundStyle(.secondary)
                        Spacer()
                        Toggle("Только доступные форматы", isOn: $onlyAvailable).toggleStyle(.checkbox).font(.caption)
                    }
                    Text("TCP-проверка измеряет доступность порта, а не скорость VPN.").font(.caption2).foregroundStyle(.secondary)
                    let filtered = servers.filter { (!onlyAvailable || $0.selectable) && (search.isEmpty || $0.title.localizedCaseInsensitiveContains(search)) }
                    if filtered.isEmpty { ContentUnavailableView.search(text: search) }
                    LazyVStack(spacing: 10) {
                        ForEach(filtered) { server in serverRow(server, profile: selected) }
                    }
                }
            } else {
                ContentUnavailableView("Нет выбранного профиля", systemImage: "globe", description: Text("Добавьте подписку или выберите профиль."))
                Button("Добавить подписку") { beginImport() }.buttonStyle(.borderedProminent)
            }
        }
    }

    private func serverRow(_ server: ProfileServer, profile: SavedProfile) -> some View {
        let key = "\(profile.id)-\(server.rawURI)"
        return ClientCard {
            HStack(spacing: 14) {
                Button { selectServer(profile, index: server.index) } label: {
                    HStack(spacing: 12) {
                        ClientBadge(symbol: profile.selectedServerIndex == server.index ? "checkmark.circle.fill" : "globe")
                        VStack(alignment: .leading, spacing: 6) {
                            Text(server.title).font(.headline).lineLimit(2)
                            if !server.selectable {
                                Text("Недоступно в текущем ядре или требуется зависимая группа конфигурации")
                                    .font(.caption2).foregroundStyle(.orange)
                            }
                        }.frame(maxWidth: .infinity, alignment: .leading)
                    }.contentShape(Rectangle())
                }.buttonStyle(.plain).disabled(locked || profiles.refreshing.contains(profile.id) || !server.selectable)
                VStack(alignment: .trailing, spacing: 5) {
                    Button(probing.contains(key) ? "Проверка…" : "Проверить TCP") { probe(server, profile: profile) }
                        .disabled(probing.contains(key) || TCPReachability.target(for: server) == nil)
                    if let result = probeStatuses[key] { Text(result).font(.caption2).foregroundStyle(.secondary) }
                }
            }
        }
    }

    private var importSheet: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                ClientBadge(symbol: "link.badge.plus")
                VStack(alignment: .leading, spacing: 4) {
                    Text("Добавить подписку или профиль").font(.title2.bold())
                    Text("HTTP/HTTPS, ссылки серверов, Base64, JSON/JSONC, Clash YAML, WireGuard")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            TextField("Название автоматически из подписки", text: $draftName).textFieldStyle(.roundedBorder)
            Text("Ссылка или конфигурация").font(.headline)
            TextEditor(text: $draftText).font(.system(.body, design: .monospaced))
                .scrollContentBackground(.hidden).padding(10)
                .frame(height: 160)
                .background(.quaternary.opacity(0.3), in: RoundedRectangle(cornerRadius: 12))
                .accessibilityLabel("Ссылка на подписку или текст конфигурации")
            if let importError {
                Label(importError, systemImage: "exclamationmark.circle").font(.caption).foregroundStyle(.red)
            }
            Text("Данные подключения хранятся в защищённом хранилище macOS Keychain.")
                .font(.caption).foregroundStyle(.secondary)
            HStack {
                Button("Из буфера", systemImage: "doc.on.clipboard") {
                    draftText = NSPasteboard.general.string(forType: .string) ?? ""
                }
                Button("Из файла") { showImport = false; showImporter = true }
                Spacer()
                Button("Отмена") { showImport = false }.keyboardShortcut(.cancelAction)
                Button("Добавить") { submitImport() }.keyboardShortcut(.defaultAction)
                    .buttonStyle(.borderedProminent)
                    .disabled(locked || draftText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }.padding(28).frame(width: 620)
    }

    private func renameSheet(_ profile: SavedProfile) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Название профиля").font(.title2.bold())
            TextField("Название", text: $newName).textFieldStyle(.roundedBorder)
            HStack {
                Spacer()
                Button("Отмена") { renaming = nil }.keyboardShortcut(.cancelAction)
                Button("Сохранить") {
                    guard !locked else { return }
                    do { try profiles.rename(profile.id, name: newName); renaming = nil }
                    catch { showMessage = error.localizedDescription }
                }.keyboardShortcut(.defaultAction).buttonStyle(.borderedProminent)
                    .disabled(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || locked)
            }
        }.padding(28).frame(width: 430)
    }

    private func editSheet(_ profile: SavedProfile) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Источник: \(profile.name)").font(.title2.bold())
            TextEditor(text: $editText).font(.system(.body, design: .monospaced))
                .frame(height: 260).accessibilityLabel("Ссылка или конфигурация профиля")
            if let editError { Text(editError).font(.caption).foregroundStyle(.orange) }
            HStack {
                Spacer()
                Button("Отмена") { editing = nil }.keyboardShortcut(.cancelAction)
                Button("Сохранить") {
                    guard !locked else { return }
                    do {
                        try profiles.replace(profile.id, source: editText)
                        editing = nil; editText = ""
                        if let updated = profiles.profiles.first(where: { $0.id == profile.id }), updated.format == .subscriptionURL {
                            refresh(updated, notify: false)
                        }
                    } catch { editError = error.localizedDescription }
                }.buttonStyle(ClientPrimaryButton()).keyboardShortcut(.defaultAction).disabled(locked)
            }
        }.padding(28).frame(width: 650)
    }

    @ViewBuilder private func subscriptionDetails(_ profile: SavedProfile) -> some View {
        if let data = profile.subscription {
            VStack(alignment: .leading, spacing: 12) {
                if let used = data.usedBytes {
                    HStack {
                        Label("Трафик подписки", systemImage: "arrow.up.arrow.down").font(.caption).foregroundStyle(.secondary)
                        Spacer()
                        Text("\(bytes(used)) / \(data.total.flatMap { $0 > 0 ? bytes($0) : nil } ?? "Без лимита")")
                            .font(.caption.weight(.semibold)).monospacedDigit()
                    }
                    if let total = data.total, total > 0 {
                        ProgressView(value: min(Double(used) / Double(total), 1)).tint(ClientTheme.mint)
                    }
                }
                HStack {
                    if let date = data.expiresAt {
                        Label(date > Date() ? "До \(date.formatted(date: .abbreviated, time: .omitted))" : "Срок подписки истёк",
                              systemImage: "calendar")
                            .font(.caption).foregroundStyle(date > Date() ? Color.secondary : .orange)
                    }
                    Spacer()
                    if let link = profiles.subscriptionLinks[profile.id]?.website, let url = URL(string: link) {
                        Link("Личный кабинет", destination: url).font(.caption)
                    }
                    if let link = profiles.subscriptionLinks[profile.id]?.support, let url = URL(string: link) {
                        Link("Поддержка", destination: url).font(.caption)
                    }
                }
            }
        }
    }

    private func bytes(_ value: Int64) -> String { ByteCountFormatter.string(fromByteCount: value, countStyle: .binary) }

    private var settings: some View {
        VStack(spacing: 18) {
            ClientCard {
                VStack(alignment: .leading, spacing: 16) {
                    Label("Внешний вид", systemImage: "paintpalette").font(.headline)
                    Picker("Тема оформления", selection: $appearance) {
                        Text("Тёмная").tag("dark")
                        Text("Светлая").tag("light")
                        Text("Как в системе").tag("system")
                    }.pickerStyle(.segmented)
                }
            }
            ClientCard {
                VStack(alignment: .leading, spacing: 16) {
                    Label("Подписки", systemImage: "arrow.clockwise").font(.headline)
                    Toggle("Обновлять автоматически", isOn: $autoRefreshSubscriptions)
                    Picker("Период обновления", selection: $refreshHours) {
                        Text("Каждый час").tag(1)
                        Text("Каждые 6 часов").tag(6)
                        Text("Каждые 12 часов").tag(12)
                        Text("Раз в сутки").tag(24)
                    }.disabled(!autoRefreshSubscriptions)
                    Text("Пока приложение открыто и VPN выключен. Рабочая конфигурация сохраняется при ошибке загрузки.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            ClientCard {
                VStack(alignment: .leading, spacing: 16) {
                    Label("Системный VPN", systemImage: "shield.lefthalf.filled").font(.headline)
                    LabeledContent("VPN-конфигурация", value: vpn.isInstalled ? "Установлена" : "Не установлена")
                    Button("Установить VPN-конфигурацию") { Task { await vpn.install() } }
                        .disabled(locked || vpn.isInstalled)
                    if let issue = vpn.availabilityMessage { Text(issue).font(.caption).foregroundStyle(.secondary) }
                }
            }
            ClientCard {
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        Image("BrandMark").resizable().scaledToFit().frame(width: 48, height: 48)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("VetrOFF Client").font(.headline)
                            Text("macOS · Apple Silicon · \(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—")")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Button(releaseChecker.checking ? "Проверка…" : "Проверить обновления") {
                        Task { await releaseChecker.check() }
                    }.disabled(releaseChecker.checking)
                    Text(releaseChecker.status).font(.caption).foregroundStyle(.secondary)
                    if let release = releaseChecker.availableRelease { Link("Открыть релиз", destination: release) }
                    Link("Исходный код и лицензии", destination: URL(string: "https://github.com/SawaMEN/hiddify-app")!)
                }
            }
        }
    }

    private var diagnostics: some View {
        ClientCard {
            VStack(alignment: .leading, spacing: 16) {
                Label("Состояние клиента", systemImage: "waveform.path.ecg").font(.headline)
                LabeledContent("macOS", value: ProcessInfo.processInfo.operatingSystemVersionString)
                LabeledContent("Системный VPN", value: vpn.statusText)
                LabeledContent("Возможность запуска", value: vpn.availabilityMessage ?? "Ядро и разрешения доступны")
                LabeledContent("Профили", value: "\(profiles.profiles.count)")
                Divider()
                Text(vpn.diagnosticMessage).font(.callout).textSelection(.enabled)
                Button("Запросить состояние расширения") { vpn.queryTunnelDiagnostics() }.disabled(!vpn.canStop)
                Text("Статус подключения получен от macOS. Доступность TCP-порта не подтверждает передачу трафика через VPN.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private func beginImport(_ text: String = "") {
        guard !locked else { return }
        draftName = ""; draftText = text; importError = nil; showImport = true
    }

    private func submitImport() {
        guard !locked else { return }
        do {
            let record = try profiles.add(draftText, name: draftName)
            selectedProfileID = record.id.uuidString
            showImport = false
            draftText = ""; draftName = ""; importError = nil
            page = .profiles; search = ""
            if record.format == .subscriptionURL { refresh(record, notify: false) }
        } catch { importError = error.localizedDescription }
    }

    private func importFile(_ url: URL) throws {
        guard !locked else { return }
        let allowed = url.startAccessingSecurityScopedResource()
        defer { if allowed { url.stopAccessingSecurityScopedResource() } }
        let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard size <= ProfileSource.maxBytes else { throw ProfileSourceError.tooLarge }
        draftText = try ProfileSource.decodeUTF8(Data(contentsOf: url))
        draftName = url.deletingPathExtension().lastPathComponent
        importError = nil
        showImport = true
    }

    private func refresh(_ profile: SavedProfile, notify: Bool = true) {
        guard !locked else { return }
        Task { @MainActor in
            do {
                let changed = try await profiles.refresh(profile.id)
                if notify { showMessage = changed ? "Подписка обновлена." : "Подписка проверена. Изменений нет." }
            } catch { showMessage = "Не удалось обновить подписку «\(profile.name)». \(error.localizedDescription)" }
        }
    }

    private func refreshAll() {
        guard !locked else { return }
        Task { @MainActor in
            var failures: [String] = []
            for profile in profiles.profiles where profile.format == .subscriptionURL {
                guard !locked else { break }
                do { try await profiles.refresh(profile.id) }
                catch { failures.append(profile.name) }
            }
            showMessage = failures.isEmpty ? "Подписки проверены и обновлены." : "Не удалось обновить: \(failures.joined(separator: ", ")). Рабочие данные сохранены."
        }
    }

    private func reconcileSelection() {
        if selected == nil { selectedProfileID = profiles.profiles.first?.id.uuidString ?? "" }
    }

    private func selectServer(_ profile: SavedProfile, index: Int) {
        guard !locked && !profiles.refreshing.contains(profile.id) else { return }
        do { try profiles.selectServer(id: profile.id, index: index) }
        catch { showMessage = error.localizedDescription }
    }

    private func toggleConnection() {
        if vpn.canStop { vpn.stop(); return }
        guard let selected else { beginImport(); return }
        guard !profiles.refreshing.contains(selected.id) else {
            showMessage = "Дождитесь окончания обновления подписки."; return
        }
        guard profiles.isReadyForConnect(selected.id) else {
            page = .profiles
            showMessage = "Сначала загрузите подписку кнопкой «Обновить»."; return
        }
        Task { @MainActor in
            guard vpn.isCoreReady else { vpn.errorMessage = vpn.availabilityMessage; return }
            if !vpn.isInstalled { await vpn.install() }
            guard vpn.isInstalled else { return }
            vpn.connect(profileID: selected.id)
        }
    }

    private func export(_ profile: SavedProfile) {
        do {
            exportDocument = ProfileExport(text: try profiles.source(for: profile.id))
            exportName = "VetrOFF-profile.txt"
            showExporter = true
        } catch { showMessage = error.localizedDescription }
    }

    private func probe(_ server: ProfileServer, profile: SavedProfile) {
        let key = "\(profile.id)-\(server.rawURI)"
        guard probing.insert(key).inserted else { return }
        Task { @MainActor in
            defer { probing.remove(key) }
            do { probeStatuses[key] = "TCP: \(try await TCPReachability.measure(server)) мс" }
            catch { probeStatuses[key] = "Порт недоступен" }
        }
    }
}

extension Notification.Name {
    static let clientAddProfile = Notification.Name("VetrOFF.addProfile")
    static let clientImportClipboard = Notification.Name("VetrOFF.importClipboard")
}
