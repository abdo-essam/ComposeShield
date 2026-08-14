# Platform Notes

The reasoning behind ComposeGuard's less obvious behaviour: which platform defects it works around,
which it refuses to paper over, and why several capabilities report `Unsupported` where a fallback
would have been possible.

The short version: **for a security library, a false "you are not being captured" is worse than an
honest "unsupported."** Nearly everything below follows from that.

See [capability-matrix.md](capability-matrix.md) for what is supported where.

---

## iOS prevention and App Review

**This is the library's one unsanctioned mechanism, and the only one behind an opt-in.**

Apple ships no screenshot- or recording-prevention API, and the omission is deliberate — a
Frameworks engineer has stated publicly that blocking screenshots is unsupported, reasoning that a
user can photograph the screen with a second device regardless.

The only working mechanism relies on undocumented behaviour of `UITextField.isSecureTextEntry`. A
secure text field owns a private canvas subview that the render server excludes from screen
capture. ComposeGuard lifts that canvas **out** of the text field into the app's own hierarchy and
reparents the window's content inside it.

Two consequences follow from that direction, and both are easy to get backwards:

- The text field is **never added to the hierarchy but must be retained anyway**, because it owns
  the layer mask that makes the canvas secure. Releasing it silently un-secures the container,
  producing content that looks protected and is not.
- Trait propagation survives, because the canvas ends up in the normal hierarchy rather than buried
  inside a detached view. This is why capture *detection* has no conflict with prevention — the two
  subsystems are structurally independent, which an early draft of the design assumed they were not.

### The risk being accepted

The mechanism calls **no private API**, so it does not trip static private-symbol scanners, and it
ships in production apps today. But Apple Developer Technical Support has stated that using a
secure text field as a wrapping container "is not its intended purpose," invoking **App Review
Guideline 2.5.1** (using non-public APIs / undocumented behaviour). **Rejection is possible.**

The mechanism is also fragile independently of policy. The canvas class name is undocumented and
version-dependent (`_UITextLayoutCanvasView` on iOS 15+), and iOS 17 broke index-based sublayer
lookups once already. ComposeGuard therefore matches by class-name **substring, never by index** —
an index-based lookup would silently adopt a view that is *not* secure rather than fail honestly.
Every failure path fails soft to `Unsupported(MechanismUnavailable)`, at which point the declared
`FailurePosture` decides what happens to the content.

Re-verify on every iOS release.

### Why it is opt-in rather than on by default

The library will not transfer an unevaluated app-store policy risk to a consumer silently. Until
`ComposeGuard.optInToUnsanctionedCapability` is called for a capability, that capability **does
nothing**, and `supportLevel()` reports `RequiresOptIn` so the application can see it.

Consent is **per capability**, and never extends to capabilities added in a later version — a
library upgrade cannot broaden what an application agreed to. A `FailurePosture` is required at the
same call, because a mechanism that can vanish mid-session must have an answer to "what happens to
the content then?" before it is switched on.

The prevention mechanism cannot be verified in the Simulator, which writes the emulated framebuffer
directly and bypasses the render-server path that carries the protection. Device-only.

---

## The Android prevention/detection exclusion

**Activating screenshot prevention silently disables screenshot detection.** AOSP `Activity.java`
states that the screen-capture callback "is not invoked if the activity window has `FLAG_SECURE`
set."

This is platform behaviour, not a library design choice, and it cannot be worked around. ComposeGuard
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

So `captureState` starts at `CaptureState.Unknown` and **is never seeded to `Inactive`**. It stays
`Unknown` until the platform affirmatively says otherwise. ComposeGuard additionally re-polls on
every return to the foreground, because capture that starts while the app is backgrounded produces a
transition nothing was alive to observe.

`Unknown` is never coerced to `Inactive` anywhere in the library. Treat it as "possibly being
captured," not as a safe default.

---

## Spurious-inactive suppression

iOS 26.2 flips scene capture state to inactive when a Live Activity expands from the Dynamic Island,
while recording continues.

A transition **to** inactive is therefore held for 750 ms and published only if nothing contradicts
it. A transition to **active** is published immediately and never delayed.

The asymmetry is the entire point. The suppression window only ever delays the *reassuring*
direction, so erring long costs nothing but a stale warning. It is sized to absorb the Live Activity
flap (which resolves in well under a second) while keeping genuine transitions observable within the
1 s budget SC-004 sets.

---

## Where fallbacks were rejected

Four capabilities report `Unsupported` below an OS floor where a fallback was technically possible.
Each was rejected deliberately:

| Capability | Floor | Rejected fallback | Why |
|---|---|---|---|
| Screenshot events | API 34 | MediaStore `ContentObserver` | Needs `READ_MEDIA_IMAGES`, which FR-026 forbids the library from requesting. Heuristic across OEMs anyway. |
| Recording detection | API 35 | Infer from `DisplayManager` | Misses MediaProjection recorders that create no visible display — a false negative in the one direction that matters. |
| Standalone app-switcher | API 33 | `setDisablePreviewScreenshots` | `@UnsupportedAppUsage` with `maxTargetSdk S`; reaching it requires reflection, which Principle V forbids outright. |
| iOS secure container | — | Index-based sublayer lookup | Would silently adopt a non-secure view when iOS reorders subviews, rather than failing honestly. |

Each fallback would have produced a false "you are not being captured." An honest `Unsupported` lets
the application choose its own response; a silent lie does not.

---

## Window scoping

**Protection is window-scoped, not subtree-scoped.** While `SecureContent` is composed, the *entire
window* is protected — not only its content. This is the most likely misunderstanding of the API,
and it is a platform constraint: both platforms apply capture prevention at window level.

Two consequences:

- A sibling composable outside the boundary is protected too.
- Content in a *different* window — a dialog with its own window, the other half of a split screen —
  is **not** protected.

The composable is named `SecureContent` rather than `SecureScreen` precisely so it does not promise
a subtree guarantee the platform cannot honour.

Because only one physical protection primitive exists per window, requests are reference-counted:
protection is withdrawn only when the last outstanding request on that window is released. A
departing screen that cleared the flag directly would unprotect a still-visible screen underneath,
and nothing would report the exposure.

---

## Threading

Every public member is safe to call from any thread. Platform effects are marshalled to the main
thread internally.

This matters more than it looks. `Window.addFlags` routes to `ViewRootImpl.checkThread()`, which
throws `CalledFromWrongThreadException` off the main thread — but only *sometimes*: with no decor
view attached yet, the flags are merely stored and nothing complains. That makes an off-main call an
intermittent crash that passes testing and fails in the field.

A call marshalled from a background thread reports `Deferred` rather than a synchronous result. The
alternative — blocking the caller until the main thread answers — is how deadlocks are written.
`Deferred` means "requested, not yet confirmed," and is deliberately distinct from `Failed` so that
ordinary startup ordering never fires a failure posture.
