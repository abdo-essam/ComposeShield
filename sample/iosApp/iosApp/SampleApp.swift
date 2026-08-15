import SwiftUI
import ComposeShield

/// Entry point — sets up the singleton opt-in before the root view appears,
/// so the first screenshot of the launch screen is already protected on iOS.
@main
struct SampleApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
