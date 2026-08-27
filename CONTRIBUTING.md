# Contributing to ComposeShield

Thank you for taking the time to contribute. ComposeShield is a security library, so
correctness and stability matter more than speed — please read this guide before opening a PR.

---

## Table of contents

- [Development environment](#development-environment)
- [Running tests](#running-tests)
- [Static analysis](#static-analysis)
- [Public API changes](#public-api-changes)
- [Pull request checklist](#pull-request-checklist)
- [Reporting security issues](#reporting-security-issues)

---

## Development environment

| Tool | Version |
|---|---|
| JDK | 21 (Temurin recommended) |
| Android Studio | Latest stable |
| Xcode | Required for iOS targets (macOS only) |
| Kotlin | 2.4.x (see `libs.versions.toml`) |
| Compose Multiplatform | 1.11.x |

Clone the repo and sync Gradle — no extra setup is required on Android. The
`ComposeShieldInitializer` content provider wires itself automatically.

```bash
git clone https://github.com/abdo-essam/ComposeShield.git
cd ComposeShield
./gradlew :composeshield:testAndroidHostTest   # verify the setup works
```

---

## Running tests

### Common logic tests (JVM — fast, no device needed)

```bash
./gradlew :composeshield:testAndroidHostTest
```

### iOS simulator tests (macOS only)

```bash
./gradlew :composeshield:iosSimulatorArm64Test
```

### Android instrumentation tests (physical device / Firebase Test Lab)

Instrumentation tests require a physical device or FTL credentials. Locally:

```bash
./gradlew :composeshield:connectedAndroidTest
```

On CI these run via `on-demand.yml` or `release.yml` using FTL.

### All checks at once

```bash
./gradlew :composeshield:testAndroidHostTest detekt spotlessCheck :composeshield:checkKotlinAbi
```

---

## Static analysis

```bash
# Lint + style
./gradlew detekt spotlessCheck

# Auto-fix formatting
./gradlew spotlessApply
```

`detekt` config lives in `config/detekt/detekt.yml`. `spotless` enforces ktlint.

---

## Public API changes

ComposeShield uses [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator)
to track the public ABI. Any change to a `public` declaration updates the dump.

**After changing a public API**, regenerate the dump:

```bash
./gradlew :composeshield:updateKotlinAbi
```

Then commit the updated `composeshield/api/composeshield.klib.api` alongside your code change.

The CI `checkKotlinAbi` step will fail on any unrecorded ABI change.

> **Adding a `public` member** is additive and fine.
> **Removing or changing a `public` member** is breaking — bump the minor or major version
> and document it in `CHANGELOG.md`.

### KDoc requirement

Every `public` declaration must have a KDoc comment. Dokka runs in strict mode
(`failOnWarning = true`) — a missing doc fails the `dokkaHtml` task in CI.

---

## Pull request checklist

Before marking a PR ready for review, confirm:

- [ ] `./gradlew :composeshield:testAndroidHostTest` passes
- [ ] `./gradlew detekt spotlessCheck` passes
- [ ] `./gradlew :composeshield:iosSimulatorArm64Test` passes (macOS runners only)
- [ ] `./gradlew :composeshield:checkKotlinAbi` passes — or ABI dump updated with `updateKotlinAbi`
- [ ] All new `public` declarations have KDoc
- [ ] `docs/capability-matrix.md` updated if capability behaviour changed
- [ ] `CHANGELOG.md` has an entry under `[Unreleased]`

---

## Reporting security issues

Please **do not** file public issues for security vulnerabilities.
See [SECURITY.md](SECURITY.md) for the responsible disclosure process.
