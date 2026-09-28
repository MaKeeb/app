package com.makeeb.shared.keyboard

import com.makeeb.core.model.EditorAttributes
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardPanel
import com.makeeb.core.model.Suggestion
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.core.settings.PreferencesRepository
import com.makeeb.engine.clipboard.ClipboardEntry
import com.makeeb.engine.clipboard.ClipboardHistory
import com.makeeb.engine.emoji.Emoji
import com.makeeb.engine.emoji.EmojiCatalog
import com.makeeb.engine.emoji.EmojiRecents
import com.makeeb.engine.input.InputEngine
import com.makeeb.engine.layout.Key
import com.makeeb.engine.layout.KeyStyle
import com.makeeb.engine.layout.KeyboardLayout
import com.makeeb.engine.layout.LayoutGeometry
import com.makeeb.engine.touch.TouchConfig
import com.makeeb.engine.touch.TouchController
import com.makeeb.engine.touch.TouchListener
import com.makeeb.platform.clipboard.Clip
import com.makeeb.platform.clipboard.SystemClipboard
import com.makeeb.platform.feedback.HapticFeedback
import com.makeeb.platform.feedback.KeyFeedbackType
import com.makeeb.platform.feedback.SoundFeedback
import com.makeeb.platform.host.KeyboardHost
import com.makeeb.platform.host.TextHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The OS services a keyboard surface needs, supplied by each platform shell. */
class KeyboardPorts(
    val clipboard: SystemClipboard,
    val haptics: HapticFeedback,
    val sound: SoundFeedback,
    /** False where the OS withholds the clipboard (iOS without Full Access). */
    val clipboardAvailable: () -> Boolean = { true },
)

/**
 * Glue between one keyboard surface and the shared engine: routes touches to the engine, plays
 * feedback, keeps the key geometry in sync with the layout and size, and feeds clipboard
 * history. Renderers (Compose on Android, UIKit on iOS) only draw its state.
 */
