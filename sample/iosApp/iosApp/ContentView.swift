import SwiftUI
import ComposeShield

// MARK: - Root view

/// Demonstrates all five ComposeShield capabilities on iOS.
///
/// The marker is the proof: screenshot the app with protection on and it should be absent,
/// then again with protection off and it should be present. Everything else — live support
/// readout, event log, failure-simulation hook — exists to explain *why* a given attempt
/// behaved the way it did.
struct ContentView: View {
    // MARK: State

    @State private var boundaryActive = false
    @State private var switcherMode: TaskSwitcherProtection = .automatic
    @State private var simulateFailure = false
    @State private var captureState: CaptureState = .unknown
    @State private var eventLog: [String] = []
    @State private var supportLevels: [String: String] = [:]

    private let allCapabilities: [Capability] = [
        .screenshotprevention,
        .recordingprevention,
        .capturedetection,
        .screenshotevents,
        .taskswitcherprotection,
    ]

    private let allSwitcherModes: [TaskSwitcherProtection] = [
        .automatic,
        .always,
        .disabled,
    ]

    // MARK: Body

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    markerSection
                    preventionSection
                    detectionSection
                    switcherSection
                    supportSection
                    logSection
                }
                .padding()
            }
            .navigationTitle("ComposeShield iOS")
            .navigationBarTitleDisplayMode(.inline)
        }
        .navigationViewStyle(.stack)
        .onAppear {
            refreshSupportLevels()
            startObserving()
        }
    }

    // MARK: - Sections

    /// The visible secret — absent from a screenshot when protection is working.
    @ViewBuilder private var markerSection: some View {
        if boundaryActive {
            // SecureShieldView wraps the marker. While composed, the whole scene is protected.
            // Protection releases automatically when the view disappears.
            SecureShieldView(simulateFailure: $simulateFailure) {
                SecretMarker()
            } onFailure: { cap in
                appendLog("boundary reported failure: \(capabilityLabel(cap))")
            }
        } else {
            SecretMarker()
        }
    }

    @ViewBuilder private var preventionSection: some View {
        SampleSection("Prevention") {
            SampleToggle(
                label: "Declarative boundary (SecureShieldView)",
                on: $boundaryActive
            ) { on in
                appendLog(on ? "boundary entered hierarchy" : "boundary left hierarchy")
                refreshSupportLevels()
            }

            SampleToggle(
                label: "Simulate mechanism failure",
                on: $simulateFailure
            ) { _ in
                appendLog("failure simulation toggled")
            }
        }
    }

    @ViewBuilder private var detectionSection: some View {
        SampleSection("Detection") {
            SampleReadout("Capture state", captureStateLabel(captureState))
            SampleNote(
                "Inactive means \"no evidence of capture\", never a guarantee. " +
                "Unknown is expected at cold launch and is never coerced to Inactive."
            )
        }
    }

    @ViewBuilder private var switcherSection: some View {
        SampleSection("App switcher") {
            HStack(spacing: 8) {
                ForEach(allSwitcherModes, id: \.name) { mode in
                    SampleChip(
                        label: switcherLabel(mode),
                        selected: switcherMode == mode
                    ) {
                        switcherMode = mode
                        ComposeShield.shared.taskSwitcherProtection = mode
                        appendLog("app-switcher mode = \(switcherLabel(mode))")
                    }
                }
            }
            SampleNote("Background the app and check the task switcher.")
        }
    }

    @ViewBuilder private var supportSection: some View {
        SampleSection("Live support readout") {
            ForEach(allCapabilities, id: \.name) { cap in
                SampleReadout(capabilityLabel(cap), supportLevels[capabilityLabel(cap)] ?? "–")
            }
            SampleNote("Toggle prevention and watch levels update.")
        }
    }

    @ViewBuilder private var logSection: some View {
        SampleSection("Event log") {
            if eventLog.isEmpty {
                SampleNote("Nothing yet. Take a screenshot, or toggle something above.")
            } else {
                ForEach(eventLog.reversed(), id: \.self) { entry in
                    Text(entry)
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(.secondary)
                }
            }
        }
    }

    // MARK: - Helpers

    private func appendLog(_ message: String) {
        let entry = "[\(timestamp())] \(message)"
        eventLog.append(entry)
        if eventLog.count > 30 { eventLog.removeFirst() }
    }

    private func refreshSupportLevels() {
        for cap in allCapabilities {
            let level = ComposeShield.shared.supportLevel(capability: cap)
            let desc: String
            if level is SupportLevelSupported {
                desc = "Supported"
            } else if let unsupp = level as? SupportLevelUnsupported {
                desc = "Unsupported(\(unsupp.reason.name))"
            } else {
                desc = "Unknown"
            }
            supportLevels[capabilityLabel(cap)] = desc
        }
    }

    private func timestamp() -> String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f.string(from: Date())
    }

    private func startObserving() {
        if let initial = ComposeShield.shared.captureState.value as? CaptureState {
            self.captureState = initial
        }

        ComposeShield.shared.captureState.collect(collector: FlowCollector<CaptureState> { state in
            Task { @MainActor in
                self.captureState = state
            }
        }) { _ in }

        ComposeShield.shared.screenshotEvents.collect(collector: FlowCollector<AnyObject> { _ in
            Task { @MainActor in
                self.appendLog("screenshot taken")
            }
        }) { _ in }

        ComposeShield.shared.protectionFailures.collect(collector: FlowCollector<Capability> { cap in
            Task { @MainActor in
                self.appendLog("PROTECTION FAILED: \(self.capabilityLabel(cap))")
                self.refreshSupportLevels()
            }
        }) { _ in }
    }

    private func capabilityLabel(_ cap: Capability) -> String {
        switch cap {
        case .screenshotprevention: return "ScreenshotPrevention"
        case .recordingprevention: return "RecordingPrevention"
        case .capturedetection: return "CaptureDetection"
        case .screenshotevents: return "ScreenshotEvents"
        case .taskswitcherprotection: return "TaskSwitcherProtection"
        default: return cap.name
        }
    }

    private func switcherLabel(_ mode: TaskSwitcherProtection) -> String {
        switch mode {
        case .automatic: return "Automatic"
        case .always: return "Always"
        case .disabled: return "Disabled"
        default: return mode.name
        }
    }

    private func captureStateLabel(_ state: CaptureState) -> String {
        switch state {
        case .active: return "Active"
        case .inactive: return "Inactive"
        case .unknown: return "Unknown"
        default: return state.name
        }
    }
}

