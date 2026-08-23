import SwiftUI

/// SecureBank — realistic iOS integration of ComposeShield.
///
/// Sensitive screens (login, card details) are wrapped in `SecureShieldView`, and the task-switcher
/// protection is pinned to `.always` so the app switcher never previews real content. Demo mode
/// withdraws all of it for side-by-side manual comparison on a physical device.
@main
struct SecureBankApp: App {
    var body: some Scene {
        WindowGroup {
            BankContentView()
        }
    }
}
