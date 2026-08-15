# Platform Notes

The reasoning behind ComposeShield's less obvious behaviour: which platform defects it works around,
which it refuses to paper over, and why several capabilities report `Unsupported` where a fallback
would have been possible.

The short version: **for a security library, a false "you are not being captured" is worse than an
honest "unsupported."** Nearly everything below follows from that.

See [capability-matrix.md](capability-matrix.md) for what is supported where.

---

## iOS prevention and App Review

Apple ships no screenshot- or recording-prevention API, and the omission is deliberate — a
Frameworks engineer has stated publicly that blocking screenshots is unsupported, reasoning that a
user can photograph the screen with a second device regardless.

The working mechanism relies on behaviour of `UITextField.isSecureTextEntry`. A secure text field
owns a canvas subview that the render server excludes from screen capture. ComposeShield lifts that
canvas into the app's hierarchy and reparents the window's content inside it.

Two consequences follow from that direction:

- The text field is **never added to the hierarchy but must be retained**, because it owns
  the layer mask that makes the canvas secure. Releasing it silently un-secures the container,
  producing content that looks protected and is not.
- Trait propagation survives, because the canvas ends up in the normal hierarchy rather than buried
  inside a detached view. This is why capture *detection* has no conflict with prevention.

### The underlying platform considerations

The mechanism calls **no private API**, so it does not trip static private-symbol scanners, and it
ships in production apps today. However, Apple Developer Technical Support has noted that using a
secure text field as a wrapping container is not its designed purpose (under App Review Guideline 2.5.1).

The mechanism matches canvas views by class-name **substring, never by index** — an index-based lookup
would risk adopting a view that is *not* secure. Every failure path fails soft to
`Unsupported(MechanismUnavailable)` and emits a `protectionFailures` event, allowing the application
to decide its own security and UX response.

The prevention mechanism cannot be verified in the Simulator, which writes the emulated framebuffer
directly and bypasses the render-server path that carries the protection. Device-only.

---

## The Android prevention/detection exclusion

**Activating screenshot prevention silently disables screenshot detection.** AOSP `Activity.java`
states that the screen-capture callback "is not invoked if the activity window has `FLAG_SECURE`
set."

This is platform behaviour, not a library design choice, and it cannot be worked around. ComposeShield
reports it rather than hiding it: while prevention is active, `ScreenshotEvents` resolves to
`Unsupported(PrecludedByActiveCapability)`, and recovers on its own when prevention is released.

**Prevention wins the conflict.** Blocking a capture is a stronger guarantee than logging one, and a
security library that silently traded blocking for logging would be a dangerous default. The
superseded capability reports unsupported for the duration rather than silently delivering nothing.

`PrecludedByActiveCapability` is distinguished from `OsVersionTooLow` precisely because the two
demand different responses — the former comes back on its own, the latter never will on this device.

---

## Cold-launch under-reporting

**Both platforms can report "not being captured" while capture is already in progress**, for
unrelated reasons:

- **iOS** has a platform bug (FB14607048): if recording is already running at launch, the first read
  returns inactive. The *change* path is correct; only the initial read is wrong.
- **Android** reports recording state through a callback that returns the current state on
  registration. Discarding that return value — easy to do — means recording started before launch is
  never reported at all.

In both cases, if capture began *before* the app launched, no transition ever occurs and a naive
implementation reports "not being captured" for the entire session.

ComposeShield absorbs both: `CaptureState` starts at `Unknown` and is **never seeded to `Inactive`**.

---

## Window scoping

**Protection applies to the entire window, not a subtree.**

This is a platform constraint on both Android and iOS:
- On Android, `FLAG_SECURE` is a window flag (`WindowManager.LayoutParams.FLAG_SECURE`).
- On iOS, secure container reparenting protects the entire root view hierarchy in the window.

Wrapping a small composable inside `SecureContent` protects the whole screen while that composable is
composed. Subtree-only protection does not exist on mobile platforms without rendering into an offscreen
texture (which breaks accessibility, touch input, and hardware acceleration).
