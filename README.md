# ComposeGuard

**Screen capture protection for Compose Multiplatform.**

ComposeGuard protects sensitive content from screenshots and recordings, detects when the screen is
being captured, and hides app content in the OS task switcher — all behind a single Compose-first
API on Android 24+ and iOS 15+.

```kotlin
SecureContent {
    AccountBalance(balance = user.balance)
}
```

That's all. The boundary protects the window for as long as it is composed, and releases
automatically when it leaves — there is no teardown call to forget.

---

## Getting started in under 5 minutes

### 1. Add the dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.abdo-essam:composeguard:<version>")
}
```

### 2. Protect a screen

Wrap any composable in `SecureContent`. Protection is acquired when the boundary enters
composition and released when it leaves:

```kotlin
@Composable
fun PaymentScreen() {
    SecureContent {
        CardNumber(number = card.maskedNumber)
        Cvc(hint = "•••")
    }
}
```

> **What gets protected?** The *entire window*, not just the wrapped content. A sibling
> composable outside the boundary is still protected, while content in a separate window (a dialog
> with its own window, the other half of a split screen) is not. See
> [docs/platform-notes.md](docs/platform-notes.md#window-scoping).

### 3. Check whether it worked

```kotlin
val level = ComposeGuard.supportLevel(Capability.ScreenshotPrevention)
when (level) {
    SupportLevel.Supported      -> // protection is active
    is SupportLevel.Unsupported -> // reason in level.reason
}
```

Read the [capability matrix](docs/capability-matrix.md) before making security claims to your users.

---

## Platform capabilities

| Capability | Android | iOS |
|---|---|---|
| `ScreenshotPrevention` | ✅ Supported (24+) | ✅ Supported (15+) |
| `RecordingPrevention` | ✅ Supported (24+) | ✅ Supported (15+) |
| `CaptureDetection` | ✅ Supported (35+) | ✅ Supported (15+) |
| `ScreenshotEvents` | ✅ Supported (34+)† | ✅ Supported (15+) |
| `AppSwitcherProtection` | ✅ Supported (33+) | ✅ Supported (15+) |

† Precluded by active screenshot prevention on Android — see
[platform notes](docs/platform-notes.md#the-android-preventiondetection-exclusion).

### iOS Screenshot & Recording Prevention

On iOS, screenshot and recording prevention relies on internal secure container reparenting. The library enables this mechanism automatically when protection is requested on iOS. Applications should review Apple's App Review policies regarding sensitive content protection before publishing.

### Handling Protection Failures

If an underlying platform protection mechanism cannot be applied or breaks mid-session, ComposeGuard **does not break your user interface or hide content automatically** — preserving app usability. Instead, it emits a failure event and updates `supportLevel`:

```kotlin
// In Compose:
SecureContent(
    onProtectionFailure = { failedCapability ->
        logger.warn("Protection mechanism failed: $failedCapability")
    }
) {
    SensitiveContent()
}

// Or globally:
LaunchedEffect(Unit) {
    ComposeGuard.protectionFailures.collect { failedCapability ->
        // App-specific response: log, show security warning, navigate away, etc.
    }
}
```

---

## Detect screen recording

```kotlin
@Composable
fun SensitiveScreen() {
    val captureState by ComposeGuard.captureState.collectAsState()

    if (captureState == CaptureState.Active) {
        RecordingWarningBanner()
    }

    SecureContent {
        SensitiveContent()
    }
}
```

> `CaptureState.Inactive` means **"no evidence of capture"**, not "not being captured." Both
> platforms have documented detection gaps. Do not gate the display of sensitive content on
> `Inactive` alone — use `SecureContent` for that.

---

## Be notified after a screenshot

```kotlin
LaunchedEffect(Unit) {
    ComposeGuard.screenshotEvents.collect {
        // A screenshot was taken — log it, show a toast, etc.
        // No payload: any payload would risk carrying the content you are protecting.
    }
}
```

On Android, this capability is precluded while screenshot prevention is active. Query
`supportLevel(Capability.ScreenshotEvents)` to tell "no screenshots taken" from "unsupported here."

---

## Hide content in the app switcher

```kotlin
// Default: automatic — hidden whenever any SecureContent boundary is composed.
ComposeGuard.appSwitcherProtection = AppSwitcherProtection.Automatic

// Always hide — even with no boundary composed:
ComposeGuard.appSwitcherProtection = AppSwitcherProtection.Always

// Never hide — useful for testing:
ComposeGuard.appSwitcherProtection = AppSwitcherProtection.Disabled
```

---

## Imperative API

For architectures that are not Composables — navigation observers, background policies, ViewModels:

```kotlin
// Acquire protection from anywhere, on any thread.
val handle: ProtectionHandle = ComposeGuard.acquire()

// Later — from any thread:
handle.release()
```

Imperative and declarative claims compose through the same reference counter. Releasing a handle
does not unprotect a window that a `SecureContent` boundary still claims, and vice versa.

**`acquire()` is idempotent, not reference-counted.** Two calls with the same capability set share
one claim; releasing either releases it. This is intentional — a policy object calling `acquire()`
on every navigation would otherwise leak protection permanently.

---

## What the library does not claim

No software mechanism prevents photographing a screen with a second device, or capture on a rooted
or jailbroken device. ComposeGuard provides the best protection available on each platform.

See [docs/capability-matrix.md](docs/capability-matrix.md) for the full per-platform, per-OS-version
breakdown.

---

## Building and testing

```bash
# Full gate — matches CI merge gates
./gradlew check

# By layer
./gradlew :composeguard:allTests                # unit test suites
./gradlew :composeguard:testAndroidHostTest     # Robolectric — FLAG_SECURE assertions
./gradlew :composeguard:iosSimulatorArm64Test   # macOS arm64 only
```

Actual capture prevention cannot be verified by automated tests — only a physical device and a
real screenshot can prove the OS honoured the request.

---

## Sample app

`sample/androidApp/` is a runnable demonstration of all five capabilities against a visible marker.
Screenshot the app with protection on — the marker should be absent. Screenshot with it off — the
marker should be present. Everything else on screen (the live support readout, the event log) exists
to explain *why* a given attempt behaved the way it did.
