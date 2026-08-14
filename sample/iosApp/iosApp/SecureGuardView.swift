import SwiftUI
import UIKit
import ComposeGuard

/// A SwiftUI view that applies ComposeGuard's iOS screen capture protection to its content.
///
/// While this view is in the view hierarchy, the scene's window is protected (when the opt-in has
/// been granted). Protection releases when the view disappears — there is no teardown call to forget.
///
/// ```swift
/// SecureGuardView {
///     SensitiveContent()
/// } onFailure: { capability in
///     print("Protection failed: \(capability)")
/// }
/// ```
///
/// **Protection is window-scoped, not view-scoped.** While this is in the hierarchy, the entire
/// window is protected — a sibling view outside this container is also protected. See
/// `docs/platform-notes.md`.
///
/// - Parameters:
///   - simulateFailure: When true, a test hook forces the mechanism to report failure, exercising
///     both failure postures. Used by the sample app to demonstrate M10.
///   - content: The protected content. Rendered unchanged under `FailOpen`; obscured under
///     `FailClosed` while the mechanism is broken.
///   - onFailure: Invoked when the protection mechanism fails or stops working.
struct SecureGuardView<Content: View>: View {
    @Binding var simulateFailure: Bool
    let content: Content
    let onFailure: ((Capability) -> Void)?

    init(
        simulateFailure: Binding<Bool> = .constant(false),
        @ViewBuilder content: () -> Content,
        onFailure: ((Capability) -> Void)? = nil
    ) {
        self._simulateFailure = simulateFailure
        self.content = content()
        self.onFailure = onFailure
    }

    var body: some View {
        _SecureGuardViewRepresentable(
            simulateFailure: simulateFailure,
            onFailure: onFailure
        ) {
            content
        }
    }
}

// MARK: - UIKit bridge

/// UIViewControllerRepresentable bridge that acquires and releases protection through
/// `ComposeGuard.shared`.
private struct _SecureGuardViewRepresentable<Content: View>: UIViewControllerRepresentable {
    let simulateFailure: Bool
    let onFailure: ((Capability) -> Void)?
    let content: () -> Content

    func makeUIViewController(context: Context) -> _SecureGuardViewController<Content> {
        _SecureGuardViewController(content: content, onFailure: onFailure)
    }

    func updateUIViewController(_ vc: _SecureGuardViewController<Content>, context: Context) {
        vc.updateSimulateFailure(simulateFailure)
    }
}

/// Hosts the SwiftUI content and manages the protection handle lifetime.
///
/// `viewWillAppear` / `viewWillDisappear` are the lifecycle anchors: they fire symmetrically on
/// navigation push/pop, sheet present/dismiss, and tab switches — the cases where a hand-managed
/// flag most often leaks or double-clears.
private final class _SecureGuardViewController<Content: View>: UIViewController {
    private var handle: ProtectionHandle?
    private let onFailure: ((Capability) -> Void)?
    private var hostingController: UIHostingController<Content>

    init(content: () -> Content, onFailure: ((Capability) -> Void)?) {
        self.onFailure = onFailure
        self.hostingController = UIHostingController(rootView: content())
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError() }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear

        addChild(hostingController)
        view.addSubview(hostingController.view)
        hostingController.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            hostingController.view.topAnchor.constraint(equalTo: view.topAnchor),
            hostingController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hostingController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            hostingController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        hostingController.didMove(toParent: self)
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        // Acquire protection when entering the view hierarchy. The handle is held by this
        // controller, so it cannot outlive the presentation.
        handle = ComposeGuard.shared.acquire(capabilities: Set([.screenshotprevention, .recordingprevention]))
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        handle?.release()
        handle = nil
    }

    /// Test hook — forces the mechanism to report failure without removing the view.
    func updateSimulateFailure(_ simulate: Bool) {
        if simulate {
            // Signal the failure observer so the sample can demonstrate both postures (M10).
            onFailure?(.screenshotprevention)
        }
    }
}
