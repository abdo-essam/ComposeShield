# ComposeShield

**Screen capture protection for Compose Multiplatform.**

ComposeShield protects sensitive content from screenshots and recordings, detects when the screen is
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
    implementation("io.github.abdo-essam:composeshield:<version>")
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
val level = ComposeShield.supportLevel(Capability.ScreenshotPrevention)
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
| `TaskSwitcherProtection` | ✅ Supported (33+) | ✅ Supported (15+) |

† Precluded by active screenshot prevention on Android — see
[platform notes](docs/platform-notes.md#the-android-preventiondetection-exclusion).

### iOS Screenshot & Recording Prevention

On iOS, screenshot and recording prevention relies on internal secure container reparenting. The library enables this mechanism automatically when protection is requested on iOS. Applications should review Apple's App Review policies regarding sensitive content protection before publishing.

### Handling Protection Failures

If an underlying platform protection mechanism cannot be applied or breaks mid-session, ComposeShield **does not break your user interface or hide content automatically** — preserving app usability. Instead, it emits a failure event and updates `supportLevel`:

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
    ComposeShield.protectionFailures.collect { failedCapability ->
        // App-specific response: log, show security warning, navigate away, etc.
    }
}
```

---

## Detect screen recording

```kotlin
@Composable
fun SensitiveScreen() {
    val captureState by ComposeShield.captureState.collectAsState()

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
    ComposeShield.screenshotEvents.collect {
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
ComposeShield.taskSwitcherProtection = TaskSwitcherProtection.Automatic

// Always hide — even with no boundary composed:
ComposeShield.taskSwitcherProtection = TaskSwitcherProtection.Always

// Never hide — useful for testing:
ComposeShield.taskSwitcherProtection = TaskSwitcherProtection.Disabled
```

---

## Imperative API (`protect()` & `unprotect()`)

While `SecureContent { }` is the recommended path for Compose UI, `ComposeShield.protect()` and `ComposeShield.unprotect()` provide programmatic control for architectures outside composition:

```kotlin
// Option 1: Direct singleton calls
ComposeShield.protect()
// ... later
ComposeShield.unprotect()

// Option 2: Handle-based claim
val handle = ComposeShield.protect()
// ... later
handle.unprotect()
```

### Key Use Cases

#### 1. Full-App Protection
Protect the entire app session from startup:
* **Android (`Application.onCreate`)**: `ComposeShield.protect()`
* **iOS (`App.init` / `AppDelegate`)**: `ComposeShield.shared.protect()`
* **Common KMP startup**: `ComposeShield.protect()`

#### 2. Centralized Navigation Router / Destination Listener
Protect specific routes centrally without wrapping each screen individually:
```kotlin
navController.addOnDestinationChangedListener { _, destination, _ ->
    if (destination.route in listOf("payment", "profile", "otp")) {
        ComposeShield.protect()
    } else {
        ComposeShield.unprotect()
    }
}
```

#### 3. ViewModels & Business State
Enable protection based on dynamic domain state:
```kotlin
class WalletViewModel : ViewModel() {
    fun onShowCardDetails() {
        ComposeShield.protect()
    }
    fun onHideCardDetails() {
        ComposeShield.unprotect()
    }
}
```

#### 4. Native iOS (SwiftUI / UIKit) & Android View Interop
Protect native non-Compose view controllers or legacy activities:
```swift
// Swift
override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    ComposeShield.shared.protect()
}

override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    ComposeShield.shared.unprotect()
}
```

---

### Reference Counting & Safety
Imperative and declarative claims compose through the same reference counter. Calling `ComposeShield.unprotect()` releases the imperative claim, but **never unprotects a window that a `SecureContent` boundary still claims**, and vice versa. Protection is only physically removed from the window when all claims are released.

---

## What the library does not claim

No software mechanism prevents photographing a screen with a second device, or capture on a rooted
or jailbroken device. ComposeShield provides the best protection available on each platform.

See [docs/capability-matrix.md](docs/capability-matrix.md) for the full per-platform, per-OS-version
breakdown.

---

## Building and testing

```bash
# Full gate — matches CI merge gates
./gradlew check

# By layer
./gradlew :composeshield:allTests                # unit test suites
./gradlew :composeshield:testAndroidHostTest     # Robolectric — FLAG_SECURE assertions
./gradlew :composeshield:iosSimulatorArm64Test   # macOS arm64 only
```

Actual capture prevention cannot be verified by automated tests — only a physical device and a
real screenshot can prove the OS honoured the request.

---

---

## Sample app

`sample/androidApp/` is a runnable demonstration of all five capabilities against a visible marker.
Screenshot the app with protection on — the marker should be absent. Screenshot with it off — the
marker should be present. Everything else on screen (the live support readout, the event log) exists
to explain *why* a given attempt behaved the way it did.

---

## CI/CD

ComposeShield has three GitHub Actions pipelines. All are **entirely free** for public repositories.

### Pipelines

| Pipeline | Trigger | What it does |
|---|---|---|
| **PR** (`pr.yml`) | Every pull request | Static analysis, Robolectric tests, iOS Simulator tests, ABI check |
| **On-Demand** (`on-demand.yml`) | Manual (`workflow_dispatch`) | Physical Android device via [Firebase Test Lab Spark](https://firebase.google.com/docs/test-lab) (free, 5 tests/day) + produces a `validation-report.json` |
| **Release** (`release.yml`) | Push a `v*.*.*` tag | Reuses a prior on-demand report if one exists for the commit; runs device tests otherwise; gates publication on `overall_status = pass` |

### Running the on-demand pipeline

1. Go to **Actions → On-Demand Validation → Run workflow**
2. All four jobs run in parallel (JVM, iOS Simulator, Android physical, report generation)
3. Download the `on-demand-validation-report-<run-id>` artifact to see the full report

### Reading the validation report

`validation-report.json` maps each requirement ID (`C-001`, `A-001`, …) to a test result:

```json
{
  "overall_status": "pass",
  "test_results": [
    {
      "test_id": "C-001",
      "test_name": "Screenshot protection ON — marker absent (OS enforcement confirmed)",
      "status": "passed",
      "platform": "android",
      "environment": "physical",
      "evidence_url": "https://..."
    }
  ]
}
```

- `passed` — automated test confirmed the guarantee on a real device
- `manual_required` — iOS physical tests not yet automated (see `config/device-matrix.yml`)
- `blocked` — device was unavailable for this run; not a test failure
- `failed` — the OS did **not** honour the protection request — file a bug

### Adding a new device

Edit `config/device-matrix.yml` only — no workflow YAML change is required (FR-022).

### Further reading

- [`docs/support-matrix.md`](docs/support-matrix.md) — per-platform capability and CI status
- [`docs/security-limitations.md`](docs/security-limitations.md) — known limitations and security model boundary
- [`specs/002-ci-cd-pipeline/quickstart.md`](specs/002-ci-cd-pipeline/quickstart.md) — end-to-end scenario walkthroughs
