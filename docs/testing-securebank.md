# Manual verification guide — SecureBank

`sample/securebank` is a realistic banking app that integrates ComposeShield the way a product
would: whole sensitive screens, not demo toggles. Use this checklist to verify on real hardware
that content is **completely blocked** from every capture path.

## Install & launch

```bash
./gradlew :sample:securebank:installDebug
adb shell am start -n io.github.composeshield.securebank/.MainActivity
```

Sign in with any credentials. The app opens on **Your accounts**.

### iOS (`sample/securebank-ios`)

```bash
xcodebuild -project sample/securebank-ios/securebank-ios.xcodeproj -scheme securebank \
  -destination "platform=iOS Simulator,name=iPhone 16 Pro" \
  -derivedDataPath build/DerivedData-sb build CODE_SIGNING_ALLOWED=NO
```

The Xcode shell phase links the Kotlin framework automatically. Verify on a **physical iPhone**
(simulators do not enforce screenshot prevention): wrap targets are Login and Card details;
task-switcher protection is `.always`.

## The protection model

| Layer | Mechanism | Blocks |
|---|---|---|
| Sensitive screens (`SecureContent`) | `FLAG_SECURE`, window-scoped | screenshots, screen recording/casting, recents thumbnail |
| Task switcher | `TaskSwitcherProtection.Always` | recents preview even between screens |
| Session lock (background) | lock overlay + imperative `protect()` | renders nothing sensitive while backgrounded |
| Recording detection | `captureState == Active` | auto-masks balances/CVV when OS reports recording |

## Checklist — protection ON (default)

Run through every item; each must hold:

- [ ] **Screenshot**: take a system screenshot on Accounts / Virtual card / Transactions.
      Result: blocked (black image saved, or OS refuses with "can't capture screenshot").
- [ ] **Screen recording**: start a screen recording, scroll the card screen (reveal CVV first).
      Result: recording is entirely black for as long as any sensitive screen is visible.
- [ ] **Recents thumbnail**: open the app switcher while viewing balances.
      Result: preview is blank/black.
- [ ] **Background + return**: press Home, then reopen SecureBank.
      Result: full-screen session lock appears — no balances visible until unlocked.
- [ ] **Cast/second display** (if available): mirror the phone to a TV/monitor.
      Result: secure screens show black on the external display.

## Checklist — demo mode (negative control)

Security → *Disable ALL protection* → ON. Repeat the same captures:

- [ ] Screenshot succeeds and shows balances/card/CVV.
- [ ] Screen recording shows everything.
- [ ] Recents preview shows the real UI.
- [ ] Session lock still appears on backgrounding (app behaviour), but captures of it are allowed.

Flip demo mode OFF again and re-check one screenshot to confirm protection returns.

> iOS note: simulators do not enforce screenshot prevention (`docs/support-matrix.md`). Verify
> `sample/securebank-ios` on a physical iPhone only.

## Automated equivalents

```bash
./gradlew :sample:securebank:testDebugUnitTest          # policy/navigation logic
./gradlew :sample:securebank:connectedDebugAndroidTest  # FLAG_SECURE vs framebuffer proof
```

The connected tests assert both directions on-device: FLAG_SECURE applied + dark framebuffer when
protected, and a bright capturable framebuffer in demo mode (so a broken pipeline cannot pass
vacuously).
