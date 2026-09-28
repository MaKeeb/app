package com.makeeb.testing

import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.core.settings.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** In-memory preferences; [reload] keeps what [update] wrote. */
class FakePreferencesRepository(initial: KeyboardPreferences = KeyboardPreferences()) : PreferencesRepository {
    private val state = MutableStateFlow(initial)
    override val preferences: StateFlow<KeyboardPreferences> = state

    override fun update(transform: (KeyboardPreferences) -> KeyboardPreferences) = state.update(transform)

    override fun reload() = Unit
}
