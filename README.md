<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/logo-light.png">
    <img src="assets/logo-dark.png" alt="ComposeShield Logo" width="380">
  </picture>
</p>

<p align="center">
  <strong>Comprehensive, multiplatform screen-capture prevention and detection for Kotlin Multiplatform, Android, and iOS.</strong>
</p>

<p align="center">
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF.svg?logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://central.sonatype.com/artifact/io.github.abdo-essam/composeshield"><img src="https://img.shields.io/maven-central/v/io.github.abdo-essam/composeshield?color=blue" alt="Maven Central"></a>
  <a href="https://github.com/abdo-essam/ComposeShield/actions/workflows/ci.yml"><img src="https://img.shields.io/github/actions/workflow/status/abdo-essam/ComposeShield/ci.yml?branch=main" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="Apache 2.0 License"></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Android-API%2024%2B-3DDC84.svg?logo=android&logoColor=white" alt="Android"></a>
  <a href="https://developer.apple.com/ios/"><img src="https://img.shields.io/badge/iOS-15.0%2B-000000.svg?logo=apple&logoColor=white" alt="iOS"></a>
</p>

---

## Overview

**ComposeShield** is a lightweight, zero-overhead screen-capture protection library for Kotlin Multiplatform (Android & iOS). It blocks screenshots and screen recordings, detects active capture sessions, and automatically obscures app previews in the OS task switcher / recents menu.

Whether you need to shield an **entire application** with a single line of code, guard **individual sensitive screens**, or manage protection imperatively from ViewModels, traditional Views, or Swift/UIKit, ComposeShield delivers a unified, lifecycle-safe API backed by each operating system's strongest native security mechanisms.

### Key Features

- 🛡️ **Full-App or Granular Protection** — Wrap your root UI once to secure the entire app (including dialogs and popups), or scope protection to specific screens.
- 🧩 **Declarative & Composition-Aware** — Use `SecureContent {}` to automatically acquire and release protection with Compose lifecycle—never leak protection or forget teardown.
- ⚡ **Multi-Paradigm Support** — Native integration across Jetpack Compose, Compose Multiplatform, Android Views/XML, and Native iOS (Swift/UIKit).
- 📡 **Real-Time Capture Detection** — Reactive `StateFlow` streams for live screen recording, mirroring, and external display detection.
- 📸 **Screenshot Event Stream** — Receive post-hoc screenshot notifications without exposing sensitive screen contents.
- 🪟 **Automatic Child Window Inheritance** — Compose `Dialog`, `Popup`, and `ModalBottomSheet` automatically inherit protection without boilerplate.
- 🔒 **Fail-Safe by Design** — Built to never crash host applications. If an OS mechanism is unavailable, your UI continues to render smoothly and emits diagnostic failure events.

---

## Quick Start: Protect Your Entire App

Protecting every screen in your application requires just a single root wrapper:

```kotlin
@Composable
fun App() {
    // Secures all screens, dialogs, popups, and recents switcher thumbnails
    SecureContent {
        AppContent()
    }
}
```

Or initialize app-wide protection imperatively at startup:

```kotlin
// Android (Application.onCreate), iOS (AppDelegate / SwiftUI App.init), or Common setup:
ComposeShield.protect()
```

---

## Installation

Add the dependency to your project's `build.gradle.kts`:

### Kotlin Multiplatform (`commonMain`)
```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.abdo-essam:composeshield:0.1.0")
        }
    }
}
```

### Android-Only (Jetpack Compose / Views)
```kotlin
dependencies {
    implementation("io.github.abdo-essam:composeshield:0.1.0")
}
```

---

## Usage Guide

### 1. Declarative Protection (`SecureContent`)

`SecureContent` is the recommended approach for Jetpack Compose and Compose Multiplatform. Protection is acquired when entering composition and automatically released when disposed.

#### Protect the Entire Application
```kotlin
@Composable
fun App() {
    SecureContent {
        AppNavHost() // Secures the entire window hierarchy
    }
}
```

#### Protect a Single Screen
```kotlin
@Composable
fun SensitiveScreen() {
    SecureContent {
        SensitiveContent()
    }
}
```

> **Window Scoping Note:** Mobile operating systems apply hardware-level capture protection at the *window level*. When `SecureContent` is active, the entire window hosting the composable is protected against screenshots and recordings.

---

### 2. Dialog and Popup Protection

In Jetpack Compose and Compose Multiplatform, child surfaces like `Dialog`, `Popup`, and Material3 `ModalBottomSheet` create separate platform windows.

ComposeShield automatically attaches protection to any child window created inside a `SecureContent` block—no extra wrappers required:

```kotlin
SecureContent {
    Column {
        SensitiveContent()

        if (showSheet) {
            // ModalBottomSheet creates its own window — automatically secured!
            ModalBottomSheet(onDismissRequest = { showSheet = false }) {
                BottomSheetContent()
            }
        }
    }
}
```

---

### 3. Imperative API (Views, Swift, ViewModels)

For non-composable architectures, Native Android (XML), Native iOS (Swift), or business logic controllers, use the imperative API:

#### Native Android (Activity / Fragment)
```kotlin
class SensitiveActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensitive)
        ComposeShield.protect() // Works with standard Android Views
    }
}
```

