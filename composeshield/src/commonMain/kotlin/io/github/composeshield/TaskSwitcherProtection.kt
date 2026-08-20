package io.github.composeshield

/**
 * How the OS task-switcher snapshot should be treated.
 *
 * Set through [ComposeShield.taskSwitcherProtection]. The switcher is the most frequently encountered
 * real-world leak vector — the system photographs the app on every backgrounding, whether or not
 * anyone deliberately captured anything — and it is officially supported on both platforms, so it
 * needs no unsanctioned-mechanism opt-in.
 *
 * **Thread-safety**: an immutable enum, safe to read from any thread.
 */
public enum class TaskSwitcherProtection {
    /**
     * Protect the switcher whenever any protection request is outstanding. **The default.**
     *
     * An application that protects a screen almost always wants that screen absent from the switcher
     * too, so requiring a second opt-in would mostly produce leaks from forgetfulness.
     */
    Automatic,

    /**
     * Always protect the switcher, whether or not any protection request exists.
     *
     * Lets an application obscure its switcher snapshot without preventing capture at all. On
     * Android this maps to the recents-only primitive, not to capture prevention.
     */
    Always,

    /**
     * Never protect the switcher, even while protection is active.
     *
     * Capture prevention stays fully active; only the switcher snapshot is left alone. On Android
     * the prevention primitive obscures recents as an inseparable side effect, so this cannot reveal
     * a snapshot the platform itself has already hidden.
     */
    Disabled,
}
