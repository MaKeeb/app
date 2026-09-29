package com.makeeb.engine.input

import com.makeeb.core.common.Graphemes
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
import com.makeeb.engine.prediction.KeyPositions
import com.makeeb.engine.prediction.SuggestionEngine
import com.makeeb.engine.prediction.TapPoint
import com.makeeb.engine.prediction.TypingContext
import com.makeeb.platform.host.KeyboardHost
import com.makeeb.platform.host.TextHost
import com.makeeb.platform.host.TextSelection
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
    /** The emoji a whole typed word names, for the strip ("pizza" → 🍕); none by default. */
    private val emojiForWord: (String) -> String? = { null },
) {
    private var host: TextHost = DetachedTextHost
    private var keyboardHost: KeyboardHost? = null

    /** Serves reads where they are expensive (Android); null where the host's own reads are cheap. */
    private var mirror: TextMirror? = null

    private val mutableState = MutableStateFlow(KeyboardState(layout = layoutFor(KeyboardMode.Letters, EditorAttributes())))
    val state: StateFlow<KeyboardState> = mutableState.asStateFlow()

    private var lastShiftPress: ComparableTimeMark? = null
    private var lastSpace: ComparableTimeMark? = null

    /** The last edit was a strip word and the space after it; punctuation typed next takes that space. */
    private var suggestionSpace = false

    /** The last autocorrection, undone if the very next key is Backspace. */
    private var pendingRevert: AutoCorrection? = null

    /** A word the user just un-corrected; it is not corrected again on the next separator. */
    private var rejectedCorrection: String? = null

    /** A word just forgotten from the strip, often the one being typed: committing it next doesn't learn it back. */
    private var forgottenWord: String? = null

    private data class AutoCorrection(val original: String, val corrected: String, val separator: String) {
        val committed: String get() = corrected + separator
    }

    // region Session lifecycle

    /** [selection] is where the field's selection starts, when the platform says (Android). */
    fun startInput(textHost: TextHost, keyboardHost: KeyboardHost, attributes: EditorAttributes, selection: TextSelection? = null) {
        mirror = if (textHost.readsAreCheap) null else TextMirror(textHost, selection)
        host = mirror ?: textHost
        this.keyboardHost = keyboardHost
        pendingRevert = null
        rejectedCorrection = null
        forgottenWord = null
        lastSpace = null
        suggestionSpace = false
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
        mirror = null
        keyboardHost = null
        mutableState.update { it.copy(active = false, composing = "", suggestions = emptyList(), panel = KeyboardPanel.Keys, emojiSearch = null, forgetOffer = null) }
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
     * The field's text or selection changed, possibly not by us, and the platform can't say how
     * (iOS: `textDidChange`/`selectionDidChange`). Everything is read afresh.
     */
    fun onExternalChange() {
        mirror?.invalidate()
        resyncAfterExternalChange()
    }

    /**
     * The field reports its selection (Android: `onUpdateSelection`), which it also does, late,
     * for the keyboard's own edits. Only a change the keyboard didn't make is acted on.
     */
    fun onSelectionChanged(start: Int, end: Int) {
        val known = mirror ?: return onExternalChange()
        if (known.onSelectionChanged(TextSelection(start, end))) resyncAfterExternalChange()
    }

    private fun resyncAfterExternalChange() {
        suggestionSpace = false
        val revert = pendingRevert
        if (revert != null && !host.textBeforeCursor(revert.committed.length).endsWith(revert.committed)) {
            pendingRevert = null
        }
        resyncWithHost()
    }

    // endregion

    // region Input

    /** [tap] is where a typed letter was tapped, in layout units, when the touch layer knows. */
    fun onKey(action: KeyAction, tap: TapPoint? = null) {
        pendingTap = tap?.takeIf { action is KeyAction.Text }
        // Typing on means "keep it".
        dismissForgetOffer()
        if (state.value.emojiSearch != null && searchKey(action)) return
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
            // A settings change: KeyboardSession writes it, and the new languages arrive with the preferences.
            is KeyAction.SelectLanguage -> Unit
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
        suggestionSpace = false
    }

    // region Emoji search

    /** Keys now type into an emoji search query; the letters show so the user can type it. */
    fun startEmojiSearch() {
        mutableState.update {
            it.copy(
                emojiSearch = "",
                panel = KeyboardPanel.Keys,
                mode = KeyboardMode.Letters,
                layout = layoutFor(KeyboardMode.Letters, it.editor),
                shift = ShiftState.Off,
                suggestions = emptyList(),
                forgetOffer = null,
            )
        }
    }

    /** Leave the search, back to the emoji panel ([toPanel]) or straight to the keys. */
    fun endEmojiSearch(toPanel: KeyboardPanel = KeyboardPanel.Emoji) {
        mutableState.update { it.copy(emojiSearch = null, panel = toPanel) }
        if (toPanel == KeyboardPanel.Keys) resyncWithHost()
    }

    /**
     * A key while searching. Returns false for keys that keep their usual meaning (mode
     * switches, the globe). The field is never touched.
     */
    private fun searchKey(action: KeyAction): Boolean {
        val query = state.value.emojiSearch ?: return false
        fun setQuery(next: String) = mutableState.update { it.copy(emojiSearch = next, shift = ShiftState.Off) }
        when (action) {
            is KeyAction.Text -> setQuery(query + action.text.lowercase())
            KeyAction.Space -> if (query.isNotEmpty() && !query.endsWith(' ')) setQuery("$query ")
            KeyAction.Backspace -> setQuery(query.dropLast(Graphemes.lastLength(query)))
            KeyAction.DeleteWord -> setQuery(query.trimEnd().dropLastWhile { it != ' ' })
            KeyAction.Enter -> endEmojiSearch()
            is KeyAction.ShowPanel -> endEmojiSearch(action.panel)
            KeyAction.Shift, is KeyAction.MoveCursor, is KeyAction.MoveCursorByWord, KeyAction.None -> Unit
            is KeyAction.SwitchMode, KeyAction.NextInputMethod, KeyAction.ShowInputMethodPicker, is KeyAction.SelectLanguage -> return false
        }
        return true
    }

    // endregion

    /** Commit text from a panel (emoji, clipboard) verbatim: no shift, no autocorrect. */
    fun commitRawText(text: String) {
        pendingRevert = null
        suggestionSpace = false
        host.commitText(text)
        // An emoji picked from search results leaves the search open for the next one.
        if (state.value.emojiSearch != null) return
        afterEdit(composing = TextBoundaries.trailingWord(state.value.composing + text))
    }

    fun onSuggestionSelected(suggestion: Suggestion) {
        pendingRevert = null
        suggestionSpace = false
        if (suggestion.kind == Suggestion.Kind.Emoji) {
            confirmMirror()
            // The emoji takes the word's place, like any suggestion.
            host.replaceBeforeCursor(state.value.composing.length, suggestion.text + " ")
            rejectedCorrection = null
            afterEdit(composing = "")
            return
        }
        if (suggestion.kind == Suggestion.Kind.Punctuation) {
            // "word " + "," → "word, ": the shortcut takes the space's place.
            if (host.textBeforeCursor(1) == " ") host.replaceBeforeCursor(1, suggestion.text + " ")
            else host.commitText(suggestion.text + " ")
            afterEdit(composing = "")
            return
        }
        confirmMirror()
        val word = state.value.composing
        rejectedCorrection = null
        // A predicted word (nothing typed yet) is inserted; anything else replaces the word typed.
        if (word.isEmpty()) host.commitText(suggestion.text + " ") else host.replaceBeforeCursor(word.length, suggestion.text + " ")
        // Picking the word as typed says it is meant, however unusual.
        learn(suggestion.text, kept = suggestion.kind == Suggestion.Kind.Typed)
        // The space came with the word: a quick space next is not a double space, and punctuation
        // next takes the space's place, as after a typed space.
        lastSpace = null
        afterEdit(composing = "", consumeOneShot = true)
        suggestionSpace = true
    }

    // region Forgetting

    /**
     * A long press on a strip word. For a word the keyboard learned (not a dictionary word, which
     * can't be forgotten) the strip then asks whether to forget it ([KeyboardState.forgetOffer]).
     * Returns whether it asks.
     */
    fun onSuggestionLongPressed(suggestion: Suggestion): Boolean {
        if (suggestion.kind == Suggestion.Kind.Punctuation || suggestion.kind == Suggestion.Kind.Emoji) return false
        if (!suggestionEngine.isLearned(suggestion.text)) return false
        mutableState.update { it.copy(forgetOffer = suggestion.text) }
        return true
    }

    /** The user confirmed: the word is forgotten, and the strip shows suggestions without it. */
    fun forgetOfferedWord() {
        val word = state.value.forgetOffer ?: return
        suggestionEngine.forget(word)
        forgottenWord = word
        resyncWithHost()
    }

    /** The user kept the word (or typed on): the suggestions come back. */
    fun dismissForgetOffer() {
        if (state.value.forgetOffer != null) mutableState.update { it.copy(forgetOffer = null) }
    }

    // endregion

    private fun typeText(text: String) {
        pendingRevert = null
        val current = state.value
        val output = if (current.shift.isUppercase) text.uppercase() else text
        if (output.length == 1 && output[0] in CORRECTING_PUNCTUATION && current.composing.isNotEmpty()) {
            commitSeparator(output)
        } else if (output.length == 1 && output[0] in CORRECTING_PUNCTUATION && followsWordAndSpace(current.editor) && confirmMirror()) {
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
            before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit() && confirmMirror()
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
        // Never with the caret inside a word: "T|he" + "ok " is not the word "Tok".
        val insideWord = host.textAfterCursor(1).firstOrNull()?.isLetterOrDigit() == true
        fun correctionFor(word: String) = word.takeIf { it.isNotEmpty() && it != rejectedCorrection && autoCorrectEnabled() && !insideWord }
            ?.let { suggestionEngine.suggest(typingContext(it, host.textBeforeCursor(CONTEXT_LENGTH))).autoCorrection }
            ?.takeIf { it != word }
        var word = state.value.composing
        var correction = correctionFor(word)
        // A correction replaces what the mirror says was typed: make sure the field agrees.
        if (correction != null && !confirmMirror()) {
            word = state.value.composing
            correction = correctionFor(word)
        }
        // Undoing its autocorrection and committing it anyway says the word is meant.
        val kept = word.isNotEmpty() && word == rejectedCorrection
        rejectedCorrection = null

        if (correction != null) {
            host.replaceBeforeCursor(word.length, correction + separator)
            pendingRevert = AutoCorrection(word, correction, separator)
        } else {
            host.commitText(separator)
            pendingRevert = null
            if (word.isNotEmpty()) learn(word, kept)
        }
        afterEdit(composing = "", consumeOneShot = true)
    }

    private fun backspace() {
        val revert = pendingRevert
        pendingRevert = null
        if (revert != null) confirmMirror()
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
        confirmMirror()
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
        // Caps lock turns suggestions to capitals, and leaving it turns them back; Shift
        // capitalises predictions as it does the next letter.
        if (state.value.composing.isNotEmpty() || state.value.suggestions.any { it.kind == Suggestion.Kind.NextWord }) resyncWithHost()
    }

    private fun switchMode(mode: KeyboardMode) {
        mutableState.update { it.copy(mode = mode, layout = layoutFor(mode, it.editor), panel = KeyboardPanel.Keys, shift = ShiftState.Off) }
        if (mode == KeyboardMode.Letters && state.value.emojiSearch == null) resyncWithHost()
    }

    // endregion

    // region State derivation

    /**
     * Before an edit that deletes what the mirror says was typed, check the field still agrees
     * ([TextMirror.verify]). When it doesn't, the state is rebuilt from the field and this
     * returns false. Always true where there is no mirror.
     */
    private fun confirmMirror(): Boolean {
        val known = mirror ?: return true
        if (known.verify()) return true
        resyncWithHost()
        return false
    }

    private fun resyncWithHost() {
        afterEdit(composing = TextBoundaries.trailingWord(host.textBeforeCursor(CONTEXT_LENGTH)))
    }

    /**
     * Recompute everything derived from the text around the caret: auto-shift and suggestions.
     * On Android the read comes from the [TextMirror], not the app.
     */
    private fun afterEdit(composing: String, consumeOneShot: Boolean = false) {
        alignTaps(state.value.composing, composing)
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
                suggestions = casedForShift(suggestions, shift, current.editor),
                forgetOffer = null,
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

    /**
     * Suggestions follow the shift key as typed letters do. Caps lock turns them all to capitals.
     * A one-shot shift (a sentence start where the field and the user's setting capitalise, or
     * Shift pressed) capitalises a predicted word; typed words already carry the user's case.
     */
    private fun casedForShift(suggestions: List<Suggestion>, shift: ShiftState, editor: EditorAttributes): List<Suggestion> = when {
        shift == ShiftState.Locked -> suggestions.map { it.copy(text = it.text.uppercase()) }
        shift == ShiftState.OneShot -> suggestions.map { suggestion ->
            when {
                suggestion.kind != Suggestion.Kind.NextWord || suggestion.text.any(Char::isUpperCase) -> suggestion
                editor.capitalization == Capitalization.Characters -> suggestion.copy(text = suggestion.text.uppercase())
                else -> suggestion.copy(text = suggestion.text.replaceFirstChar(Char::uppercaseChar))
            }
        }
        else -> suggestions
    }

    private fun suggestionsFor(editor: EditorAttributes, prefs: KeyboardPreferences, composing: String, textBefore: String): List<Suggestion> {
        if (!prefs.showSuggestions || !editor.suggestions || editor.isPassword) return emptyList()
        if (composing.isEmpty()) return predictions(editor, textBefore).ifEmpty { punctuationShortcuts(editor, textBefore) }
        // Learned words are flagged so a long press can offer to forget them.
        val words = suggestionEngine.suggest(typingContext(composing, textBefore)).suggestions
            .map { if (suggestionEngine.isLearned(it.text)) it.copy(learned = true) else it }
        val emoji = composing.takeIf { prefs.emojiSuggestions && it.length >= MIN_EMOJI_WORD }?.let(emojiForWord)
            ?: return words
        // The third slot: the best word keeps the middle, the next best stays one tap away.
        return words.take(2) + Suggestion(emoji, Suggestion.Kind.Emoji)
    }

    /** Where each letter of the composing word was tapped; null where unknown (a resync, a paste). */
    private var composingTaps: List<TapPoint?> = emptyList()

    /** The tap of the key being handled, until [afterEdit] files it. */
    private var pendingTap: TapPoint? = null

    /** Keeps [composingTaps] in step: a letter typed appends its tap, a delete drops the last. */
    private fun alignTaps(previous: String, composing: String) {
        val known = composingTaps.take(previous.length).let { it + List(previous.length - it.size) { null } }
        composingTaps = when {
            composing == previous -> known
            composing.length == previous.length + 1 && composing.startsWith(previous) -> known + pendingTap
            composing.length < previous.length && previous.startsWith(composing) -> known.take(composing.length)
            else -> List(composing.length) { null }
        }
        pendingTap = null
    }

    /**
     * The words likely to come next, mid-sentence after a space: where the punctuation shortcuts
     * were, in the same fields, and also after a comma, semicolon or colon. They take all three
     * slots, best in the middle; the shortcuts come back only when there are no predictions (no
     * pack yet). "," and "." sit either side of the space bar and "?" "!" on the full stop's
     * long press, and all of them still take the space's place ([followsWordAndSpace]). At the
     * start of a field or a sentence nothing is predicted and the strip keeps its toolbar.
     */
    private fun predictions(editor: EditorAttributes, textBefore: String): List<Suggestion> {
        if (!isRunningText(editor) || textBefore.length < 2 || textBefore.last() != ' ') return emptyList()
        val before = textBefore[textBefore.length - 2]
        if (!before.isLetterOrDigit() && before !in CLAUSE_PUNCTUATION) return emptyList()
        return suggestionEngine.suggest(typingContext("", textBefore)).suggestions.filter { it.kind == Suggestion.Kind.NextWord }
    }

    /** What the suggestion engine needs about [composing], the word ending [textBefore]. */
    private fun typingContext(composing: String, textBefore: String): TypingContext {
        val before = if (textBefore.endsWith(composing)) textBefore.dropLast(composing.length) else textBefore
        val sentence = TextBoundaries.wordsBefore(before, count = 2)
        return TypingContext(
            composing = composing,
            previousWords = sentence.words,
            previousWordsStartSentence = sentence.fromSentenceStart,
            keys = letterKeys(),
            atSentenceStart = TextBoundaries.isSentenceStart(before),
            taps = composingTaps.takeIf { it.size == composing.length }.orEmpty(),
            strength = preferences.value.autoCorrectStrength,
            languages = preferences.value.languageTags,
        )
    }

    private var letterKeysLayout: KeyboardLayout? = null
    private var letterKeys: KeyPositions? = null

    /** Key centres of the letters layout last shown, in key widths and rows, for key-aware typo costs. */
    private fun letterKeys(): KeyPositions? {
        val layout = state.value.layout
        if (layout.mode == KeyboardMode.Letters && layout != letterKeysLayout) {
            letterKeysLayout = layout
            val centres = HashMap<Char, Pair<Float, Float>>()
            var top = 0f
            for (row in layout.rows) {
                var x = ((layout.unitsPerRow - row.units) / 2).coerceAtLeast(0f)
                for (key in row.keys) {
                    val text = (key.action as? KeyAction.Text)?.text
                    if (text != null && text.length == 1) centres[text[0].lowercaseChar()] = (x + key.width / 2) to (top + row.heightWeight / 2)
                    x += key.width
                }
                top += row.heightWeight
            }
            letterKeys = KeyPositions { centres[it] }
        }
        return letterKeys
    }

    /** The previous key was a space that ended a word (or a strip word came with one), in running text. */
    private fun followsWordAndSpace(editor: EditorAttributes): Boolean {
        if ((lastSpace == null && !suggestionSpace) || editor.fieldType != FieldType.Text) return false
        val before = host.textBeforeCursor(2)
        return before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()
    }

    /**
     * After a word and one space in running text, the punctuation most likely to follow. Not in
     * search or go fields: queries and addresses aren't sentences.
     */
    private fun punctuationShortcuts(editor: EditorAttributes, textBefore: String): List<Suggestion> {
        val afterWord = textBefore.length >= 2 && textBefore.last() == ' ' && textBefore[textBefore.length - 2].isLetterOrDigit()
        if (!isRunningText(editor) || !afterWord) return emptyList()
        return PUNCTUATION_SHORTCUTS.map { Suggestion(it, Suggestion.Kind.Punctuation) }
    }

    /** Sentences, not queries or addresses: no search or go fields. */
    private fun isRunningText(editor: EditorAttributes): Boolean =
        editor.fieldType == FieldType.Text && editor.imeAction != ImeAction.Search && editor.imeAction != ImeAction.Go

    private fun autoCorrectEnabled(): Boolean {
        val editor = state.value.editor
        return preferences.value.autoCorrect && editor.autoCorrect && !editor.isPassword
    }

    /**
     * All learning goes through here. Never in incognito (the field asked for it, or the user did;
     * password fields always are), nor in fields that turn autocorrection off: user names, codes
     * and addresses aren't vocabulary, and iOS gives no other hint that a field shouldn't be
     * learned from. [kept]: the user chose the word on purpose, which counts for more than typing
     * it ([SuggestionEngine.keep]).
     */
    private fun learn(word: String, kept: Boolean = false) {
        val forgotten = forgottenWord
        forgottenWord = null
        val current = state.value
        if (current.incognito || !current.editor.autoCorrect || word.equals(forgotten, ignoreCase = true)) return
        if (kept) suggestionEngine.keep(word) else suggestionEngine.learn(word)
    }

    private fun layoutFor(mode: KeyboardMode, editor: EditorAttributes): KeyboardLayout {
        val prefs = preferences.value
        val options = LayoutOptions(
            letterLayoutId = prefs.letterLayoutId,
            languageTags = prefs.languageTags,
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
        /** Enough context for auto-cap and two previous words. */
        const val CONTEXT_LENGTH = 64
        val DOUBLE_SPACE_WINDOW = 800.milliseconds
        val DOUBLE_TAP_WINDOW = 350.milliseconds
        val CORRECTING_PUNCTUATION = setOf('.', ',', '!', '?', ';', ':')
        val PUNCTUATION_SHORTCUTS = listOf(",", ".", "?", "!")

        /** Punctuation inside a sentence, after which the next word is still predicted. */
        val CLAUSE_PUNCTUATION = setOf(',', ';', ':')
        /** Short words name too many emoji by accident ("i", "ok"…). */
        const val MIN_EMOJI_WORD = 3
    }
}