// MARK: - Flow Collector Helper

private final class FlowCollector<T>: Kotlinx_coroutines_coreFlowCollector {
    private let callback: (T) -> Void

    init(_ callback: @escaping (T) -> Void) {
        self.callback = callback
    }

    func emit(value: Any?, completionHandler: @escaping (Error?) -> Void) {
        if let typed = value as? T {
            callback(typed)
        }
        completionHandler(nil)
    }
}

// MARK: - Secret Marker

/// The visible secret. Should be absent from screenshots when screenshot prevention is active
/// and honoured by the platform.
struct SecretMarker: View {
    var body: some View {
        VStack(spacing: 6) {
            Text("TOP SECRET")
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundColor(Color(red: 0.49, green: 0.96, blue: 0.63))

            Text("4111 1111 1111 1111")
                .font(.system(size: 22, weight: .bold, design: .monospaced))
                .foregroundStyle(.white)

            Text("If you can read this in a screenshot, prevention is NOT active.")
                .font(.caption2)
                .foregroundStyle(Color(white: 0.73))
                .multilineTextAlignment(.center)
        }
        .padding(20)
        .frame(maxWidth: .infinity)
        .background(Color(white: 0.11))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        // SHIELD_TEST_SECRET_001 — stable pipeline identifier, mirrors Android contentDescription.
        // Do NOT rename without updating the iOS XCUITest detector (when ios-physical-iphone is enabled).
        .accessibilityIdentifier("SHIELD_TEST_SECRET_001")
    }
}

// MARK: - UI primitives

struct SampleSection<Content: View>: View {
    let title: String
    let content: Content

    init(_ title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.headline)
            content
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

struct SampleToggle: View {
    let label: String
    @Binding var on: Bool
    let onChange: (Bool) -> Void

    var body: some View {
        Toggle(label, isOn: Binding(
            get: { on },
            set: { v in on = v; onChange(v) }
        ))
        .font(.subheadline)
    }
}

struct SampleChip: View {
    let label: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.caption.weight(.bold))
                .foregroundStyle(selected ? .white : Color(.label))
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(selected ? Color(.label) : Color(.tertiarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 6))
        }
        .buttonStyle(.plain)
    }
}

struct SampleReadout: View {
    let label: String
    let value: String

    init(_ label: String, _ value: String) {
        self.label = label
        self.value = value
    }

    var body: some View {
        HStack {
            Text(label).font(.caption)
            Spacer()
            Text(value)
                .font(.system(size: 12, weight: .bold, design: .monospaced))
        }
    }
}

struct SampleNote: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text)
            .font(.caption2)
            .foregroundStyle(.secondary)
    }
}
