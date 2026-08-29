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
  <a href="https://github.com/sponsors/abdo-essam"><img src="https://img.shields.io/badge/Sponsor-%E2%99%A5-ff69b4.svg" alt="Sponsor"></a>
</p>

## What is ComposeShield? ✨

ComposeShield is a lightweight screen-capture protection library for Kotlin Multiplatform,
built for Android and iOS. It blocks screenshots and screen recordings, detects active capture,
and hides app content in the OS task switcher — behind a single API on
**Android 24+** and **iOS 15+**.

It is designed for seamless integration across all mobile environments:
- **Jetpack Compose / Compose Multiplatform** — declarative protection that follows composition.
- **Native Android (XML/Views)** — imperative protection for traditional View-based apps.
- **Native iOS (Swift/UIKit)** — native Swift support for UIKit-based applications.

- **Full-App or Per-Screen Protection** — wrap your root UI once to secure the entire app, or scope to individual screens.
- **Declarative** — wrap any UI in a `SecureContent` boundary; protection follows composition.
- **Cross-platform** — one API on Android and iOS, backed by each platform's strongest official mechanism.
- **Observable** — capture state, screenshot events, and protection failures are exposed as flows.
- **Fail-safe by design** — if a mechanism fails, your UI keeps working and you get an event.

```kotlin
// Protect your entire application with a single root wrapper:
SecureContent {
    AppContent()
}
```

> `AppContent` stands for your own root navigation or composables — it is not part of the library.

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

Because protection is scoped to the *window*, wrapping your root composable secures every screen,
dialog, and popup across the entire application:

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
ComposeShield.protect() // e.g. from Application.onCreate, AppDelegate, or app init
```

`AppNavHost` stands for your own navigation host — it is not part of the library.

### Protect a single screen

Protection is acquired when the boundary enters composition and released when it leaves — there is
no teardown call to forget.

```kotlin
@Composable
fun SensitiveScreen() {
    SecureContent {
        SensitiveContent()
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

`SecureContent {}` is the right choice whenever you're inside a `@Composable`. The imperative API
exists for **Native iOS (Swift)**, **Android XML (Views)**, and cases where there is no composable context:

**Native Android XML (Activity/Fragment)**
```kotlin
class SensitiveActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensitive)
        ComposeShield.protect() // Works with standard Android Views
    }
}
```

**App-wide policy from `Application.onCreate`**
```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ComposeShield.protect()
    }
}
```

**Native Swift / Objective-C**
```swift
// In your iOS App or ViewController
ComposeShield.shared.protect(capabilities: nil)
// ... later
ComposeShield.shared.unprotect(capabilities: nil)
```

**ViewModel or navigation observer**
```kotlin
class SensitiveViewModel : ViewModel() {
    private val handle = ComposeShield.protect()

    override fun onCleared() {
        handle.unprotect()
    }
}
```

**Scoped protection with `use {}`** — automatically released when the block exits:
```kotlin
suspend fun fetchSensitiveData(): Data {
    return ComposeShield.protect().use {
        api.fetchSensitiveData()
    }
}
```

Both paths share **one reference counter**. If a `SecureContent` boundary is composed *and*
`protect()` was called from a ViewModel, protection lifts only once both are released.

| | `SecureContent {}` | `protect()` / imperative |
|---|---|---|
| **Where** | Inside `@Composable` | Anywhere (XML, Swift, ViewModel, Application) |
| **Lifetime** | Tied to composition | Manual or `use {}` scope |
| **Teardown** | Automatic | `unprotect()` or `use {}` |

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

**v1 iOS physical device tests** are `manual_required` — automated physical validation
requires a CI-connected iPhone, which is not yet in the pipeline. Android physical tests
run automatically on Firebase Test Lab on every release. iOS physical coverage will be
automated in a future release.

## Support & Sponsoring 💖

If ComposeShield helps protect your application or team, please consider [sponsoring the project on GitHub](https://github.com/sponsors/abdo-essam) or starring the repository ⭐!

## Contact

Maintainer: **Abdo Essam** — abdo-essam@hotmail.com

For security vulnerabilities, please use [GitHub's private vulnerability reporting](https://github.com/abdo-essam/ComposeShield/security/advisories/new)
or email directly. Do not file public issues for security bugs.

## License

Apache 2.0 — see [LICENSE](LICENSE).
