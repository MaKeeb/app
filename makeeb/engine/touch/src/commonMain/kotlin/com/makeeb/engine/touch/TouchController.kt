package com.makeeb.engine.touch

import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.ShiftState
import com.makeeb.engine.layout.Key
import com.makeeb.engine.layout.KeyBounds
import com.makeeb.engine.layout.LayoutGeometry
import com.makeeb.engine.layout.PlacedKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

interface TouchListener {
    /** A finger landed on [key]: play click/haptic feedback. */
    fun onKeyDown(key: Key)

    fun onAction(action: KeyAction)

    /**
     * A tap typed [action] at ([x], [y]), in geometry coordinates. Where the finger landed within
     * the key is what lets autocorrect tell a slip from a word it doesn't know.
     */
    fun onTap(action: KeyAction, x: Float, y: Float) = onAction(action)

    /** A slide moved a selection one step: the next alternate, a cursor step, a held delete's repeat. */
    fun onSelectionTick() {}
}

/**
 * Turns raw multi-touch pointer events into key actions, shared by every renderer. Platforms
 * forward pointer down/move/up with a stable id per finger and draw [state]; everything else
 * (hit-testing, sliding between keys, long-press alternates, backspace repeat, space-bar cursor
 * slide, the key preview) happens here.
 *
 * Main thread only. Timers run in [scope].
 */
