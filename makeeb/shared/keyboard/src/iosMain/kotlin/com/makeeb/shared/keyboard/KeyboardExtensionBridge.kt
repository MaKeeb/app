package com.makeeb.shared.keyboard

import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardPanel
import com.makeeb.core.model.stripSlots
import com.makeeb.core.settings.KeyboardSignals
import com.makeeb.core.settings.PreferencesRepository
import com.makeeb.core.settings.QuickSetting
import com.makeeb.engine.emoji.Emoji
import com.makeeb.platform.clipboard.PasteboardSystemClipboard
import com.makeeb.platform.feedback.HapticFeedback
import com.makeeb.platform.feedback.ImpactHapticFeedback
import com.makeeb.platform.feedback.InputClickSoundFeedback
import com.makeeb.platform.host.InputViewControllerKeyboardHost
import com.makeeb.platform.host.TextDocumentProxyTextHost
import com.makeeb.platform.host.toEditorAttributes
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import platform.Foundation.NSLog
import platform.UIKit.UIInputViewController
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlin.time.TimeSource

/**
 * What the Swift `KeyboardViewController` talks to. Swift forwards view lifecycle, text-change
 * callbacks and touches (in key-area points) and draws each [KeyboardRender] it receives.
 */
@OptIn(ExperimentalNativeApi::class)
class KeyboardExtensionBridge(private val controller: UIInputViewController) : KoinComponent {
    private val created = TimeSource.Monotonic.markNow()
    private val scope = MainScope()
    private val preferences: PreferencesRepository
    private val session: KeyboardSession
    private val textHost = TextDocumentProxyTextHost { controller.textDocumentProxy }
    private val keyboardHost = InputViewControllerKeyboardHost(controller)

    init {
        IosKeyboardKoin.ensureStarted()
        preferences = get()
        val impact by lazy { ImpactHapticFeedback() }
        val ports = KeyboardPorts(
            clipboard = PasteboardSystemClipboard(hasAccess = { controller.hasFullAccess }),
            // Haptics need Full Access in a keyboard extension.
            haptics = HapticFeedback { type, intensity -> if (controller.hasFullAccess) impact.keyPress(type, intensity) },
            sound = InputClickSoundFeedback(),
            clipboardAvailable = { controller.hasFullAccess },
            // Without Full Access the App Group is read-only to the extension.
            settingsWritable = { controller.hasFullAccess },
        )
        session = get { parametersOf(ports, scope) }
        session.density = 1f // UIKit lays out in points, the unit KeyboardMetrics is written in
        // iPhone screens are flat to the edge: keys run as close to it as the system keyboard's.
        session.sideInset = 0f
        if (Platform.isDebugBinary) logDictionaryLoad()
    }

    /** Debug builds log when the dictionary pack is ready (Console, "MaKeebDictionary"); durations and counts only. */
    private fun logDictionaryLoad() {
        scope.launch {
            val status = get<BundledDictionaryLoader>().status.first {
                it !is BundledDictionaryLoader.Status.Idle && it !is BundledDictionaryLoader.Status.Loading
            }
            val line = "MaKeebDictionary: $status, ready ${created.elapsedNow().inWholeMilliseconds} ms after the bridge was created"
            NSLog(line.replace("%", "%%"))
        }
    }

    /**
     * The extension's whole height in points on a screen this size, for the current preferences.
     * Its shape picks the portrait or landscape size. It includes [bottomOffset]: an extension
     * can't draw outside its own view, so the gap is part of it.
     */
    fun preferredHeight(screenWidth: Double, screenHeight: Double): Double =
        KeyboardMetrics.totalHeight(preferences.preferences.value, screen(screenWidth, screenHeight)).toDouble()

    /** The user's gap under the keys in points: keys and panels end this far above the view's bottom. */
    fun bottomOffset(screenWidth: Double, screenHeight: Double): Double =
        KeyboardMetrics.bottomOffset(preferences.preferences.value, screen(screenWidth, screenHeight)).toDouble()

    private fun screen(width: Double, height: Double) = ScreenSize(width.toFloat(), height.toFloat())

    val stripHeight: Double = KeyboardMetrics.STRIP_HEIGHT.toDouble()

    fun viewWillAppear() {
        // The companion app writes preferences and snippets from another process.
        preferences.reload()
        session.reloadSnippets()
        KeyboardSignals.recordShown(controller.hasFullAccess)
        session.start(textHost, keyboardHost, controller.textDocumentProxy.toEditorAttributes())
    }

    fun viewWillDisappear() = session.stop()

    /** Called for text changes and field switches alike; a new field means a new input session. */
    fun textDidChange() {
        val attributes = controller.textDocumentProxy.toEditorAttributes()
        if (attributes != session.engine.state.value.editor) {
            session.start(textHost, keyboardHost, attributes)
        } else {
            session.engine.onExternalChange()
        }
    }

    fun selectionDidChange() = session.engine.onExternalChange()

    fun setKeysAreaSize(width: Double, height: Double) = session.setKeysAreaSize(width.toFloat(), height.toFloat())

    fun touchDown(id: Long, x: Double, y: Double) = session.touch.down(id, x.toFloat(), y.toFloat())

    fun touchMove(id: Long, x: Double, y: Double) = session.touch.move(id, x.toFloat(), y.toFloat())

    fun touchUp(id: Long, x: Double, y: Double) = session.touch.up(id, x.toFloat(), y.toFloat())

    fun touchCancel(id: Long) = session.touch.cancel(id)

