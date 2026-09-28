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
