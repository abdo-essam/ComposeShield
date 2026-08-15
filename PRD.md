# ComposeShield PRD

> **Project Name:** ComposeShield  
> **Type:** Open Source Kotlin Multiplatform Library  
> **Status:** MVP / Version 1.0  
> **Platforms:** Android, iOS (Compose Multiplatform)

---

# 1. Overview

ComposeShield is an open-source Kotlin Multiplatform library that provides a unified API for protecting sensitive UI content from screenshots, screen recording, screen sharing, and other forms of visual capture across Android and iOS.

The library abstracts platform-specific implementations behind a simple, Compose-first API, allowing developers to protect their applications with minimal code while maintaining a consistent developer experience across platforms.

ComposeShield is designed specifically for Kotlin Multiplatform and Compose Multiplatform applications and aims to become the standard security library for protecting sensitive UI content.

---

# 2. Problem Statement

Applications such as banking, healthcare, enterprise, password managers, messaging, and digital identity apps frequently display sensitive information that should not be easily captured.

Today developers face several problems:

- Android requires manually enabling `FLAG_SECURE`.
- iOS provides no official API to prevent screenshots.
- iOS implementations rely on undocumented UIKit workarounds.
- Kotlin Multiplatform developers must maintain two completely different implementations.
- There is currently no mature Compose Multiplatform library that abstracts these platform differences.

This leads to duplicated code, inconsistent implementations, and increased maintenance costs.

---

# 3. Vision

Become the standard security library for Compose Multiplatform applications by providing a clean, unified, and extensible API for protecting sensitive UI across Android and iOS.

ComposeShield should feel as simple and intuitive as libraries such as:

- Koin
- Coil
- Navigation Compose
- Voyager

Developers should never need to understand the underlying platform-specific implementations.

---

# 4. Goals

- Provide a single API for Android and iOS.
- Be Compose-first.
- Fully support Kotlin Multiplatform.
- Require minimal setup.
- Hide platform-specific implementations.
- Be lifecycle-aware.
- Offer both declarative and imperative APIs.
- Support future security features without breaking existing APIs.
- Maintain a lightweight dependency footprint.
- Be production-ready for enterprise applications.

---

# 5. Target Audience

- Kotlin Multiplatform Developers
- Compose Multiplatform Developers
- Android Developers
- Banking Applications
- Healthcare Applications
- Enterprise Applications
- Government Applications
- Authentication Applications
- Password Managers
- Secure Messaging Applications

---

# 6. Supported Platforms

## Version 1

- Android
- iOS

## Future

- Desktop
- macOS
- Web (best-effort)
- visionOS

---

# 7. Core Features

## 7.1 Screenshot Protection

Protect application content from screenshots.

### Android

### iOS

---

## 7.2 Screen Recording Protection

Detect when the screen is being recorded and optionally hide or blur sensitive content.

---

## 7.3 Secure Composable

Protect an entire Composable hierarchy.

Example

```kotlin
SecureScreen {
    PaymentScreen()
}
```

---

## 7.4 Global Protection

Enable protection for the entire application.

```kotlin
ComposeShield.enable()
```

---

## 7.5 Per-Screen Protection

Protect only selected screens.

```kotlin
ComposeShield.enable()

// ...

ComposeShield.disable()
```

or

```kotlin
SecureScreen {
    LoginScreen()
}
```

---

## 7.6 Screen Recording State

Expose the current recording status.

```kotlin
ComposeShield.isRecording
```

---

## 7.7 Screenshot Events

Notify applications when screenshots occur (where supported).

```kotlin
ComposeShield.screenshotEvents
```

---

## 7.8 Automatic Blur

Automatically blur sensitive content while screen recording is active.

---

## 7.9 Lifecycle Awareness

Automatically enable and disable protection as screens enter and leave composition.

---

## 7.10 Compose Multiplatform Support

Native support for

- Compose Android
- Compose iOS

No UIKit or Activity knowledge should be required.

---

# 8. Future Features

## Secure Regions

Protect only part of the UI.

```kotlin
SensitiveContent {
    CreditCardNumber()
}
```

---

## Watermark Overlay

Display dynamic watermarks over protected content.

Example

```
John Doe
10:45 PM
```

Useful for enterprise applications and leak prevention.

---

## Secure Dialogs

Protect popup dialogs independently.

---

## Secure Bottom Sheets

Protect modal bottom sheets.

---

## Navigation Integration

Automatically protect selected destinations.

---

## AirPlay & External Display Detection

Detect screen sharing to external displays where supported.

---

## Custom Placeholder UI

Replace protected content with custom UI.

```kotlin
SecureScreen(
    placeholder = {
        LockedContent()
    }
) {
    Content()
}
```

---

# 9. Non-Functional Requirements

- Kotlin Multiplatform First
- Compose First
- Lightweight
- Zero Reflection
- Thread Safe
- Lifecycle Aware
- High Performance
- Minimal Memory Allocation
- Production Ready
- Fully Documented
- Comprehensive Unit Tests

---

# 10. Public API

## Imperative API

```kotlin
ComposeShield.enable()

ComposeShield.disable()

ComposeShield.isEnabled

ComposeShield.isRecording

ComposeShield.screenshotEvents
```

---

## Declarative API

```kotlin
SecureScreen {
    Content()
}
```

Advanced

```kotlin
SecureScreen(
    screenshots = true,
    recording = true,
    blurWhileRecording = true
) {
    Content()
}
```

---

# 11. Architecture

```
composeshield/
│
├── composeshield/
│   ├── commonMain/
│   ├── androidMain/
│   ├── iosMain/
│
├── sample/
│
├── benchmarks/
│
├── docs/
│
└── README.md
```

---

# 12. Platform Implementation

## Android

Internally ComposeShield should

- Obtain the current Window
- Enable or disable `FLAG_SECURE`
- Observe lifecycle changes
- Integrate seamlessly with Compose

No reflection should be used.

---

## iOS

Internally ComposeShield should

- Create a secure `UITextField`
- Enable `isSecureTextEntry`
- Extract the secure rendering container
- Embed the Compose UIView into the secure container
- Observe screen recording state
- Listen for screenshot notifications
- Hide all UIKit implementation details

Consumers should never interact directly with UIKit.

---

# 13. Developer Experience

The library should require almost no configuration.

Example

```kotlin
@Composable
fun App() {
    SecureScreen {
        HomeScreen()
    }
}
```

No platform-specific code should be required.

---

# 14. Success Metrics

- Integration in under 5 minutes.
- Minimal API surface.
- Consistent behavior across Android and iOS.
- Strong community adoption.
- Excellent documentation.
- Sample applications for Android and iOS.
- Stable production performance.

---

# 15. Long-Term Vision

ComposeShield aims to become the standard security toolkit for Kotlin Multiplatform applications.

Beyond screenshot prevention, it will evolve into a comprehensive UI security framework providing:

- Screenshot Protection
- Screen Recording Protection
- Secure Composables
- Watermarking
- Sensitive Content Regions
- Screen Sharing Detection
- AirPlay Detection
- External Display Detection
- Secure Navigation Integration
- Enterprise Security Features

The goal is to give developers a single, modern, Compose-native API for protecting sensitive UI while abstracting all platform-specific complexity behind a clean and intuitive developer experience.
