<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/logo-light.png">
    <img src="assets/logo-dark.png" alt="ComposeShield" width="360">
  </picture>
</p>

<p align="center">
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.4%2B-7F52FF.svg?logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://github.com/abdo-essam/ComposeShield/actions/workflows/ci.yml"><img src="https://github.com/abdo-essam/ComposeShield/actions/workflows/ci.yml/badge.svg" alt="GitHub Actions"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="Apache 2.0 License"></a>
  <a href="https://central.sonatype.com/artifact/io.github.abdo-essam/composeshield"><img src="https://img.shields.io/maven-central/v/io.github.abdo-essam/composeshield" alt="Maven Central"></a>
</p>

## What is ComposeShield? ✨

ComposeShield is a lightweight screen-capture protection library for Kotlin Multiplatform,
built for Android and iOS. It blocks screenshots and screen recordings, detects active capture,
and hides app content in the OS task switcher — behind a single declarative API on
**Android 24+** and **iOS 15+** (Kotlin 2.4+, Compose Multiplatform 1.11+).

- **Declarative** — wrap any UI in a `SecureContent` boundary; protection follows composition,
  so there is nothing to tear down and no lifecycle to get wrong.
- **Cross-platform** — one API on Android and iOS, backed by each platform's strongest official
  mechanism (`FLAG_SECURE` / screen-capture detection on Android, secure-text-entry containers on iOS).
- **Observable** — capture state, screenshot events, and protection failures are exposed as flows.
- **Fail-safe by design** — if a mechanism fails, your UI keeps working and you get an event;
  nothing ever throws.

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
    implementation("io.github.abdo-essam:composeshield:0.1.0")
}
```

In a Kotlin Multiplatform project, declare it in `commonMain` — the Android and iOS implementations
resolve automatically.

## Usage

### Protect the entire app

Because protection is scoped to the *window*, wrapping your root composable secures every screen
in the application:

```kotlin
@Composable
fun App() {
    SecureContent {
        AppNavHost()
    }
}
```

Prefer to control it outside composition? Acquire protection once at startup instead:

```kotlin
ComposeShield.protect() // e.g. from Application.onCreate / app init
```

`AppNavHost` stands for your own navigation host — it is not part of the library.

### Protect a single screen

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

> **What gets protected?** While a `SecureContent` boundary is composed, the *entire window* is
> protected — including siblings outside the boundary. Content in a separate window (a dialog with
> its own window, split screen) is not protected.
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

### Imperative API

For protection outside composition (`protect()` acquires, `unprotect()` releases):

```kotlin
ComposeShield.protect()
// ... later
ComposeShield.unprotect()
```

Typical places to call it: app startup, navigation routing, view models, or native Swift/Objective-C
code via `ComposeShield.shared.protect()`. Imperative protection coexists with any composed
`SecureContent` boundaries — neither ever cancels the other out.

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