#### Application Startup (App-Wide Policy)
```kotlin
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ComposeShield.protect()
    }
}
```

#### Native iOS (Swift / UIKit)
```swift
import ComposeShield

// In your AppDelegate, SceneDelegate, or UIViewController
ComposeShield.shared.protect(capabilities: nil)

// To release protection
ComposeShield.shared.unprotect(capabilities: nil)
```

#### ViewModel / Scoped Coroutines
```kotlin
class SensitiveViewModel : ViewModel() {
    private val protectionHandle = ComposeShield.protect()

    override fun onCleared() {
        protectionHandle.unprotect()
    }
}

// Or execute a block with automatic RAII scoping:
suspend fun executeSensitiveOperation(): Result = ComposeShield.protect().use {
    api.fetchConfidentialData()
}
```

---

### 4. Detecting Screen Recording & Streaming

Observe live screen recording, mirroring, and external display connections reactively:

```kotlin
@Composable
fun SecurityMonitor() {
    val captureState by ComposeShield.captureState.collectAsState()

    if (captureState == CaptureState.Active) {
        RecordingWarningBanner()
    }
}
```

> **Important:** `CaptureState.Inactive` signifies *"no active capture detected"*, not an absolute guarantee. Always rely on `SecureContent` / `protect()` for prevention rather than gating sensitive UI on detection alone.

---

### 5. Screenshot Event Notifications

Listen for post-hoc screenshot events (e.g. to log security audit events or show advisory notices):

```kotlin
LaunchedEffect(Unit) {
    ComposeShield.screenshotEvents.collect {
        analytics.logSecurityEvent("screenshot_captured")
    }
}
```

*(Note: On Android, screenshot detection callbacks are precluded by the OS while `FLAG_SECURE` prevention is active.)*

---

### 6. Resilience & Failure Handling

If an underlying OS mechanism is unavailable or fails at runtime, ComposeShield ensures the app remains stable and notifies your diagnostic handlers:

```kotlin
SecureContent(
    onProtectionFailure = { failedCapability ->
        logger.warn("Protection mechanism unavailable for $failedCapability")
    }
) {
    SensitiveContent()
}
```

---

## Unified Lifetime Model

ComposeShield uses a unified, reference-counted state engine. Imperative claims (`ComposeShield.protect()`) and declarative boundaries (`SecureContent {}`) interoperate seamlessly without race conditions:

| Approach | Context | Lifetime | Teardown |
|---|---|---|---|
| **`SecureContent {}`** | Jetpack Compose / Compose Multiplatform | Tied to composition | Automatic on disposal |
| **`ComposeShield.protect()`** | Application, Activity, Swift, ViewModel | Explicit handle | `handle.unprotect()` or `use {}` |

---

## Platform Support & Mechanisms

| Capability | Android | Android Mechanism | iOS | iOS Mechanism |
|---|:---:|---|:---:|---|
| **Screenshot Prevention** | ✅ API 24+ | `FLAG_SECURE` | ✅ iOS 15+ | Secure layer reparenting |
| **Recording Prevention** | ✅ API 24+ | `FLAG_SECURE` | ✅ iOS 15+ | Secure layer reparenting |
| **Capture Detection** | ✅ API 35+ | `ScreenRecordingCallback` | ✅ iOS 15+ | `UITraitSceneCaptureState` |
| **Screenshot Events** | ✅ API 34+ | `ScreenCaptureCallback` | ✅ iOS 15+ | `userDidTakeScreenshotNotification` |
| **Task Switcher Obscuring** | ✅ API 33+ | `setRecentsScreenshotEnabled` | ✅ iOS 15+ | Scene transition overlay |

 *On Android, screenshot event callbacks are intentionally suppressed by the OS while `FLAG_SECURE` is active.*

For in-depth architecture and platform notes, see:
- 📖 [Platform Notes](docs/platform-notes.md)
- 📊 [Capability Matrix](docs/capability-matrix.md)
- 🛡️ [Security Limitations](docs/security-limitations.md)

---

## Security Model & Boundary

ComposeShield enforces the strongest official, review-compliant protections provided by Android and iOS. However, please note the physical and platform security boundaries:
- **Physical Observation:** No software can prevent a screen from being photographed by an external camera.
- **Rooted / Jailbroken Devices:** Custom ROMs, Frida scripts, or LSPosed modules can hook OS-level APIs and bypass platform security flags.
- **Simulators:** Simulators and emulators render directly to desktop framebuffers and do not enforce hardware-level capture restrictions. Always validate protection on physical devices.

---

## Support & Sponsoring 💖

If ComposeShield helps protect your application or team, please consider [sponsoring the project on GitHub](https://github.com/sponsors/abdo-essam) or starring the repository ⭐!

---

## Vulnerability Reporting & Security

Please **do not** file public GitHub issues for security vulnerabilities. Report security findings directly to **abdo-essam@hotmail.com** or submit a confidential report via [GitHub Private Vulnerability Reporting](https://github.com/abdo-essam/ComposeShield/security/advisories/new).

---

## License

```
Copyright 2026 Abdo Essam

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
