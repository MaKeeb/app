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

interface TouchListener {
    /** A finger landed on [key]: play click/haptic feedback. */
    fun onKeyDown(key: Key)

    fun onAction(action: KeyAction)
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
) {
    private enum class Mode { Tap, Alternates, Repeating, CursorSlide, Consumed }

    private class Pointer(var placed: PlacedKey, val downX: Float) {
        var mode = Mode.Tap
        var lastX = downX
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

    fun down(id: Long, x: Float, y: Float) {
        val placed = geometry?.keyAt(x, y) ?: return
        pointers[id]?.timer?.cancel()
        val pointer = Pointer(placed, x)
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
        when (pointer.mode) {
            Mode.Tap -> {
                val action = pointer.placed.key.action
                if (action == KeyAction.Space && abs(x - pointer.downX) > config.cursorSlideStart) {
                    pointer.timer?.cancel()
                    pointer.mode = Mode.CursorSlide
                    pointer.lastX = x
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
                while (pointer.slide >= config.cursorStep) {
                    listener.onAction(KeyAction.MoveCursor(1))
                    pointer.slide -= config.cursorStep
                }
                while (pointer.slide <= -config.cursorStep) {
                    listener.onAction(KeyAction.MoveCursor(-1))
                    pointer.slide += config.cursorStep
                }
            }
            Mode.Alternates -> popup = popup?.let { it.copy(selected = it.indexAt(x)) }
            Mode.Repeating, Mode.Consumed -> Unit
        }
        publish()
    }

    fun up(id: Long, x: Float, y: Float) {
        move(id, x, y)
        val pointer = pointers.remove(id) ?: return
        pointer.timer?.cancel()
        when (pointer.mode) {
            Mode.Tap -> listener.onAction(pointer.placed.key.action)
            Mode.Alternates -> {
                val current = popup
                popup = null
                if (current != null) {
                    // Commit the unshifted alternate: the engine applies the shift state itself.
                    listener.onAction(KeyAction.Text(current.key.key.alternates[current.selected]))
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

    private fun startLongPressTimer(id: Long, pointer: Pointer) {
        pointer.timer?.cancel()
        val key = pointer.placed.key
        val hasLongPress = key.action == KeyAction.NextInputMethod || key.alternates.isNotEmpty()
        if (!hasLongPress) return
        pointer.timer = scope.launch {
            delay(config.longPressMillis)
            onLongPress(id)
        }
    }

    private fun onLongPress(id: Long) {
        val pointer = pointers[id] ?: return
        val placed = pointer.placed
        when {
            placed.key.action == KeyAction.NextInputMethod -> {
                pointer.mode = Mode.Consumed
                listener.onAction(KeyAction.ShowInputMethodPicker)
            }
            placed.key.alternates.isNotEmpty() && popup == null -> {
                pointer.mode = Mode.Alternates
                popup = buildPopup(placed)
            }
        }
        publish()
    }

    private fun buildPopup(placed: PlacedKey): AlternatesPopup {
        val bounds = placed.bounds
        val options = placed.key.displayAlternates(shift)
        val cellWidth = bounds.width
        val height = bounds.bottom - bounds.top
        val width = geometry?.width ?: Float.MAX_VALUE
        var left = bounds.left
        if (left + cellWidth * options.size > width) left = (width - cellWidth * options.size).coerceAtLeast(0f)
        val top = (bounds.top - height).coerceAtLeast(-config.overflowAbove)
        val cells = options.indices.map { i ->
            KeyBounds(left + i * cellWidth, top, left + (i + 1) * cellWidth, top + height)
        }
        return AlternatesPopup(placed, options, cells, selected = 0)
    }

    private fun AlternatesPopup.indexAt(x: Float): Int {
        val first = cells.first()
        return floor((x - first.left) / first.width).toInt().coerceIn(0, cells.lastIndex)
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
        /** Gboard- and iOS-like proportions: clearly larger than the key under the finger. */
        const val PREVIEW_WIDTH_SCALE = 1.4f
        const val PREVIEW_HEIGHT_SCALE = 1.25f
    }
}
