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

`SecureContent {}` is the right choice whenever you're inside a `@Composable` — protection
follows composition automatically and there is nothing to release manually. The imperative API
exists for the cases where there is **no composable context**:

**App-wide policy from `Application.onCreate`**

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ComposeShield.protect() // composables don't exist yet
    }
}
```

**ViewModel or navigation observer**

```kotlin
class PaymentViewModel : ViewModel() {
    private val handle = ComposeShield.protect()

    override fun onCleared() {
        handle.unprotect()
    }
}
```

**Scoped protection with `use {}`** — automatically released when the block exits, even on
exception:

```kotlin
suspend fun fetchSensitiveData(): Data {
    return ComposeShield.protect().use {
        api.fetchSensitiveData()
    }
}
```

**Native Swift / Objective-C**

```swift
ComposeShield.shared.protect()
// ... later
ComposeShield.shared.unprotect()
```

Both paths share **one reference counter**. If a `SecureContent` boundary is composed *and*
`protect()` was called from a ViewModel, protection lifts only once both are released — neither
can accidentally unprotect the other.

| | `SecureContent {}` | `protect()` / imperative |
|---|---|---|
| **Where** | Inside `@Composable` | Anywhere (ViewModel, Application, Swift, coroutines) |
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
