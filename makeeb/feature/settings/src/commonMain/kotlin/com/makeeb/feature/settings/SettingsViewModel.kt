package com.makeeb.feature.settings

import androidx.lifecycle.ViewModel
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.core.settings.PreferencesRepository
import com.makeeb.core.settings.SnippetsRepository
import com.makeeb.engine.layout.LanguageInfo
import com.makeeb.engine.layout.LayoutInfo
import com.makeeb.engine.layout.LayoutProvider
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val repository: PreferencesRepository,
    layouts: LayoutProvider,
    private val snippetsRepository: SnippetsRepository,
) : ViewModel() {
    val snippets: StateFlow<List<String>> = snippetsRepository.snippets

    fun addSnippet(text: String) = snippetsRepository.add(text)

    fun removeSnippet(index: Int) = snippetsRepository.remove(index)

    val preferences: StateFlow<KeyboardPreferences> = repository.preferences
    val letterLayouts: List<LayoutInfo> = layouts.letterLayouts
    val languages: List<LanguageInfo> = layouts.languages

    fun update(transform: (KeyboardPreferences) -> KeyboardPreferences) = repository.update(transform)

    /** The keyboard's quick settings write too; on iOS from another process. */
    fun reload() {
        repository.reload()
        snippetsRepository.reload()
    }
}
