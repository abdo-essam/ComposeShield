# Security Limitations

This document describes known limitations and the security model boundary for ComposeShield.

## What ComposeShield Guarantees

ComposeShield **requests** OS-level screenshot protection via the platform API
(`FLAG_SECURE` on Android, `UIApplicationProtectedDataUnavailable` equivalent on iOS).
When the OS honours the request, protected content is **not detectable** in captured images —
the validation pipeline confirms this with real physical-device instrumentation tests (C-001, A-001).

## What ComposeShield Does NOT Guarantee

| Limitation | Detail |
|---|---|
| **OS discretion** | The OS may ignore the protection request on rooted/jailbroken devices, custom firmware, or future OS versions. ComposeShield cannot override OS policy. |
| **Side-channel leaks** | Physical observation of the screen (camera, shoulder-surfing) is outside scope. |
| **ADB mirroring** | `adb shell screencap` on a rooted device bypasses `FLAG_SECURE`. This is an OS limitation, not a library bug. |
| **Simulator/emulator** | Simulators do not enforce OS-level screenshot protection. Tests on simulators only verify library API calls — not OS enforcement. |
| **iOS physical (v1)** | iOS physical device tests are `manual_required` in v1. Automated iOS physical validation requires a CI-connected iPhone (see `config/device-matrix.yml`). |

## Validation Evidence

Every release includes a `validation-report.json` attached to the GitHub Release. It lists:
- Each test ID, its status (`passed` / `manual_required` / `blocked`), and an `evidence_url`
  pointing to the protected screenshot (marker absent) or a manual-required attestation.

## SC-004 Compliance

The requirement for zero false negatives/positives is validated by:
1. The deterministic marker-detection algorithm (region sampling, not pixel equality — FR-010)
2. Manual pre-release validation: 5 consecutive on-demand runs, all C-001/A-001 must be `passed`
   Document results in the release notes before tagging.

## Reporting Security Issues

Please do **not** file public issues for security vulnerabilities. Email **abdo-essam@hotmail.com**
or use [GitHub's private vulnerability reporting](https://github.com/abdo-essam/ComposeShield/security/advisories/new).
See the [Contact section in README](../README.md#contact) for details.
