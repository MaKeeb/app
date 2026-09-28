package com.makeeb.engine.input

import com.makeeb.core.common.Graphemes
import com.makeeb.core.model.ImeAction
import com.makeeb.platform.host.TextHost
import com.makeeb.platform.host.TextSelection

/**
 * The text around the caret, kept by the keyboard so typing doesn't ask the app for it on every
 * key. Android's `InputConnection` getters are blocking IPC into the app (up to 2 s each), and
 * the engine reads the text before the caret after every edit.
 *
 * The mirror fetches a window of text once, then applies its own edits to that copy. It is only
 * as good as its view of the field, so the platform reports every selection change
 * ([onSelectionChanged]), including the late reports of the keyboard's own edits. A report the
 * mirror didn't predict (the user tapped elsewhere, the app rewrote or filtered the text) drops
 * the copy, and the next read asks the app again.
 */
internal class TextMirror(private val host: TextHost, selection: TextSelection?) : TextHost {
    /** Text before the selection start; null until fetched. */
    private var before: StringBuilder? = null

    /** [before] reaches the start of the field (a fetch returned less than it asked for). */
    private var beforeIsWhole = false

    /** Text after the selection end; null until fetched. */
    private var after: String? = null
    private var afterIsWhole = false

    /** The selected text; null until fetched. */
    private var selected: String? = null

    /** Where the field's selection will be once the keyboard's edits land; null when unknown. */
    private var expected: TextSelection? = selection?.takeIf { it.start >= 0 && it.end >= it.start }

    /** Positions the keyboard's edits passed through, which the field may still report, oldest first. */
    private val pending = ArrayDeque<TextSelection>()

    // region Reconciliation

    /**
     * The field reports its selection. Returns true when that is news: not a position the
     * keyboard's own edits produced. The copy is then dropped.
     */
    fun onSelectionChanged(selection: TextSelection): Boolean {
        val index = pending.indexOf(selection)
        if (index >= 0) {
            repeat(index + 1) { pending.removeFirst() }
            return false
        }
        if (selection == expected && pending.isEmpty()) return false
        dropText()
        expected = selection
        pending.clear()
        return true
    }

    /**
     * Compare the copy with the field before an edit that deletes what the copy says is there
     * (an autocorrection, a suggestion, deleting a word). A field can refuse input without moving
     * its selection (a full length-limited field), which no report reveals. Returns false, and
     * adopts the field's text, when they differ. One read per word, not per key.
     */
    fun verify(): Boolean {
        val known = before ?: return true
        val fetched = host.textBeforeCursor(FETCH_LENGTH)
        val fieldStartReached = fetched.length < FETCH_LENGTH
        val overlap = minOf(known.length, fetched.length)
        val same = known.substring(known.length - overlap) == fetched.substring(fetched.length - overlap) &&
            !(beforeIsWhole && fetched.length > known.length) &&
            !(fieldStartReached && known.length > fetched.length)
        before = StringBuilder(fetched)
        beforeIsWhole = fieldStartReached
        if (!same) {
            after = null
            afterIsWhole = false
            selected = null
            expected = null
            pending.clear()
        }
        return same
    }

    /** Something changed and the platform can't say what (iOS): forget everything. */
    fun invalidate() {
        dropText()
        expected = null
        pending.clear()
    }

    private fun dropText() {
        before = null
        beforeIsWhole = false
        after = null
        afterIsWhole = false
        selected = null
    }

    /** The keyboard's edit moved the selection to [next]; unknown stays unknown until a report. */
    private fun expect(next: (TextSelection) -> TextSelection) {
        val current = expected ?: return
        val moved = next(current)
        expected = moved
        pending.addLast(moved)
        if (pending.size > MAX_PENDING) pending.removeFirst()
    }

    // endregion

    // region Reads

    override val readsAreCheap: Boolean get() = host.readsAreCheap

    override fun textBeforeCursor(maxLength: Int): String {
        val known = before
        if (known != null && (known.length >= maxLength || beforeIsWhole)) return known.takeLastString(maxLength)
        val want = maxOf(maxLength, FETCH_LENGTH)
        val fetched = host.textBeforeCursor(want)
        before = StringBuilder(fetched)
        beforeIsWhole = fetched.length < want
        return fetched.takeLast(maxLength)
    }

