# ComposeShield

**Screen capture protection for Compose Multiplatform.**

[![CI](https://github.com/abdo-essam/ComposeShield/actions/workflows/ci.yml/badge.svg)](https://github.com/abdo-essam/ComposeShield/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.abdo-essam/composeshield)](https://central.sonatype.com/artifact/io.github.abdo-essam/composeshield)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

ComposeShield protects sensitive UI from screenshots and recordings, detects when the screen is
being captured, and hides app content in the OS task switcher — behind a single API on
**Android 24+** and **iOS 15+** (Kotlin 2.4+, Compose Multiplatform 1.11+).

```kotlin
// Anything composed inside the boundary is protected.
// This is your UI — rendered exactly as usual.
SecureContent {
    AccountBalance(balance = user.balance)
}
```

> `AccountBalance` stands for your own composables — it is not part of the library.

## Installation

```kotlin
dependencies {
    implementation("io.github.abdo-essam:composeshield:<version>")
}
```

In a Kotlin Multiplatform project, declare it in `commonMain` — the Android and iOS implementations
resolve automatically.

## Usage

### Protect a screen

Protection is acquired when the boundary enters composition and released when it leaves — there is
no teardown call to forget.

```kotlin
@Composable
fun PaymentScreen() {
    SecureContent {
        CardNumber(number = card.maskedNumber)
        Cvc(hint = "•••")
    }
}
```

> **What gets protected?** The *entire window*, not just the wrapped content. Content in a separate
> window (a dialog with its own window, split screen) is not protected.
> See [platform notes](docs/platform-notes.md#window-scoping).

### Detect screen recording

```kotlin
val captureState by ComposeShield.captureState.collectAsState()

if (captureState == CaptureState.Active) {
    RecordingWarningBanner()
}
```

> `Inactive` means *"no evidence of capture"*, not *"not being captured."* Never gate sensitive
> content on detection alone — use `SecureContent` for prevention.

### Be notified after a screenshot

```kotlin
LaunchedEffect(Unit) {
    ComposeShield.screenshotEvents.collect {
        // A screenshot was taken — log it, show a toast, etc.
    }
}
```

On Android this stream is unavailable while screenshot prevention is active — check
`ComposeShield.supportLevel(Capability.ScreenshotEvents)`.

### Hide content in the app switcher

```kotlin
// Automatic (default): hidden whenever any SecureContent boundary is composed.
ComposeShield.taskSwitcherProtection = TaskSwitcherProtection.Always   // always hide
ComposeShield.taskSwitcherProtection = TaskSwitcherProtection.Disabled // never hide
```

### Imperative API

For protection outside composition (`protect()` acquires, `unprotect()` releases):

```kotlin
ComposeShield.protect()
// ... later
ComposeShield.unprotect()
```

Typical places to call it: app startup, navigation routing, view models, or native Swift/Objective-C
code via `ComposeShield.shared.protect()`. Declarative and imperative claims are reference-counted,
so they never cancel each other out.

### Handle protection failures

If a mechanism fails to install or breaks mid-session, your UI keeps working and you get an event:

```kotlin
SecureContent(
    onProtectionFailure = { failedCapability -> logger.warn("Failed: $failedCapability") }
) {
    SensitiveContent()
}
```

## Platform support

| Capability | Android | iOS |
|---|---|---|
| Screenshot & recording prevention | ✅ (API 24+) | ✅ (iOS 15+) |
| Capture detection | ✅ (API 35+) | ✅ (iOS 15+) |
| Screenshot events | ✅ (API 34+)† | ✅ (iOS 15+) |
| Task switcher protection | ✅ (API 33+) | ✅ (iOS 15+) |

† Unavailable while screenshot prevention is active on Android.

Full details: [capability matrix](docs/capability-matrix.md) ·
[platform notes](docs/platform-notes.md) · [security limitations](docs/security-limitations.md)

## Limitations

No software can prevent photographing the screen with another device, or capture on rooted or
jailbroken devices. ComposeShield provides the strongest protection each OS officially offers.

## License

Apache 2.0 — see [LICENSE](LICENSE).
