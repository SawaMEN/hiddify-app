import Foundation
import Observation
import ServiceManagement

@MainActor
@Observable
final class LoginItemController {
    private(set) var enabled = SMAppService.mainApp.status == .enabled
    private(set) var needsApproval = SMAppService.mainApp.status == .requiresApproval
    private(set) var busy = false
    var errorMessage: String?

    func refresh() {
        enabled = SMAppService.mainApp.status == .enabled
        needsApproval = SMAppService.mainApp.status == .requiresApproval
    }

    func setEnabled(_ value: Bool) async {
        guard !busy else { return }
        busy = true
        defer { busy = false; refresh() }
        do {
            if value { try SMAppService.mainApp.register() }
            else { try await SMAppService.mainApp.unregister() }
            errorMessage = nil
        } catch {
            errorMessage = "Не удалось изменить автозапуск. Установите VetrOFF в «Программы» и проверьте «Объекты входа» в настройках macOS."
        }
    }

    func openSettings() { SMAppService.openSystemSettingsLoginItems() }
}