    override fun textAfterCursor(maxLength: Int): String {
        val known = after
        if (known != null && (known.length >= maxLength || afterIsWhole)) return known.take(maxLength)
        val want = maxOf(maxLength, FETCH_LENGTH)
        val fetched = host.textAfterCursor(want)
        after = fetched
        afterIsWhole = fetched.length < want
        return fetched.take(maxLength)
    }

    override fun selectedText(): String {
        if (expected?.isCollapsed == true) return ""
        return selected ?: host.selectedText().also { selected = it }
    }

    // endregion

    // region Edits

    override fun commitText(text: String) {
        host.commitText(text)
        appendBefore(text)
        selected = ""
        expect { TextSelection(it.start + text.length) }
    }

    override fun deleteBackward() {
        val selection = expected
        when {
            // Without positions a selection can't be ruled out: let the host decide, then re-read.
            selection == null -> {
                host.deleteBackward()
                dropText()
            }
            !selection.isCollapsed -> {
                host.deleteBackward()
                selected = ""
                expect { TextSelection(it.start) }
            }
            else -> {
                val window = textBeforeCursor(GRAPHEME_WINDOW)
                if (window.isEmpty()) {
                    // Nothing known before the caret: the host sends a real key, whose effect only
                    // the app knows (terminals keep their text out of reach).
                    host.deleteBackward()
                    dropText()
                    return
                }
                val grapheme = window.substring(window.length - Graphemes.lastLength(window))
                host.deleteGrapheme(grapheme)
                before?.let { it.setLength(it.length - grapheme.length) }
                expect { TextSelection(it.start - grapheme.length) }
            }
        }
    }

    override fun deleteGrapheme(grapheme: String) {
        host.deleteGrapheme(grapheme)
        before?.let { if (it.length >= grapheme.length) it.setLength(it.length - grapheme.length) else dropText() }
        expect { TextSelection(it.start - grapheme.length) }
    }

    override fun replaceBeforeCursor(length: Int, replacement: String) {
        host.replaceBeforeCursor(length, replacement)
        val known = before
        if (known != null && known.length >= length) {
            known.setLength(known.length - length)
            appendBefore(replacement)
        } else {
            before = null
            beforeIsWhole = false
        }
        selected = ""
        expect { TextSelection(it.start - length + replacement.length) }
    }

    override fun moveCursor(offset: Int) {
        host.moveCursor(offset)
        val selection = expected
        val known = before
        val ahead = after
        if (selection == null || !selection.isCollapsed) {
            // The host moves from the selection's end; the mirror doesn't try to follow.
            invalidate()
            return
        }
        val step = when {
            offset < 0 && known != null && (known.length >= -offset || beforeIsWhole) -> maxOf(offset, -known.length)
            offset > 0 && ahead != null && (ahead.length >= offset || afterIsWhole) -> minOf(offset, ahead.length)
            offset == 0 -> 0
            else -> {
                dropText()
                expect { TextSelection((it.start + offset).coerceAtLeast(0)) }
                return
            }
        }
        if (step < 0 && known != null) {
            val moved = known.substring(known.length + step)
            known.setLength(known.length + step)
            after = ahead?.let { moved + it }
        } else if (step > 0 && ahead != null) {
            appendBefore(ahead.substring(0, step))
            after = ahead.substring(step)
        }
        expect { TextSelection(it.start + step) }
    }

    /** The app may do anything on an editor action (send and clear, move focus): re-read after. */
    override fun performEditorAction(action: ImeAction): Boolean {
        val handled = host.performEditorAction(action)
        if (handled) invalidate()
        return handled
    }

    override fun batchEdit(block: () -> Unit) = host.batchEdit(block)

    // endregion

    private fun appendBefore(text: String) {
        val known = before ?: return
        known.append(text)
        if (known.length > MAX_KEPT) {
            known.deleteRange(0, known.length - MAX_KEPT)
            beforeIsWhole = false
        }
    }

    private fun StringBuilder.takeLastString(n: Int): String = substring((length - n).coerceAtLeast(0))

    private companion object {
        /** One fetch covers many keystrokes of context reads; the IPC cost is per call, not per character. */
        const val FETCH_LENGTH = 256
        /** Bounded, so a long session doesn't grow the copy without end. */
        const val MAX_KEPT = 1024
        /** Longer than any emoji sequence or flag. */
        const val GRAPHEME_WINDOW = 32
        /** Reports can lag a burst of typing; older positions are no longer expected. */
        const val MAX_PENDING = 64
    }
}
