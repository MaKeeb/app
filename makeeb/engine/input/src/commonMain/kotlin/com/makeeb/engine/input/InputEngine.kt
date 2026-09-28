package com.makeeb.engine.input

import com.makeeb.core.common.TextBoundaries
import com.makeeb.core.model.Capitalization
import com.makeeb.core.model.EditorAttributes
import com.makeeb.core.model.FieldType
import com.makeeb.core.model.ImeAction
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode
import com.makeeb.core.model.KeyboardPanel
import com.makeeb.core.model.ShiftState
import com.makeeb.core.model.Suggestion
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.engine.layout.KeyboardLayout
import com.makeeb.engine.layout.LetterVariant
import com.makeeb.engine.layout.LayoutOptions
import com.makeeb.engine.layout.LayoutProvider
import com.makeeb.engine.prediction.SuggestionEngine
import com.makeeb.engine.prediction.TypingContext
import com.makeeb.platform.host.KeyboardHost
import com.makeeb.platform.host.TextHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The shared typing state machine. Platform shells feed it key actions and editor events; it
 * edits the field through [TextHost] and publishes [state] for the UI.
 *
 * Not thread-safe: call it from the main thread only, as both platforms deliver input there.
 */
class InputEngine(
    private val layouts: LayoutProvider,
    private val suggestionEngine: SuggestionEngine,
    private val preferences: StateFlow<KeyboardPreferences>,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    private var host: TextHost = DetachedTextHost
    private var keyboardHost: KeyboardHost? = null

    private val mutableState = MutableStateFlow(KeyboardState(layout = layoutFor(KeyboardMode.Letters, EditorAttributes())))
    val state: StateFlow<KeyboardState> = mutableState.asStateFlow()

    private var lastShiftPress: ComparableTimeMark? = null
    private var lastSpace: ComparableTimeMark? = null

    /** The last autocorrection, undone if the very next key is Backspace. */
    private var pendingRevert: AutoCorrection? = null

    /** A word the user just un-corrected; it is not corrected again on the next separator. */
    private var rejectedCorrection: String? = null

    private data class AutoCorrection(val original: String, val corrected: String, val separator: String) {
        val committed: String get() = corrected + separator
    }

    // region Session lifecycle

    fun startInput(textHost: TextHost, keyboardHost: KeyboardHost, attributes: EditorAttributes) {
        host = textHost
        this.keyboardHost = keyboardHost
        pendingRevert = null
        rejectedCorrection = null
        lastSpace = null
        val mode = attributes.initialMode
        mutableState.value = KeyboardState(
            layout = layoutFor(mode, attributes),
            mode = mode,
            editor = attributes,
            active = true,
            manualIncognito = state.value.manualIncognito,
        )
        resyncWithHost()
    }

    fun finishInput() {
        host = DetachedTextHost
        keyboardHost = null
        mutableState.update { it.copy(active = false, composing = "", suggestions = emptyList(), panel = KeyboardPanel.Keys) }
    }

    /** The user's incognito toggle; the field's own request applies regardless. */
    fun setIncognito(on: Boolean) {
        mutableState.update { it.copy(manualIncognito = on) }
    }

    /** Preferences that shape the layout (number row, letter layout) changed. */
    fun refreshLayout() {
        mutableState.update { it.copy(layout = layoutFor(it.mode, it.editor)) }
    }

    /**
     * The field's text or selection changed, possibly not by us (the user tapped elsewhere, the
     * app rewrote the text). Android: `onUpdateSelection`; iOS: `textDidChange`/`selectionDidChange`.
     */
    fun onExternalChange() {
        val revert = pendingRevert
        if (revert != null && !host.textBeforeCursor(revert.committed.length).endsWith(revert.committed)) {
            pendingRevert = null
        }
        resyncWithHost()
    }

    // endregion

    // region Input

    fun onKey(action: KeyAction) {
        when (action) {
            is KeyAction.Text -> typeText(action.text)
            KeyAction.Space -> typeSpace()
            KeyAction.Backspace -> backspace()
            KeyAction.DeleteWord -> deleteWord()
            KeyAction.Enter -> enter()
            KeyAction.Shift -> toggleShift()
            is KeyAction.SwitchMode -> switchMode(action.mode)
            is KeyAction.ShowPanel -> mutableState.update { it.copy(panel = action.panel) }
            KeyAction.NextInputMethod -> keyboardHost?.switchToNextInputMethod()
            KeyAction.ShowInputMethodPicker -> keyboardHost?.showInputMethodPicker()
            is KeyAction.MoveCursor -> {
                host.moveCursor(action.offset)
                pendingRevert = null
                resyncWithHost()
            }
            is KeyAction.MoveCursorByWord -> {
                val offset = if (action.direction < 0) -TextBoundaries.previousWordStart(host.textBeforeCursor(CONTEXT_LENGTH))
                else TextBoundaries.nextWordEnd(host.textAfterCursor(CONTEXT_LENGTH))
                if (offset != 0) host.moveCursor(offset)
                pendingRevert = null
                resyncWithHost()
            }
            KeyAction.None -> Unit
        }
        if (action != KeyAction.Space) lastSpace = null
    }

    /** Commit text from a panel (emoji, clipboard) verbatim: no shift, no autocorrect. */
    fun commitRawText(text: String) {
        pendingRevert = null
        host.commitText(text)
        afterEdit(composing = TextBoundaries.trailingWord(state.value.composing + text))
    }

    fun onSuggestionSelected(suggestion: Suggestion) {
        pendingRevert = null
        if (suggestion.kind == Suggestion.Kind.Punctuation) {
            // "word " + "," → "word, ": the shortcut takes the space's place.
            if (host.textBeforeCursor(1) == " ") host.replaceBeforeCursor(1, suggestion.text + " ")
            else host.commitText(suggestion.text + " ")
            afterEdit(composing = "")
            return
        }
        val word = state.value.composing
        rejectedCorrection = null
        host.replaceBeforeCursor(word.length, suggestion.text + " ")
        learn(suggestion.text)
        afterEdit(composing = "")
    }

    private fun typeText(text: String) {
        pendingRevert = null
        val current = state.value
        val output = if (current.shift.isUppercase) text.uppercase() else text
        if (output.length == 1 && output[0] in CORRECTING_PUNCTUATION && current.composing.isNotEmpty()) {
            commitSeparator(output)
        } else if (output.length == 1 && output[0] in CORRECTING_PUNCTUATION && followsWordAndSpace(current.editor)) {
            // "word " + "," → "word, ": punctuation belongs against the word, the space after it.
            host.replaceBeforeCursor(1, "$output ")
            afterEdit(composing = "", consumeOneShot = true)
        } else {
            host.commitText(output)
            afterEdit(composing = TextBoundaries.trailingWord(current.composing + output), consumeOneShot = true)
        }
    }

    private fun typeSpace() {
        val sinceLastSpace = lastSpace?.elapsedNow()
        val prefs = preferences.value
        val before = host.textBeforeCursor(2)
        if (prefs.doubleSpacePeriod && sinceLastSpace != null && sinceLastSpace < DOUBLE_SPACE_WINDOW &&
            before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()
        ) {
            pendingRevert = null
            host.replaceBeforeCursor(1, ". ")
            lastSpace = null
            afterEdit(composing = "")
            return
        }
        commitSeparator(" ")
        lastSpace = timeSource.markNow()
    }

    /** Commit a word separator, autocorrecting the word before it when confident. */
    private fun commitSeparator(separator: String) {
        val word = state.value.composing
        val correction = word.takeIf { it.isNotEmpty() && it != rejectedCorrection && autoCorrectEnabled() }
            ?.let { suggestionEngine.suggest(TypingContext(it)).autoCorrection }
            ?.takeIf { it != word }
        rejectedCorrection = null

        if (correction != null) {
            host.replaceBeforeCursor(word.length, correction + separator)
            pendingRevert = AutoCorrection(word, correction, separator)
        } else {
            host.commitText(separator)
            pendingRevert = null
            if (word.isNotEmpty()) learn(word)
        }
        afterEdit(composing = "", consumeOneShot = true)
    }

    private fun backspace() {
        val revert = pendingRevert
        pendingRevert = null
        if (revert != null && host.textBeforeCursor(revert.committed.length) == revert.committed) {
            host.replaceBeforeCursor(revert.committed.length, revert.original)
            rejectedCorrection = revert.original
            afterEdit(composing = revert.original)
            return
        }
        host.deleteBackward()
        resyncWithHost()
    }

    /**
     * The spaces before the caret and the word before them. Anything else (punctuation, emoji)
     * goes one grapheme at a time, so a word delete never splits an emoji.
     */
    private fun deleteWord() {
        pendingRevert = null
        val before = host.textBeforeCursor(CONTEXT_LENGTH)
        val trimmed = before.trimEnd()
        val spaces = before.length - trimmed.length
        val word = trimmed.takeLastWhile { it.isLetterOrDigit() || it == '\'' || it == '’' }
        when {
            word.isNotEmpty() -> host.replaceBeforeCursor(word.length + spaces, "")
            spaces > 0 -> host.replaceBeforeCursor(spaces, "")
            else -> host.deleteBackward()
        }
        resyncWithHost()
    }

    private fun enter() {
        pendingRevert = null
        val action = state.value.editor.imeAction
        if (action == ImeAction.None || !host.performEditorAction(action)) {
            host.commitText("\n")
        }
        afterEdit(composing = "")
    }

    private fun toggleShift() {
        val now = timeSource.markNow()
        val doubleTap = lastShiftPress?.let { (now - it) < DOUBLE_TAP_WINDOW } ?: false
        lastShiftPress = now
        mutableState.update { current ->
            val next = when (current.shift) {
                ShiftState.Off -> ShiftState.OneShot
                ShiftState.OneShot -> if (doubleTap) ShiftState.Locked else ShiftState.Off
                ShiftState.Locked -> ShiftState.Off
            }
            current.copy(shift = next)
        }
        // Caps lock turns suggestions to capitals, and leaving it turns them back.
        if (state.value.composing.isNotEmpty()) resyncWithHost()
    }

    private fun switchMode(mode: KeyboardMode) {
        mutableState.update { it.copy(mode = mode, layout = layoutFor(mode, it.editor), panel = KeyboardPanel.Keys, shift = ShiftState.Off) }
        if (mode == KeyboardMode.Letters) resyncWithHost()
    }

    // endregion

    // region State derivation

    private fun resyncWithHost() {
        afterEdit(composing = TextBoundaries.trailingWord(host.textBeforeCursor(CONTEXT_LENGTH)))
    }

    /**
     * Recompute everything derived from the text around the caret: auto-shift and suggestions.
     * One host read per edit keeps IPC to a minimum.
     */
    private fun afterEdit(composing: String, consumeOneShot: Boolean = false) {
        val textBefore = host.textBeforeCursor(CONTEXT_LENGTH)
        val prefs = preferences.value
        mutableState.update { current ->
            val shift = when {
                current.shift == ShiftState.Locked -> ShiftState.Locked
                current.mode != KeyboardMode.Letters -> ShiftState.Off
                shouldAutoCapitalize(current.editor, prefs, textBefore) -> ShiftState.OneShot
                consumeOneShot -> ShiftState.Off
                else -> current.shift
            }
            val suggestions = suggestionsFor(current.editor, prefs, composing, textBefore)
            current.copy(
                composing = composing,
                shift = shift,
                suggestions = if (shift == ShiftState.Locked) suggestions.map { it.copy(text = it.text.uppercase()) } else suggestions,
            )
        }
    }

    private fun shouldAutoCapitalize(editor: EditorAttributes, prefs: KeyboardPreferences, textBefore: String): Boolean =
        when (editor.capitalization) {
            Capitalization.Characters -> true
            Capitalization.Words -> TextBoundaries.isWordStart(textBefore)
            Capitalization.Sentences -> prefs.autoCapitalize && TextBoundaries.isSentenceStart(textBefore)
            Capitalization.None -> false
        }

    private fun suggestionsFor(editor: EditorAttributes, prefs: KeyboardPreferences, composing: String, textBefore: String): List<Suggestion> {
        if (!prefs.showSuggestions || !editor.suggestions || editor.isPassword) return emptyList()
        if (composing.isEmpty()) return punctuationShortcuts(editor, textBefore)
        val context = TypingContext(composing, TextBoundaries.previousWords(textBefore, count = 2))
        return suggestionEngine.suggest(context).suggestions
    }

    /** The previous key was a space that ended a word, in running text. */
    private fun followsWordAndSpace(editor: EditorAttributes): Boolean {
        if (lastSpace == null || editor.fieldType != FieldType.Text) return false
        val before = host.textBeforeCursor(2)
        return before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()
    }

    /**
     * After a word and one space in running text, the punctuation most likely to follow. Not in
     * search or go fields: queries and addresses aren't sentences.
     */
    private fun punctuationShortcuts(editor: EditorAttributes, textBefore: String): List<Suggestion> {
        val afterWord = textBefore.length >= 2 && textBefore.last() == ' ' && textBefore[textBefore.length - 2].isLetterOrDigit()
        val runningText = editor.fieldType == FieldType.Text && editor.imeAction != ImeAction.Search && editor.imeAction != ImeAction.Go
        if (!runningText || !afterWord) return emptyList()
        return PUNCTUATION_SHORTCUTS.map { Suggestion(it, Suggestion.Kind.Punctuation) }
    }

    private fun autoCorrectEnabled(): Boolean {
        val editor = state.value.editor
        return preferences.value.autoCorrect && editor.autoCorrect && !editor.isPassword
    }

    private fun learn(word: String) {
        if (!state.value.incognito) suggestionEngine.learn(word)
    }

    private fun layoutFor(mode: KeyboardMode, editor: EditorAttributes): KeyboardLayout {
        val prefs = preferences.value
        val options = LayoutOptions(
            letterLayoutId = prefs.letterLayoutId,
            numberRow = prefs.numberRow,
            switchKey = keyboardHost?.needsInputMethodSwitchKey ?: false,
            variant = when (editor.fieldType) {
                FieldType.Email -> LetterVariant.Email
                FieldType.Uri -> LetterVariant.Url
                else -> LetterVariant.Text
            },
        )
        return layouts.layout(mode, options)
    }

    // endregion

    private companion object {
        /** Enough context for auto-cap and two previous words; small to keep IPC cheap. */
        const val CONTEXT_LENGTH = 64
        val DOUBLE_SPACE_WINDOW = 800.milliseconds
        val DOUBLE_TAP_WINDOW = 350.milliseconds
        val CORRECTING_PUNCTUATION = setOf('.', ',', '!', '?', ';', ':')
        val PUNCTUATION_SHORTCUTS = listOf(",", ".", "?", "!")
    }
}
