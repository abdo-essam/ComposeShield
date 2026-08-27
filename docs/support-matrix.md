# Support Matrix

> **Status key**: ✅ Automated · ⚠️ Manual required · ❌ Not automatable · 🚧 Planned

## Screenshot Prevention

| Capability | Android (physical) | iOS (physical) | iOS (Simulator) |
|---|---|---|---|
| Screenshot blocked | ✅ C-001 | ⚠️ C-004 | ❌ N/A¹ |
| Negative control | ✅ C-002 | ⚠️ C-005 | ❌ N/A¹ |
| Disabled → marker returns | ✅ C-003 | ⚠️ — | ❌ N/A¹ |

¹ Simulators do not enforce OS-level screenshot protection; assertion is meaningless.

## App-Switcher Prevention

| Capability | Android (physical) | iOS (physical) |
|---|---|---|
| Thumbnail suppressed | ✅ A-001 | ⚠️ A-002 |

## State & Lifecycle

| Capability | Environment |
|---|---|
| Double-enable idempotency (I-001) | ✅ Android physical |
| Release/cleanup (R-001) | ✅ Android physical |

## Static Analysis

| Check | Runner | Status |
|---|---|---|
| Common logic (Robolectric) | ubuntu-latest | ✅ Automated — every PR |
| iOS Simulator tests | macos-26 | ✅ Automated — every PR |
| Binary compatibility (ABI) | macos-26 | ✅ Automated — every PR |
| Detekt + Spotless | ubuntu-latest | ✅ Automated — every PR |
| KDoc completeness (Dokka strict) | ubuntu-latest | ✅ Automated — release |
| API dump currency | ubuntu-latest | ✅ Automated — release |

## Quality Compliance Procedure

Zero false negatives/positives is validated by:
1. The marker-absent detection algorithm in `ScreenshotValidationTest` (deterministic)
2. Manual validation: run the on-demand pipeline 5 times before each release and confirm all
   C-001 results are `passed` and all C-002 results are `passed`. Document results in the
   release checklist.

## iOS Physical Promotion

To promote iOS physical tests from `⚠️ manual_required` to `✅ automated`:
1. Set `ios-physical-iphone.enabled: true` in [`config/device-matrix.yml`](../config/device-matrix.yml)
2. Set `provider` to your CI device provider and update credentials
3. Commit — no workflow YAML change required
