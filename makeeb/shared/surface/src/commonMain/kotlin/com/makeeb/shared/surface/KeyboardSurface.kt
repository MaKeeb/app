package com.makeeb.shared.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.makeeb.shared.keyboard.KeyboardMetrics
import com.makeeb.shared.keyboard.KeyboardRenderer
import com.makeeb.shared.keyboard.KeyboardSession
import com.makeeb.shared.keyboard.ScreenSize
import com.makeeb.shared.keyboard.StripAction
import com.makeeb.core.common.currentMinuteOfDay
import com.makeeb.core.model.ImeAction
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardPanel
import com.makeeb.feature.clipboard.ClipboardPanel
import com.makeeb.feature.emoji.EmojiPanel
import com.makeeb.feature.emoji.EmojiSearchStrip
import com.makeeb.feature.keyboard.KeyboardKeys
import com.makeeb.feature.settings.QuickSettingsPanel
import com.makeeb.feature.suggestions.StripButton
import com.makeeb.feature.suggestions.SuggestionStrip
import com.makeeb.ui.theme.KeyboardIcons
import com.makeeb.ui.theme.KeyboardTheme
import com.makeeb.ui.theme.MaKeebKeyboardTheme

/**
 * The whole keyboard: suggestion strip, then keys or a panel. Fixed height across modes.
 * [bottomInset] is the system navigation area under the keyboard (Android's back and switcher
 * buttons), which the host measures: inside an IME window Compose's own insets report zero; the
 * user's own gap goes on top of it. [screenSize] picks the portrait or landscape size and keeps
 * rows short on short screens (landscape phones); null means portrait at full size.
 */
@Composable
fun KeyboardSurface(
    session: KeyboardSession,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    screenSize: DpSize? = null,
    /** Side insets the keys must avoid (a landscape camera cutout, a side navigation bar); the background still fills them. */
    startInset: Dp = 0.dp,
    endInset: Dp = 0.dp,
) {
    val state by session.engine.state.collectAsState()
    val preferences by session.preferences.collectAsState()
    val geometry by session.geometry.collectAsState()
    val dark = preferences.useDarkTheme(isSystemInDarkTheme(), currentMinuteOfDay())
    val screen = screenSize?.let { ScreenSize(it.width.value, it.height.value) }

    MaKeebKeyboardTheme(darkTheme = dark) {
        Column(
            modifier
                .fillMaxWidth()
                .background(KeyboardTheme.colors.background)
                .padding(
                    start = startInset,
                    end = endInset,
                    bottom = bottomInset + (KeyboardMetrics.BOTTOM_PADDING + KeyboardMetrics.bottomOffset(preferences, screen)).dp,
                ),
        ) {
            val searching = state.emojiSearch
            if (searching != null) {
                val recents by session.emojiRecentsState.collectAsState()
                val results = remember(searching, recents) { if (searching.isBlank()) recents else session.emojiCatalog.search(searching) }
                EmojiSearchStrip(
                    query = searching,
                    results = results,
                    onEmoji = session::onEmoji,
                    onClose = session::endEmojiSearch,
                    modifier = Modifier.fillMaxWidth().height(KeyboardMetrics.STRIP_HEIGHT.dp),
                )
            } else SuggestionStrip(
                suggestions = if (state.panel == KeyboardPanel.Keys) state.suggestions else emptyList(),
                onSuggestion = session::onSuggestion,
                modifier = Modifier.fillMaxWidth().height(KeyboardMetrics.STRIP_HEIGHT.dp),
            ) {
                // Hiding and switching keyboards live in the system navigation bar on Android 10+.
                val actions = KeyboardRenderer.stripActions(state)
                val active = { action: StripAction ->
                    when (action) {
                        StripAction.Emoji -> state.panel == KeyboardPanel.Emoji
                        StripAction.Clipboard -> state.panel == KeyboardPanel.Clipboard
                        StripAction.Incognito -> state.incognito
                        StripAction.Settings -> state.panel == KeyboardPanel.Settings
                    }
                }
                actions.filter { it != StripAction.Settings }.forEach { action ->
                    StripButton(action.icon(), action.description(), onClick = { session.perform(action) }, active = active(action))
                }
                Spacer(Modifier.weight(1f))
                if (StripAction.Settings in actions) {
                    val settings = StripAction.Settings
                    StripButton(settings.icon(), settings.description(), onClick = { session.perform(settings) }, active = active(settings))
                }
            }

            val area = Modifier.fillMaxWidth().height(KeyboardMetrics.keysAreaHeight(preferences, screen).dp)
            when (state.panel) {
                KeyboardPanel.Keys -> KeyboardKeys(
                    geometry = geometry,
                    touch = session.touch,
                    shift = state.shift,
                    // Enter closes an emoji search rather than reaching the field.
                    imeAction = if (searching != null) ImeAction.Done else state.editor.imeAction,
                    onSizeChanged = { size -> session.setKeysAreaSize(size.width.toFloat(), size.height.toFloat()) },
                    modifier = area,
                )
                KeyboardPanel.Emoji -> {
                    val recents by session.emojiRecentsState.collectAsState()
                    EmojiPanel(
                        catalog = session.emojiCatalog,
                        recents = recents,
                        onEmoji = session::onEmoji,
                        onBackspace = { session.onKey(KeyAction.Backspace) },
                        onClose = { session.showPanel(KeyboardPanel.Keys) },
                        onSearch = session::startEmojiSearch,
                        modifier = area,
                    )
                }
                KeyboardPanel.Clipboard -> {
                    val entries by session.clipboardEntries.collectAsState()
                    val snippets by session.snippets.collectAsState()
                    ClipboardPanel(
                        entries = entries,
                        available = session.clipboardAvailable,
                        onPaste = session::onPaste,
                        onTogglePin = { session.setClipPinned(it, !it.pinned) },
                        onDelete = session::removeClip,
                        onClearUnpinned = session::clearClips,
                        snippets = snippets,
                        onSnippet = session::onSnippet,
                        onClose = { session.showPanel(KeyboardPanel.Keys) },
                        modifier = area,
                    )
                }
                KeyboardPanel.Settings -> QuickSettingsPanel(
                    preferences = preferences,
                    editable = session.quickSettingsEditable,
                    onToggle = session::toggle,
                    onOpenApp = session::openSettings.takeIf { session.canOpenSettings },
                    onClose = { session.showPanel(KeyboardPanel.Keys) },
                    modifier = area,
                )
            }
        }
    }
}

private fun StripAction.icon() = when (this) {
    StripAction.Emoji -> KeyboardIcons.Emoji
    StripAction.Clipboard -> KeyboardIcons.Clipboard
    StripAction.Incognito -> KeyboardIcons.Incognito
    StripAction.Settings -> KeyboardIcons.Settings
}

private fun StripAction.description() = when (this) {
    StripAction.Emoji -> "Emoji"
    StripAction.Clipboard -> "Clipboard"
    StripAction.Incognito -> "Incognito"
    StripAction.Settings -> "Quick settings"
}
