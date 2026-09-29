package com.makeeb.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makeeb.core.settings.PreferencesRepository
import com.makeeb.engine.packs.PackInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Setup's state: the platform's steps for turning the keyboard on, and the languages step, which
 * is done once every selected language has its dictionary (English is built in) or the user
 * skips it. Without [preferences] and [installer] there is no languages step.
 */
class OnboardingViewModel(
    private val setup: KeyboardSetup,
    preferences: PreferencesRepository? = null,
    installer: PackInstaller? = null,
) : ViewModel() {
    private val mutableSteps = MutableStateFlow(emptyList<SetupStep>())
    val steps: StateFlow<List<SetupStep>> = mutableSteps.asStateFlow()

    /** Whether setup has a languages step. */
    val hasLanguagesStep: Boolean = preferences != null && installer != null

    /** Every selected language has a dictionary: built in or installed. */
    val languagesReady: StateFlow<Boolean> =
        if (preferences == null || installer == null) MutableStateFlow(false).asStateFlow()
        else combine(preferences.preferences, installer.state) { prefs, packs -> packs.hasDictionaries(prefs.languageTags) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val mutableLanguagesSkipped = MutableStateFlow(false)

    /** The user skipped the languages step; Settings has the same choices any time. */
    val languagesSkipped: StateFlow<Boolean> = mutableLanguagesSkipped.asStateFlow()

    fun skipLanguages() {
        mutableLanguagesSkipped.value = true
    }

    fun reopenLanguages() {
        mutableLanguagesSkipped.value = false
    }

    /** Re-check on every resume: the user comes back from system settings. */
    fun refresh() {
        mutableSteps.value = setup.steps(setup.status())
    }
}
