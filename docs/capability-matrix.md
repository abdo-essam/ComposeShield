# Capability Matrix

Every ComposeGuard capability resolves a `SupportLevel` per platform, per OS version, and — for one
capability on Android — per currently-active capability. This page is the published form of that
matrix.

It is a **contract, not documentation**: `CapabilityMatrixTest` asserts that runtime
`ComposeGuard.supportLevel()` matches these rows on every tier (SC-005).

> **Support is evaluated at call time, never cached at startup.** Read [Platform Notes](platform-notes.md) for details.

---

## Cross-platform summary

| Capability | Android | iOS |
|---|---|---|
| `ScreenshotPrevention` | Supported (24+) | Supported (15+) |
| `RecordingPrevention` | Supported (24+) | Supported (15+) |
| `CaptureDetection` | Supported (35+) | Supported (15+) |
| `ScreenshotEvents` | Supported (34+), precluded by active prevention | Supported (15+) |
| `AppSwitcherProtection` | Supported (24+ with prevention; 33+ standalone) | Supported (15+) |

---

## Android (minSdk 24)

| Capability | Mechanism | Supported from | Permission | Level |
|---|---|---|---|---|
| `ScreenshotPrevention` | `FLAG_SECURE` | 24 (all) | none | `Supported` |
| `RecordingPrevention` | `FLAG_SECURE` | 24 (all) | none | `Supported` |
| `CaptureDetection` — recording | `WindowManager.addScreenRecordingCallback` | **35** | `DETECT_SCREEN_RECORDING` (normal) | `Supported` ≥35, else `Unsupported(OsVersionTooLow)` |
| `CaptureDetection` — external display | `DisplayManager` + `DisplayListener` | 24 | none | `Supported` |
| `ScreenshotEvents` | `Activity.registerScreenCaptureCallback` | **34** | `DETECT_SCREEN_CAPTURE` (normal) | see conflict below |
| `AppSwitcherProtection` — with prevention | `FLAG_SECURE` (covers recents) | 24 | none | `Supported` |
| `AppSwitcherProtection` — standalone | `setRecentsScreenshotEnabled(false)` | **33** | none | `Supported` ≥33, else `Unsupported(OsVersionTooLow)` |

Both permissions are `protectionLevel="normal"` — install-time, no runtime prompt, no user-facing
dialog, no data access.

### The Android prevention/detection conflict

`ScreenshotEvents` resolves dynamically:

| Condition | Result |
|---|---|
| API < 34 | `Unsupported(OsVersionTooLow)` |
| API ≥ 34, no prevention active | `Supported` |
| API ≥ 34, prevention active | `Unsupported(PrecludedByActiveCapability)` |

This is platform behaviour, not a library design choice — see
[Platform Notes](platform-notes.md#the-android-preventiondetection-exclusion).

### Android coverage gaps

- Recording detection sees **only MediaProjection-based** capture. Not `scrcpy`, ADB
  `screenrecord`, HDMI capture, or OEM recorders that bypass MediaProjection.
- Screenshot detection fires only for hardware-button screenshots; ADB captures are invisible. The
  system also shows the user a toast on every detection.
- `FLAG_SECURE` does **not** block autofill services (working as intended per AOSP).

---

## iOS (15+)

| Capability | Mechanism | Supported from | Level |
|---|---|---|---|
| `ScreenshotPrevention` | Secure-container reparenting | 15 | `Supported` |
| `RecordingPrevention` | Secure-container reparenting | 15 | `Supported` |
| `CaptureDetection` | `UITraitSceneCaptureState` on `UIWindowScene` | 17 | `Supported` |
| `CaptureDetection` (fallback) | `UIScreen.capturedDidChangeNotification` | 15 | `Supported` |
| `ScreenshotEvents` | `userDidTakeScreenshotNotification` | 15 | `Supported` |
| `AppSwitcherProtection` | Overlay on scene resign-active | 15 | `Supported` |

`ScreenshotPrevention` and `RecordingPrevention` share the single secure-container mechanism on iOS.

See [Platform Notes](platform-notes.md#ios-prevention-and-app-review) for iOS platform details.

### iOS coverage gaps

- `CaptureDetection` reports **scene-level capture participation**, not device-level recording.
- Screenshot events are strictly **post-hoc** and cannot prevent the capture that triggered them.
- Neither prevention nor detection can be verified in the Simulator; both are device-only.

---

## What this library does not claim

No software mechanism prevents photographing a screen with a second device, or capture on a rooted
or jailbroken device. On Android, `FLAG_SECURE` is trivially bypassed by LSPosed or Frida hooks.

`CaptureState.Inactive` means **"no evidence of capture,"** never "not being captured." Both
platforms' detection has the documented blind spots listed above. Do not treat `Inactive` as a
security guarantee, and do not gate the display of sensitive content on it alone — use prevention
for that.