class TouchController(
    private val scope: CoroutineScope,
    private val listener: TouchListener,
    var config: TouchConfig = TouchConfig(),
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private enum class Mode { Tap, Alternates, Repeating, CursorSlide, Consumed }

    private class Pointer(var placed: PlacedKey, val downX: Float, val downY: Float, val downAt: TimeMark) {
        var mode = Mode.Tap
        var lastX = downX
        /** The latest position, for where a tap is typed. */
        var x = downX
        var y = downY
        var slide = 0f
        var timer: Job? = null
    }

    private val pointers = LinkedHashMap<Long, Pointer>()
    private var popup: AlternatesPopup? = null
    private val mutableState = MutableStateFlow(TouchState())
    val state: StateFlow<TouchState> = mutableState.asStateFlow()

    /** Set whenever the layout or keyboard size changes. Cancels touches in flight. */
    var geometry: LayoutGeometry? = null
        set(value) {
            if (field != value) {
                cancelAll()
                field = value
            }
        }

    var shift: ShiftState = ShiftState.Off
    var previewEnabled: Boolean = true

    /** Holding delete long switches from characters to whole words. */
    var deleteWordsWhenHeld: Boolean = true

    /** Space-bar sliding moves the caret by word rather than by character. */
    var cursorByWord: Boolean = false

    fun down(id: Long, x: Float, y: Float) {
        val placed = geometry?.keyAt(x, y) ?: return
        rollOver(exceptId = id)
        pointers[id]?.timer?.cancel()
        val pointer = Pointer(placed, x, y, timeSource.markNow())
        pointers[id] = pointer
        listener.onKeyDown(placed.key)

        when (placed.key.action) {
            KeyAction.Backspace -> {
                pointer.mode = Mode.Repeating
                listener.onAction(KeyAction.Backspace)
                pointer.timer = scope.launch {
                    delay(config.repeatStartMillis)
                    var repeats = 0
                    while (isActive) {
                        val words = deleteWordsWhenHeld && repeats >= config.wordDeleteAfterRepeats
                        listener.onAction(if (words) KeyAction.DeleteWord else KeyAction.Backspace)
                        listener.onSelectionTick()
                        repeats++
                        delay(
                            when {
                                words -> config.wordRepeatIntervalMillis
                                repeats >= config.accelerateAfterRepeats -> config.fastRepeatIntervalMillis
                                else -> config.repeatIntervalMillis
                            },
                        )
                    }
                }
            }
            else -> startLongPressTimer(id, pointer)
        }
        publish()
    }

    fun move(id: Long, x: Float, y: Float) {
        val pointer = pointers[id] ?: return
        pointer.x = x
        pointer.y = y
        when (pointer.mode) {
            Mode.Tap -> {
                val action = pointer.placed.key.action
                if (action == KeyAction.Space && abs(x - pointer.downX) > config.cursorSlideStart) {
                    pointer.timer?.cancel()
                    pointer.mode = Mode.CursorSlide
                    pointer.lastX = x
                } else if (action is KeyAction.Text && isFlickUp(pointer, x, y)) {
                    // A quick upward flick opens the alternates at once, like a long press.
                    pointer.timer?.cancel()
                    onLongPress(id)
                } else if (action is KeyAction.Text) {
                    // Sliding onto a neighbouring character key retargets the touch.
                    val under = geometry?.keyAt(x, y)
                    if (under != null && under != pointer.placed && under.key.action is KeyAction.Text) {
                        pointer.placed = under
                        startLongPressTimer(id, pointer)
                    }
                }
            }
            Mode.CursorSlide -> {
                pointer.slide += x - pointer.lastX
                pointer.lastX = x
                val step = if (cursorByWord) config.cursorWordStep else config.cursorStep
                while (pointer.slide >= step) {
                    listener.onAction(if (cursorByWord) KeyAction.MoveCursorByWord(1) else KeyAction.MoveCursor(1))
                    listener.onSelectionTick()
                    pointer.slide -= step
                }
                while (pointer.slide <= -step) {
                    listener.onAction(if (cursorByWord) KeyAction.MoveCursorByWord(-1) else KeyAction.MoveCursor(-1))
                    listener.onSelectionTick()
                    pointer.slide += step
                }
            }
            Mode.Alternates -> popup = popup?.let { current ->
                current.copy(selected = current.indexAt(x, y)).also { if (it.selected != current.selected) listener.onSelectionTick() }
            }
            Mode.Repeating, Mode.Consumed -> Unit
        }
        publish()
    }

    fun up(id: Long, x: Float, y: Float) {
        move(id, x, y)
        val pointer = pointers.remove(id) ?: return
        pointer.timer?.cancel()
        when (pointer.mode) {
            Mode.Tap -> listener.onTap(pointer.placed.key.action, pointer.x, pointer.y)
            Mode.Alternates -> {
                val current = popup
                popup = null
                if (current != null) {
                    // Commit the unshifted alternate (the engine applies the shift state itself),
                    // or the key's action for it (the space bar's languages).
                    listener.onAction(current.key.key.alternateAction(current.selected))
                }
            }
            Mode.Repeating, Mode.CursorSlide, Mode.Consumed -> Unit
        }
        publish()
    }

    fun cancel(id: Long) {
        val pointer = pointers.remove(id) ?: return
        pointer.timer?.cancel()
        if (pointer.mode == Mode.Alternates) popup = null
        publish()
    }

    fun cancelAll() {
        pointers.values.forEach { it.timer?.cancel() }
        pointers.clear()
        popup = null
        publish()
    }

    /**
     * Types [placed] as one complete tap on its centre: a screen reader activating the key's
     * accessibility node. Same feedback and action as a finger, so the engine can't tell them apart.
     */
    fun perform(placed: PlacedKey) {
        listener.onKeyDown(placed.key)
        listener.onTap(placed.key.action, placed.bounds.centerX, placed.bounds.centerY)
    }

    /** Types the [index]th long-press alternate of [placed], as releasing on it in the popup would. */
    fun performAlternate(placed: PlacedKey, index: Int) {
        if (index !in placed.key.alternates.indices) return
        listener.onKeyDown(placed.key)
        listener.onAction(placed.key.alternateAction(index))
    }

    /** Runs [placed]'s long-press action (the globe key's keyboard list, the number pad). */
    fun performLongPress(placed: PlacedKey) {
        val action = placed.key.longPressAction ?: return
        listener.onKeyDown(placed.key)
        listener.onAction(action)
    }

    /**
     * Fast typists land the next key before lifting the last. Keys still held as plain taps are
     * typed now, in the order they were pressed, instead of in the order the fingers lift ("hi",
     * not "ih"). Only characters and space: a held modifier (shift, a mode key) keeps waiting for
     * its own release.
     */
    private fun rollOver(exceptId: Long) {
        for ((otherId, other) in pointers) {
            val action = other.placed.key.action
            if (otherId == exceptId || other.mode != Mode.Tap) continue
            if (action !is KeyAction.Text && action != KeyAction.Space) continue
            other.timer?.cancel()
            other.mode = Mode.Consumed
            listener.onTap(action, other.x, other.y)
        }
    }

    /** Fast and mostly vertical: a slower slide up is a correction to the key above. */
    private fun isFlickUp(pointer: Pointer, x: Float, y: Float): Boolean =
        pointer.placed.key.alternates.isNotEmpty() &&
            pointer.downY - y >= config.swipeUpDistance &&
            abs(x - pointer.downX) < config.swipeUpDistance &&
            pointer.downAt.elapsedNow() <= config.swipeUpWindowMillis.milliseconds

    private fun startLongPressTimer(id: Long, pointer: Pointer) {
        pointer.timer?.cancel()
        val key = pointer.placed.key
        if (key.longPressAction == null && key.alternates.isEmpty()) return
        pointer.timer = scope.launch {
            delay(config.longPressMillis)
            onLongPress(id)
        }
    }

    private fun onLongPress(id: Long) {
        val pointer = pointers[id] ?: return
        val placed = pointer.placed
        val longPressAction = placed.key.longPressAction
        when {
            longPressAction != null -> {
                pointer.mode = Mode.Consumed
                listener.onAction(longPressAction)
            }
            placed.key.alternates.isNotEmpty() && popup == null -> {
                pointer.mode = Mode.Alternates
                popup = buildPopup(placed)
            }
        }
        publish()
    }

    /**
     * Key-sized cells from the key's left edge, kept inside the keyboard. The first option sits
     * under the finger and the rest fan out right, then left, then right again (AOSP's order), so
     * a key near the right edge, whose popup shifts left, still offers its likeliest option where
     * the finger is. Options that don't fit across wrap onto evenly filled rows stacked upwards,
     * the first row nearest the finger. The
     * stack may reach [TouchConfig.overflowAbove] into the strip; where even that is too little
     * (the top letter row's longest lists), the cells get shorter, so the first row still ends
     * where the finger is.
     */
    private fun buildPopup(placed: PlacedKey): AlternatesPopup {
        val bounds = placed.bounds
        val options = placed.key.displayAlternates(shift)
        val height = bounds.bottom - bounds.top
        val areaWidth = geometry?.width ?: Float.MAX_VALUE
        // A wide key's options (the space bar's languages) get cells a few keys wide, not its own width.
        val unitWidth = geometry?.let { g -> (g.width - 2 * g.horizontalInset) / g.layout.unitsPerRow } ?: bounds.width
        val cellWidth = minOf(bounds.width, unitWidth * MAX_CELL_UNITS)
        // Tolerance: without side insets (iOS) the width is exactly ten keys, which division can
        // put a hair under 10.
        val fit = floor(areaWidth / cellWidth + FIT_TOLERANCE).toInt().coerceAtLeast(1)
        val rows = (options.size + fit - 1) / fit
        val columns = (options.size + rows - 1) / rows
        val left = bounds.left.coerceAtMost(areaWidth - cellWidth * columns).coerceAtLeast(0f)
        val cellHeight = minOf(height, (bounds.centerY + config.overflowAbove) / rows)
        val top = (bounds.top - cellHeight * rows).coerceAtLeast(-config.overflowAbove)
        val firstRowTop = top + cellHeight * (rows - 1)
        val order = fanOut(floor((bounds.centerX - left) / cellWidth).toInt().coerceIn(0, columns - 1), columns)
        val cells = options.indices.map { i ->
            val cellLeft = left + order[i % columns] * cellWidth
            val cellTop = firstRowTop - (i / columns) * cellHeight
            KeyBounds(cellLeft, cellTop, cellLeft + cellWidth, cellTop + cellHeight)
        }
        val frame = KeyBounds(left, top, left + cellWidth * columns, top + cellHeight * rows)
        return AlternatesPopup(placed, options, cells, selected = 0, bounds = frame)
    }

    /** Columns from [start] outwards: start, right, left, two right, two left… */
    private fun fanOut(start: Int, columns: Int): List<Int> = buildList {
        add(start)
        var step = 1
        while (size < columns) {
            if (start + step < columns) add(start + step)
            if (start - step >= 0 && size < columns) add(start - step)
            step++
        }
    }

    /** The cell under ([x], [y]); outside the popup, the nearest one. */
    private fun AlternatesPopup.indexAt(x: Float, y: Float): Int {
        val first = cells.first()
        val firstRow = cells.takeWhile { it.top == first.top }
        // Where in the fan-out order the column under the finger comes.
        val position = firstRow.indices.minBy { abs(firstRow[it].centerX - x) }
        val row = floor((first.bottom - y) / (first.bottom - first.top)).toInt().coerceIn(0, cells.lastIndex / firstRow.size)
        return (row * firstRow.size + position).coerceAtMost(cells.lastIndex)
    }

    private fun publish() {
        val latest = pointers.values.lastOrNull()
        val preview = latest
            ?.takeIf { previewEnabled && it.mode == Mode.Tap && it.placed.key.action is KeyAction.Text && popup == null }
            ?.let { previewFor(it.placed) }
        mutableState.value = TouchState(
            pressed = pointers.values.mapTo(LinkedHashSet()) { it.placed },
            preview = preview,
            popup = popup,
            cursorSliding = pointers.values.any { it.mode == Mode.CursorSlide },
        )
    }

    /**
     * A bubble wider and taller than the key, centred over it so it shows around the finger, and
     * kept inside the key area horizontally (edge keys) and at most [TouchConfig.overflowAbove]
     * into the strip (top row).
     */
    private fun previewFor(placed: PlacedKey): KeyPreview {
        val b = placed.bounds
        val width = b.width * PREVIEW_WIDTH_SCALE
        val height = (b.bottom - b.top) * PREVIEW_HEIGHT_SCALE
        val areaWidth = geometry?.width ?: (b.centerX + width)
        val left = (b.centerX - width / 2).coerceIn(0f, max(0f, areaWidth - width))
        val top = (b.top - height).coerceAtLeast(-config.overflowAbove)
        return KeyPreview(placed, placed.key.displayLabel(shift), KeyBounds(left, top, left + width, top + height))
    }

    private companion object {
        const val FIT_TOLERANCE = 0.01f

        /** Widest popup cell, in keys: room for a language's name. */
        const val MAX_CELL_UNITS = 3f

        /** Gboard- and iOS-like proportions: clearly larger than the key under the finger. */
        const val PREVIEW_WIDTH_SCALE = 1.4f
        const val PREVIEW_HEIGHT_SCALE = 1.25f
    }
}