    /** VoiceOver's custom action for a long-press alternate of the [keyIndex]th rendered key. */
    fun performAlternate(keyIndex: Int, alternate: Int) {
        val placed = session.touch.geometry?.keys?.getOrNull(keyIndex) ?: return
        session.touch.performAlternate(placed, alternate)
    }

    /** [stripIndex] is the position in [KeyboardRender.suggestions]. */
    fun selectSuggestion(stripIndex: Int) {
        val suggestion = session.engine.state.value.suggestions.stripSlots().getOrNull(stripIndex) ?: return
        session.onSuggestion(suggestion)
    }

    /** A finger rested on the [stripIndex]th cell. True when it holds a learned word and the strip now offers to forget it. */
    fun longPressSuggestion(stripIndex: Int): Boolean {
        val suggestion = session.engine.state.value.suggestions.stripSlots().getOrNull(stripIndex) ?: return false
        return session.onSuggestionLongPress(suggestion)
    }

    fun forgetOfferedWord() = session.forgetOfferedWord()

    fun dismissForgetOffer() = session.dismissForgetOffer()

    val suggestionLongPressMillis: Long = KeyboardMetrics.SUGGESTION_LONG_PRESS_MILLIS

    /**
     * Every change the renderer or an open panel shows. Recents and clipboard changes re-send the
     * same render so an open panel reloads its data.
     */
    fun observe(onRender: (KeyboardRender) -> Unit): RenderSubscription {
        val job = scope.launch {
            combine(session.engine.state, session.touch.state, session.geometry, session.preferences) { state, touch, geometry, prefs ->
                KeyboardRenderer.render(state, touch, geometry, prefs, emojiResults(state.emojiSearch))
            }.combine(combine(session.emojiRecentsState, session.clipboardEntries, session.snippets) { _, _, _ -> }) { render, _ -> render }
                .collect(onRender)
        }
        return RenderSubscription(job)
    }

    fun performStripAction(action: StripAction) = session.perform(action)

    fun showKeys() = session.showPanel(KeyboardPanel.Keys)

    fun deleteBackward() = session.onKey(KeyAction.Backspace)

    // region Emoji panel

    /** Tab icons: recents first, then the catalog's categories. */
    val emojiTabs: List<String> get() = listOf(RECENTS_TAB_ICON) + session.emojiCatalog.categories.map { it.icon }

    /** Opens on recents only when there are some. */
    val initialEmojiTab: Int get() = if (session.emojiRecentsState.value.isEmpty()) 1 else 0

    fun emojis(tab: Int): List<String> = emojiList(tab).map { it.value }

    /** By value, not position: recents reorder as soon as one is used. */
    fun commitEmoji(value: String) {
        val emoji = session.emojiRecentsState.value.firstOrNull { it.value == value } ?: session.emojiCatalog.find(value) ?: return
        session.onEmoji(emoji)
    }

    /** Leaves the panel for the letters, which now type into the search query. */
    fun startEmojiSearch() = session.startEmojiSearch()

    fun endEmojiSearch() = session.endEmojiSearch()

    private fun emojiResults(query: String?): List<String> = when {
        query == null -> emptyList()
        query.isBlank() -> session.emojiRecentsState.value.map { it.value }
        else -> session.emojiCatalog.search(query).map { it.value }
    }

    /** The emoji's name, for VoiceOver. */
    fun emojiName(value: String): String = session.emojiCatalog.find(value)?.name ?: value

    private fun emojiList(tab: Int): List<Emoji> =
        if (tab == 0) session.emojiRecentsState.value
        else session.emojiCatalog.categories.getOrNull(tab - 1)?.let(session.emojiCatalog::emojis).orEmpty()


    // endregion

    // region Clipboard panel

    /** False without Full Access: the panel explains why it is empty. */
    val clipboardAvailable: Boolean get() = session.clipboardAvailable

    val clips: List<ClipItem> get() = session.clipboardEntries.value.map { ClipItem(it.id, it.text, it.pinned) }

    fun pasteClip(id: Long) = clip(id)?.let(session::onPaste)

    fun toggleClipPin(id: Long) = clip(id)?.let { session.setClipPinned(it, !it.pinned) }

    fun deleteClip(id: Long) = clip(id)?.let(session::removeClip)

    fun clearClips() = session.clearClips()

    private fun clip(id: Long) = session.clipboardEntries.value.firstOrNull { it.id == id }

    /** The user's reusable texts; they need no Full Access. */
    val snippets: List<String> get() = session.snippets.value

    fun pasteSnippet(index: Int) {
        session.snippets.value.getOrNull(index)?.let(session::onSnippet)
    }

    // endregion

    // region Quick settings panel

    /** False without Full Access: the panel shows the settings but says why it can't change them. */
    val quickSettingsEditable: Boolean get() = session.quickSettingsEditable

    val quickSettings: List<QuickSettingItem>
        get() {
            val prefs = session.preferences.value
            return QuickSetting.entries.map { QuickSettingItem(it.ordinal, it.title, it.valueLabel(prefs), it.isOn(prefs), it != QuickSetting.Theme) }
        }

    fun toggleQuickSetting(id: Int) {
        QuickSetting.entries.getOrNull(id)?.let(session::toggle)
    }

    // endregion

    private companion object {
        const val RECENTS_TAB_ICON = "🕘"
    }

    fun dispose() {
        session.stop()
        scope.cancel()
    }
}

/** A quick-settings tile as the iOS panel shows it; [isSwitch] is false for choices (the theme). */
data class QuickSettingItem(val id: Int, val title: String, val value: String, val on: Boolean, val isSwitch: Boolean)

/** A clipboard entry as the iOS panel shows it. */
data class ClipItem(val id: Long, val text: String, val pinned: Boolean)

class RenderSubscription internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}
