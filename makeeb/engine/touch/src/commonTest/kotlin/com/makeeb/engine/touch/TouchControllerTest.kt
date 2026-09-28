package com.makeeb.engine.touch

import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyboardMode
import com.makeeb.engine.layout.BuiltInLayoutProvider
import com.makeeb.engine.layout.Key
import com.makeeb.engine.layout.LayoutGeometry
import com.makeeb.engine.layout.LayoutOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TouchControllerTest {
    private val actions = mutableListOf<KeyAction>()
    private val geometry = LayoutGeometry(
        BuiltInLayoutProvider().layout(KeyboardMode.Letters, LayoutOptions()),
        width = 1000f,
        rowHeight = 100f,
    )

    private fun TestScope.controller() = TouchController(
        scope = backgroundScope,
        listener = object : TouchListener {
            override fun onKeyDown(key: Key) = Unit
            override fun onAction(action: KeyAction) {
                actions += action
            }
        },
        config = TouchConfig(overflowAbove = 50f),
        timeSource = testTimeSource,
    ).also { it.geometry = geometry }

    private fun centre(char: Char) = geometry.keyFor(char)!!.bounds.let { it.centerX to it.centerY }

    @Test
    fun tapCommitsOnRelease() = runTest {
        val touch = controller()
        val (x, y) = centre('q')
        touch.down(1, x, y)
        assertTrue(actions.isEmpty(), "characters commit on release, not on touch-down")
        assertNotNull(touch.state.value.preview)
        touch.up(1, x, y)
        assertEquals(listOf<KeyAction>(KeyAction.Text("q")), actions)
    }

    @Test
    fun previewIsLargerThanTheKeyCentredAndKeptInsideTheArea() = runTest {
        val touch = controller()
        val g = geometry.keyFor('g')!!.bounds
        touch.down(1, g.centerX, g.centerY)
        val bubble = touch.state.value.preview!!.bounds
        assertTrue(bubble.width > g.width * 1.3f && bubble.bottom - bubble.top > (g.bottom - g.top) * 1.2f)
        assertEquals(g.centerX, bubble.centerX, absoluteTolerance = 0.5f)
        assertEquals(g.top, bubble.bottom, absoluteTolerance = 0.5f)
        touch.up(1, g.centerX, g.centerY)

        val q = geometry.keyFor('q')!!.bounds
        touch.down(2, q.centerX, q.centerY)
        val edge = touch.state.value.preview!!.bounds
        assertEquals(0f, edge.left, absoluteTolerance = 0.5f) // clamped at the left edge
        assertTrue(edge.top >= -50f, "at most overflowAbove into the strip")
    }

    @Test
    fun slidingToANeighbourCommitsTheNeighbour() = runTest {
        val touch = controller()
        val (qx, qy) = centre('q')
        val (wx, wy) = centre('w')
        touch.down(1, qx, qy)
        touch.move(1, wx, wy)
        touch.up(1, wx, wy)
        assertEquals(listOf<KeyAction>(KeyAction.Text("w")), actions)
    }

    @Test
    fun overlappingTapsFromTwoFingersBothCommit() = runTest {
        val touch = controller()
        val (hx, hy) = centre('h')
        val (ix, iy) = centre('i')
        touch.down(1, hx, hy)
        touch.down(2, ix, iy)
        touch.up(1, hx, hy)
        touch.up(2, ix, iy)
        assertEquals(listOf<KeyAction>(KeyAction.Text("h"), KeyAction.Text("i")), actions)
    }

    @Test
    fun rolloverTypesKeysInTheOrderTheyWerePressed() = runTest {
        val touch = controller()
        val (hx, hy) = centre('h')
        val (ix, iy) = centre('i')
        touch.down(1, hx, hy)
        touch.down(2, ix, iy) // lands before the first finger lifts
        touch.up(2, ix, iy) // and lifts first
        touch.up(1, hx, hy)
        assertEquals(listOf<KeyAction>(KeyAction.Text("h"), KeyAction.Text("i")), actions)
    }

    @Test
    fun rolloverCoversSpaceButNotAHeldModifier() = runTest {
        val touch = controller()
        val space = geometry.keys.first { it.key.action == KeyAction.Space }.bounds
        val shift = geometry.keys.first { it.key.action == KeyAction.Shift }.bounds
        val (ax, ay) = centre('a')
        touch.down(1, space.centerX, space.centerY)
        touch.down(2, ax, ay)
        assertEquals(listOf<KeyAction>(KeyAction.Space), actions, "space typed as the next key lands")
        touch.up(1, space.centerX, space.centerY)
        touch.up(2, ax, ay)
        actions.clear()

        touch.down(3, shift.centerX, shift.centerY)
        touch.down(4, ax, ay)
        assertTrue(actions.isEmpty(), "a held shift waits for its own release")
        touch.up(4, ax, ay)
        touch.up(3, shift.centerX, shift.centerY)
        assertEquals(listOf(KeyAction.Text("a"), KeyAction.Shift), actions)
    }

    @Test
    fun aCancelledTouchTypesNothing() = runTest {
        val touch = controller()
        val (x, y) = centre('k')
        touch.down(1, x, y)
        touch.cancel(1)
        touch.up(1, x, y) // a stray release after the cancel
        assertTrue(actions.isEmpty())
        assertTrue(touch.state.value.pressed.isEmpty())
    }

    @Test
    fun edgeTapsReachTheNearestKey() = runTest {
        val touch = controller()
        fun tapAt(x: Float, y: Float): KeyAction {
            actions.clear()
            touch.down(1, x, y)
            touch.up(1, x, y)
            return actions.single()
        }
        assertEquals(KeyAction.Text("q"), tapAt(0f, 0f))
        assertEquals(KeyAction.Text("q"), tapAt(1f, -20f)) // just above the keys, inside the strip overlap
        assertEquals(KeyAction.Text("p"), tapAt(999f, 1f))
        assertEquals(KeyAction.Enter, tapAt(999f, 399f))
        assertEquals(KeyAction.Text("a"), tapAt(0f, 150f)) // the gap left of the indented middle row
    }

    @Test
    fun longPressOpensAlternatesAndDragSelects() = runTest {
        val touch = controller()
        val (x, y) = centre('e')
        touch.down(1, x, y)
        advanceTimeBy(400)
        runCurrent()
        val popup = assertNotNull(touch.state.value.popup)
        assertEquals("3", popup.options.first()) // the digit hint comes first
        assertTrue(popup.cells.first().top >= -50f, "popups stay inside the keyboard")

        val target = popup.cells[1]
        touch.move(1, target.centerX, target.centerY)
        touch.up(1, target.centerX, target.centerY)
        assertEquals(listOf<KeyAction>(KeyAction.Text(popup.options[1])), actions)
    }

    @Test
    fun holdingABCOnTheSymbolsPageOpensTheNumberPadAndATapGoesBackToLetters() = runTest {
        val touch = controller()
        touch.geometry = LayoutGeometry(
            BuiltInLayoutProvider().layout(KeyboardMode.Symbols, LayoutOptions()),
            width = 1000f,
            rowHeight = 100f,
        )
        val abc = touch.geometry!!.keys.first { it.key.action == KeyAction.SwitchMode(KeyboardMode.Letters) }.bounds
        touch.down(1, abc.centerX, abc.centerY)
        touch.up(1, abc.centerX, abc.centerY)
        touch.down(2, abc.centerX, abc.centerY)
        advanceTimeBy(400)
        runCurrent()
        touch.up(2, abc.centerX, abc.centerY)
        assertEquals(
            listOf<KeyAction>(KeyAction.SwitchMode(KeyboardMode.Letters), KeyAction.SwitchMode(KeyboardMode.Numeric)),
            actions,
            "the hold opens the pad, and releasing it does nothing more",
        )
    }

    @Test
    fun holdingTheGlobeKeyShowsTheInputMethodPicker() = runTest {
        val touch = controller()
        touch.geometry = LayoutGeometry(
            BuiltInLayoutProvider().layout(KeyboardMode.Letters, LayoutOptions(switchKey = true)),
            width = 1000f,
            rowHeight = 100f,
        )
        val globe = touch.geometry!!.keys.single { it.key.action == KeyAction.NextInputMethod }.bounds
        touch.down(1, globe.centerX, globe.centerY)
        advanceTimeBy(400)
        runCurrent()
        touch.up(1, globe.centerX, globe.centerY)
        assertEquals(listOf<KeyAction>(KeyAction.ShowInputMethodPicker), actions)
    }

    @Test
    fun backspaceFiresImmediatelyThenRepeats() = runTest {
        val touch = controller()
        val backspace = geometry.keys.first { it.key.action == KeyAction.Backspace }.bounds
        touch.down(1, backspace.centerX, backspace.centerY)
        assertEquals(1, actions.size)
        advanceTimeBy(400 + 50 * 3 + 1)
        runCurrent()
        touch.up(1, backspace.centerX, backspace.centerY)
        assertTrue(actions.size >= 4, "got ${actions.size}")
        assertTrue(actions.all { it == KeyAction.Backspace })
    }

    @Test
    fun aQuickFlickUpOpensTheAlternatesAndReleasingTypesTheFirst() = runTest {
        val touch = controller()
        val e = geometry.keyFor('e')!!.bounds
        touch.down(1, e.centerX, e.centerY)
        touch.move(1, e.centerX, e.centerY - 25f)
        assertNotNull(touch.state.value.popup, "the flick opens the popup without waiting")
        touch.up(1, e.centerX, e.centerY - 25f)
        assertEquals(listOf<KeyAction>(KeyAction.Text("3")), actions, "the top row's digit")
    }

    @Test
    fun aSlowMoveUpIsNotAFlick() = runTest {
        val touch = controller()
        val a = geometry.keyFor('a')!!.bounds
        touch.down(1, a.centerX, a.centerY)
        advanceTimeBy(300)
        touch.move(1, a.centerX, a.centerY - 25f)
        assertEquals(null, touch.state.value.popup)
        touch.up(1, a.centerX, a.centerY - 25f)
        assertEquals(listOf<KeyAction>(KeyAction.Text("a")), actions)
    }

    @Test
    fun heldBackspaceAcceleratesThenDeletesWords() = runTest {
        val touch = controller()
        val backspace = geometry.keys.first { it.key.action == KeyAction.Backspace }.bounds
        touch.down(1, backspace.centerX, backspace.centerY)
        advanceTimeBy(1_000)
        runCurrent()
        val firstSecond = actions.size
        assertTrue(firstSecond > 1 + 600 / 60, "faster than a steady 60 ms repeat: got $firstSecond")
        advanceTimeBy(1_000)
        runCurrent()
        touch.up(1, backspace.centerX, backspace.centerY)
        val firstWord = actions.indexOf(KeyAction.DeleteWord)
        assertEquals(1 + 20, firstWord, "words after the initial delete and 20 character repeats")
        assertTrue(actions.drop(firstWord).all { it == KeyAction.DeleteWord })
    }

    @Test
    fun heldBackspaceKeepsToCharactersWhenWordDeletionIsOff() = runTest {
        val touch = controller().also { it.deleteWordsWhenHeld = false }
        val backspace = geometry.keys.first { it.key.action == KeyAction.Backspace }.bounds
        touch.down(1, backspace.centerX, backspace.centerY)
        advanceTimeBy(3_000)
        runCurrent()
        touch.up(1, backspace.centerX, backspace.centerY)
        assertTrue(actions.all { it == KeyAction.Backspace })
    }

    @Test
    fun spaceSlideCanMoveByWord() = runTest {
        val touch = controller().also { it.cursorByWord = true }
        val space = geometry.keys.first { it.key.action == KeyAction.Space }.bounds
        touch.down(1, space.centerX, space.centerY)
        touch.move(1, space.centerX - 30f, space.centerY) // past the slide threshold
        touch.move(1, space.centerX - 30f - 36f * 2, space.centerY)
        touch.up(1, space.centerX - 30f - 36f * 2, space.centerY)
        assertTrue(actions.isNotEmpty() && actions.all { it == KeyAction.MoveCursorByWord(-1) }, "got $actions")
        assertTrue(KeyAction.Space !in actions)
    }

    @Test
    fun spaceSlideMovesTheCursorInsteadOfTypingSpace() = runTest {
        val touch = controller()
        val space = geometry.keys.first { it.key.action == KeyAction.Space }.bounds
        touch.down(1, space.centerX, space.centerY)
        touch.move(1, space.centerX + 30f, space.centerY) // past the slide threshold
        touch.move(1, space.centerX + 30f + 14f * 3, space.centerY)
        touch.up(1, space.centerX + 30f + 14f * 3, space.centerY)
        assertEquals(List<KeyAction>(3) { KeyAction.MoveCursor(1) }, actions)
    }
}
