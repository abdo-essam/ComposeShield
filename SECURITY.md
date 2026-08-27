# Security Policy

## Supported Versions

| Version | Supported |
|---|---|
| 0.1.x (latest) | ✅ |

Older versions receive no security fixes once a newer minor version is released.

## Reporting a Vulnerability

**Please do not file public GitHub issues for security vulnerabilities.**

You have two options:

### Option 1 — GitHub Private Vulnerability Reporting (preferred)

Use GitHub's built-in private reporting:
[Report a vulnerability](https://github.com/abdo-essam/ComposeShield/security/advisories/new)

This creates a private advisory visible only to you and the maintainer.

### Option 2 — Direct email

Email **abdo-essam@hotmail.com** with:
- A description of the vulnerability
- Steps to reproduce
- Affected platform(s) and OS version(s)
- Any suggested fix or workaround (optional)

## Response Timeline

| Step | Target |
|---|---|
| Acknowledgement | Within 48 hours |
| Initial assessment | Within 5 business days |
| Fix or mitigation | Depends on severity |

## Scope

ComposeShield provides the strongest screen-capture protection each platform officially supports.
The following are **out of scope** by design — they are platform limitations, not library bugs:

- Physical observation of the screen (camera, shoulder-surfing)
- Capture on rooted or jailbroken devices
- ADB `screencap` bypassing `FLAG_SECURE` on rooted Android
- Simulator / emulator — OS-level protection is not enforced there

See [docs/security-limitations.md](docs/security-limitations.md) for the full boundary.
