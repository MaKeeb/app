package com.makeeb.feature.onboarding

/** `null` means the platform cannot tell (iOS exposes no public "is my keyboard enabled" API). */
data class SetupStatus(val enabled: Boolean?, val selected: Boolean?)

data class SetupStep(
    val title: String,
    val body: String,
    val actionLabel: String?,
    val action: (() -> Unit)?,
    val done: Boolean?,
)

/** Platform-specific steps for turning the keyboard on. */
interface KeyboardSetup {
    fun status(): SetupStatus

    fun steps(status: SetupStatus): List<SetupStep>
}
