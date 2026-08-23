import SwiftUI
import ComposeShield

// MARK: - Root

enum BankScreen {
    case login
    case accounts
    case card
}

struct BankContentView: View {
    @State private var screen: BankScreen = .login
    @State private var demoMode = false

    var body: some View {
        VStack(spacing: 0) {
            content
        }
        .background(Color(white: 0.95))
        .onAppear(perform: applyPosture)
        .onChange(of: demoMode) { _ in applyPosture() }
    }

    /// The whole-screen boundary: while any sensitive route is visible the window is protected.
    @ViewBuilder private var content: some View {
        switch screen {
        case .login:
            protectedView { LoginView(onSignedIn: { screen = .accounts }) }
        case .accounts:
            protectedView {
                AccountsView(
                    onOpenCard: { screen = .card },
                    onToggleDemo: { demoMode.toggle() },
                    demoMode: demoMode,
                )
            }
        case .card:
            protectedView { CardDetailView(onBack: { screen = .accounts }) }
        }
    }

    @ViewBuilder private func protectedView<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        if demoMode {
            VStack(spacing: 8) {
                Text("DEMO MODE — capture protection disabled")
                    .font(.caption.weight(.bold))
                    .foregroundColor(.white)
                    .padding(6)
                    .frame(maxWidth: .infinity)
                    .background(Color.red)
                content()
            }
        } else {
            SecureShieldView(simulateFailure: .constant(false)) {
                content()
            } onFailure: { capability in
                print("SecureBank: protection failure for \(capability.name)")
            }
        }
    }

    private func applyPosture() {
        ComposeShield.shared.taskSwitcherProtection = demoMode ? .disabled : .always
    }
}

// MARK: - Login

struct LoginView: View {
    let onSignedIn: () -> Void

    @State private var username = ""
    @State private var password = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Spacer().frame(height: 64)
            Text("SecureBank")
                .font(.largeTitle.bold())
                .foregroundColor(Color(red: 0.04, green: 0.15, blue: 0.25))
            Text("Mobile banking").foregroundColor(.gray)

            field("Username", text: $username, secure: false)
            field("Password", text: $password, secure: true)

            Button(action: onSignedIn) {
                Text("Sign in")
                    .fontWeight(.bold)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color(red: 0.11, green: 0.5, blue: 0.23))
                    .foregroundColor(.white)
                    .cornerRadius(8)
            }
            Text("Any credentials work. This screen is capture-protected at all times.")
                .font(.caption2)
                .foregroundColor(.gray)
            Spacer()
        }
        .padding(24)
    }

    private func field(_ label: String, text: Binding<String>, secure: Bool) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption).foregroundColor(.gray)
            Group {
                if secure {
                    SecureField("", text: text)
                } else {
                    TextField("", text: text)
                }
            }
            .padding(10)
            .background(Color(white: 0.96))
            .cornerRadius(6)
        }
    }
}

// MARK: - Accounts

struct AccountsView: View {
    let onOpenCard: () -> Void
    let onToggleDemo: () -> Void
    let demoMode: Bool

    private let accounts: [(name: String, number: String, balance: String)] = [
        ("Everyday Checking", "•• 4821", "$4,812.90"),
        ("High-Interest Savings", "•• 7734", "$18,250.00"),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Your accounts").font(.title2.bold())
                card { 
                    Text("Total balance").font(.caption).foregroundColor(.gray)
                    Text("$23,062.90").font(.system(size: 30, weight: .bold))
                        .foregroundColor(Color(red: 0.04, green: 0.15, blue: 0.25))
                }
                ForEach(accounts, id: \.name) { account in
                    card {
                        Text(account.name).font(.headline)
                        Text(account.number).font(.caption).foregroundColor(.gray)
                        Text(account.balance).font(.title3.bold()).foregroundColor(.green)
                    }
                }
                Button(action: onOpenCard) {
                    Text("Virtual card")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color(red: 0.11, green: 0.5, blue: 0.23))
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
                Button(action: onToggleDemo) {
                    Text(demoMode ? "Restore protection" : "Disable ALL protection (demo)")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(demoMode ? Color.green : Color.red.opacity(0.85))
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
                Text("While this screen is visible the whole window blocks screenshots and recording.")
                    .font(.caption2).foregroundColor(.gray)
            }
            .padding(16)
        }
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) { content() }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white)
            .cornerRadius(8)
    }
}

// MARK: - Card details

struct CardDetailView: View {
    let onBack: () -> Void

    @State private var cvvRevealed = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Button(action: onBack) {
                    Label("Accounts", systemImage: "chevron.left").font(.subheadline)
                }

                RoundedRectangle(cornerRadius: 16)
                    .fill(Color(white: 0.07))
                    .frame(height: 190)
                    .overlay(
                        VStack(alignment: .leading, spacing: 18) {
                            HStack {
                                Text("SecureBank Debit").font(.subheadline.bold()).foregroundColor(.white)
                                Spacer()
                                Text("VISA").font(.headline).foregroundColor(.white)
                            }
                            Text("4111 1111 1111 1111")
                                .font(.system(size: 20, weight: .bold, design: .monospaced))
                                .foregroundColor(.white)
                            HStack {
                                Text("A. ESSAM").font(.caption).foregroundColor(.gray)
                                Spacer()
                                Text("EXP 09/29").font(.caption).foregroundColor(.gray)
                            }
                        }
                        .padding(20)
                    )

                card(title: "Card details") {
                    HStack {
                        Text("CVV").foregroundColor(.gray)
                        Spacer()
                        Text(cvvRevealed ? "123" : "•••").bold()
                    }
                    Toggle("Reveal CVV", isOn: $cvvRevealed)
                    Text("Full number and CVV are visible only while the window blocks all capture.")
                        .font(.caption2).foregroundColor(.gray)
                }
            }
            .padding(16)
        }
    }

    private func card<Content: View>(title: String, @ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.headline)
            content()
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white)
        .cornerRadius(8)
    }
}