class KeyboardSession(
    val engine: InputEngine,
    preferences: PreferencesRepository,
    val emojiCatalog: EmojiCatalog,
    private val emojiRecents: EmojiRecents,
    private val clipboardHistory: ClipboardHistory,
    private val ports: KeyboardPorts,
    private val scope: CoroutineScope,
) {
    val preferences: StateFlow<KeyboardPreferences> = preferences.preferences
    val emojiRecentsState: StateFlow<List<Emoji>> = emojiRecents.recents
    val clipboardEntries: StateFlow<List<ClipboardEntry>> = clipboardHistory.entries
    val clipboardAvailable: Boolean get() = ports.clipboardAvailable()

    val touch = TouchController(
        scope = scope,
        listener = object : TouchListener {
            override fun onKeyDown(key: Key) = playFeedback(key)
            override fun onAction(action: KeyAction) = engine.onKey(action)
        },
    )

    private val mutableGeometry = MutableStateFlow<LayoutGeometry?>(null)
    val geometry: StateFlow<LayoutGeometry?> = mutableGeometry.asStateFlow()

    /**
     * Pixels per dp (Android) or 1 (iOS, which lays out in points). Scales the shared metrics and
     * touch thresholds into the renderer's units.
     */
    var density: Float = 1f
        set(value) {
            field = value
            touch.config = TouchConfig.forDensity(value, overflowAbove = KeyboardMetrics.STRIP_HEIGHT * value)
            rebuildGeometry()
        }

    private var keysAreaSize: Pair<Float, Float>? = null
    private var geometryLayout: KeyboardLayout? = null
    private var keyboardHost: KeyboardHost? = null
    private var clipboardJob: Job? = null

    init {
        scope.launch {
            engine.state.collect { state ->
                touch.shift = state.shift
                touch.previewEnabled = previewEnabled()
                if (state.layout != geometryLayout) rebuildGeometry()
            }
        }
        scope.launch {
            this@KeyboardSession.preferences.collect { prefs ->
                touch.previewEnabled = previewEnabled()
                touch.deleteWordsWhenHeld = prefs.holdDeleteWords
                engine.refreshLayout()
            }
        }
    }

    fun start(textHost: TextHost, keyboardHost: KeyboardHost, attributes: EditorAttributes) {
        this.keyboardHost = keyboardHost
        engine.startInput(textHost, keyboardHost, attributes)
        clipboardHistory.expire()
        clipboardJob?.cancel()
        if (ports.clipboardAvailable()) {
            ports.clipboard.read()?.let(::offerClip)
            clipboardJob = scope.launch { ports.clipboard.changes.collect(::offerClip) }
        }
    }

    fun stop() {
        clipboardJob?.cancel()
        clipboardJob = null
        touch.cancelAll()
        engine.finishInput()
        keyboardHost = null
    }

    /** The renderer's key area size, in its own units (px on Android, pt on iOS). */
    fun setKeysAreaSize(width: Float, height: Float) {
        keysAreaSize = width to height
        rebuildGeometry()
    }

    fun onSuggestion(suggestion: Suggestion) = engine.onSuggestionSelected(suggestion)

    fun onKey(action: KeyAction) = engine.onKey(action)

    fun showPanel(panel: KeyboardPanel) = engine.onKey(KeyAction.ShowPanel(panel))

    /** A strip button: a panel button toggles its panel, so a second tap returns to the keys. */
    fun perform(action: StripAction) {
        val panel = when (action) {
            StripAction.Emoji -> KeyboardPanel.Emoji
            StripAction.Clipboard -> KeyboardPanel.Clipboard
            StripAction.Settings -> return openSettings()
        }
        showPanel(if (engine.state.value.panel == panel) KeyboardPanel.Keys else panel)
    }

    fun hideKeyboard() {
        keyboardHost?.hideKeyboard()
    }

    val canOpenSettings: Boolean get() = keyboardHost?.canOpenSettings ?: false

    fun openSettings() {
        keyboardHost?.openSettings()
    }

    fun onEmoji(emoji: Emoji) {
        engine.commitRawText(emoji.value)
        emojiRecents.record(emoji)
    }

    fun onPaste(entry: ClipboardEntry) = engine.commitRawText(entry.text)

    fun setClipPinned(entry: ClipboardEntry, pinned: Boolean) = clipboardHistory.setPinned(entry.id, pinned)

    fun removeClip(entry: ClipboardEntry) = clipboardHistory.remove(entry.id)

    fun clearClips() = clipboardHistory.clearUnpinned()

    private fun rebuildGeometry() {
        val (width, height) = keysAreaSize ?: return
        val layout = engine.state.value.layout
        if (width <= 0f || height <= 0f || layout.rows.isEmpty()) return
        geometryLayout = layout
        val geometry = LayoutGeometry(layout, width, height / layout.totalHeightWeight, KeyboardMetrics.SIDE_INSET * density)
        mutableGeometry.value = geometry
        touch.geometry = geometry
    }

    /** Never in password fields: an enlarged character is readable over the user's shoulder. */
    private fun previewEnabled(): Boolean = preferences.value.keyPopupPreview && !engine.state.value.editor.isPassword

    private fun offerClip(clip: Clip) {
        // Never keep what password managers mark sensitive, nor anything copied while typing
        // into a field that asked for no learning.
        if (!clip.isSensitive && !engine.state.value.editor.incognito) clipboardHistory.add(clip.text)
    }

    private fun playFeedback(key: Key) {
        val prefs = preferences.value
        val type = when (key.action) {
            KeyAction.Backspace -> KeyFeedbackType.Delete
            KeyAction.Space -> KeyFeedbackType.Space
            KeyAction.Enter -> KeyFeedbackType.Return
            else -> if (key.style == KeyStyle.Modifier) KeyFeedbackType.Modifier else KeyFeedbackType.Standard
        }
        if (prefs.keyPressHaptics) ports.haptics.keyPress(type)
        if (prefs.keyPressSound) ports.sound.keyPress(type)
    }
}
