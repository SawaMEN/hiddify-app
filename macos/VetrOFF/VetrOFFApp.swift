import SwiftUI

@main
struct VetrOFFApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .frame(minWidth: 760, minHeight: 520)
        }
        .defaultSize(width: 920, height: 640)
        .windowResizability(.contentMinSize)
    }
}
