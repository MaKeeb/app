package com.makeeb.feature.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OnboardingViewModel(private val setup: KeyboardSetup) : ViewModel() {
    private val mutableSteps = MutableStateFlow(emptyList<SetupStep>())
    val steps: StateFlow<List<SetupStep>> = mutableSteps.asStateFlow()

    /** Re-check on every resume: the user comes back from system settings. */
    fun refresh() {
        mutableSteps.value = setup.steps(setup.status())
    }
}
