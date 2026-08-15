import SwiftUI
import ComposeShield

/// Entry point. Protection is applied on demand via SecureShieldView in ContentView — there is no
/// setup to perform at launch.
@main
struct SampleApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
