package io.github.composeguard

/**
 * Marks a declaration as an implementation detail of ComposeGuard.
 *
 * Anything annotated with this is **outside the binary-compatibility promise** and may change or
 * disappear in any release, including a patch. It is filtered out
 * of the committed ABI dump, so changes to it never register as API breakage.
 *
 * Some declarations must be `public` for the Kotlin compiler to reach them across source sets —
 * `expect`/`actual` pairs in particular — while remaining private to the library in every sense
 * that matters to a consumer. This annotation marks that gap rather than leaving those
 * declarations indistinguishable from the supported surface.
 *
 * Opting in is an error rather than a warning: reaching for one of these is never accidental, and
 * a consumer who does so should have to write it down.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message =
        "This is a ComposeGuard implementation detail. It is excluded from the ABI dump " +
            "and carries no compatibility guarantee — it can change or be removed in any release.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
public annotation class InternalComposeGuardApi
