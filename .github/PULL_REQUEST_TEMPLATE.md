## Summary

<!-- One sentence: what does this PR do and why? -->

## Type of change

- [ ] Bug fix
- [ ] New feature / capability
- [ ] Documentation
- [ ] CI / tooling
- [ ] Refactor (no behaviour change)

## Checklist

- [ ] `./gradlew :composeshield:testAndroidHostTest` passes
- [ ] `./gradlew detekt spotlessCheck` passes
- [ ] `./gradlew :composeshield:iosSimulatorArm64Test` passes (run on macOS)
- [ ] `./gradlew :composeshield:checkKotlinAbi` passes — or ABI dump updated with `./gradlew :composeshield:updateKotlinAbi`
- [ ] All new public API has KDoc
- [ ] `docs/capability-matrix.md` updated if capability support changed
- [ ] `CHANGELOG.md` entry added under `[Unreleased]`

## Testing

<!-- How was this tested? Device model, OS version, emulator vs physical. -->

## Related issues

<!-- Closes # -->
