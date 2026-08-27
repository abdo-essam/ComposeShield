# Changelog

All notable changes to ComposeShield are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [0.1.0] — 2026-08-28

### Added

- **`SecureContent {}`** — declarative composable boundary that acquires and releases
  window-level protection tied to composition lifetime.
- **`ComposeShield.protect()` / `unprotect()`** — imperative API for protection outside
  composition (Application, ViewModel, Swift/Objective-C, coroutines).
- **`ProtectionHandle`** — returned by `protect()`; implements `AutoCloseable` for
  scoped protection via `use {}`.
- **`ComposeShield.captureState`** — `StateFlow<CaptureState>` reporting whether the
  screen is being recorded or mirrored.
- **`ComposeShield.screenshotEvents`** — `Flow<Unit>` emitting once per screenshot (post-hoc).
- **`ComposeShield.protectionFailures`** — `Flow<Capability>` replaying the most recent
  mechanism failure to any late collector.
- **`ComposeShield.supportLevel(Capability)`** — runtime query for whether a capability
  is available on the current device and OS version.
- **`ComposeShield.taskSwitcherProtection`** — controls OS task-switcher snapshot
  behaviour (`Automatic` / `Always` / `Disabled`).
- **`Capability`** enum: `ScreenshotPrevention`, `RecordingPrevention`,
  `CaptureDetection`, `ScreenshotEvents`, `TaskSwitcherProtection`.
- **`CaptureState`** enum: `Active`, `Inactive`, `Unknown`.
- **`SupportLevel`** sealed interface with `Supported` and `Unsupported(Reason)`.

### Platform support

| Capability | Android | iOS |
|---|---|---|
| Screenshot & recording prevention | API 24+ | iOS 15+ |
| Capture detection | API 35+ | iOS 15+ |
| Screenshot events | API 34+† | iOS 15+ |
| Task switcher protection | API 33+ (standalone) | iOS 15+ |

† Unavailable while screenshot prevention is active on Android (platform constraint).

### Notes

- Android: zero-setup initialisation via `ContentProvider` — no `Application` subclass needed.
- iOS: prevention uses secure-container reparenting (no private API).
- iOS physical device tests are `manual_required` in this release; automated physical
  validation requires a CI-connected device.
- Nothing in the public API throws. Unsupported capabilities and mechanism failures are
  reported through `supportLevel` and `protectionFailures`.

[0.1.0]: https://github.com/abdo-essam/ComposeShield/releases/tag/v0.1.0
