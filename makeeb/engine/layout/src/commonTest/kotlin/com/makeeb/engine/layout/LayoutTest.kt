package com.makeeb.engine.layout

import com.makeeb.core.model.ImeAction
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyIcon
import com.makeeb.core.model.KeyboardMode
import com.makeeb.core.model.ShiftState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutTest {
    private val provider = BuiltInLayoutProvider()

    @Test
    fun everyBuiltInLetterRowIsTenUnitsWide() {
        provider.letterLayouts.forEach { info ->
            val layout = provider.layout(KeyboardMode.Letters, LayoutOptions(letterLayoutId = info.id))
            assertEquals(10f, layout.unitsPerRow, "layout ${info.id}")
        }
    }

    @Test
    fun emailAndUrlFieldsGetTheirKeysAndStayTenUnitsWide() {
        fun bottomTexts(variant: LetterVariant) =
            provider.layout(KeyboardMode.Letters, LayoutOptions(variant = variant)).let { layout ->
                assertEquals(10f, layout.unitsPerRow, "variant $variant")
                layout.rows.last().keys.mapNotNull { (it.action as? KeyAction.Text)?.text }
            }
        assertEquals(listOf(",", "."), bottomTexts(LetterVariant.Text))
        assertEquals(listOf("@", "."), bottomTexts(LetterVariant.Email))
        assertEquals(listOf("/", ".", ".com"), bottomTexts(LetterVariant.Url))
        // The symbols pages keep the field's bottom row; the rows above don't depend on the field.
        val textSymbols = provider.layout(KeyboardMode.Symbols, LayoutOptions())
        val urlSymbols = provider.layout(KeyboardMode.Symbols, LayoutOptions(variant = LetterVariant.Url))
        assertEquals(textSymbols.rows.dropLast(1), urlSymbols.rows.dropLast(1))
        assertEquals(listOf("/", ".", ".com"), urlSymbols.rows.last().keys.mapNotNull { (it.action as? KeyAction.Text)?.text })
    }

    @Test
    fun everyLetterLayoutHasTheWholeAlphabetInsideTheKeyboard() {
        provider.letterLayouts.forEach { info ->
            val layout = provider.layout(KeyboardMode.Letters, LayoutOptions(letterLayoutId = info.id))
            assertEquals(('a'..'z').toSet(), layout.characterKeys.keys.filter { it in 'a'..'z' }.toSet(), info.id)
            val geometry = LayoutGeometry(layout, width = 1000f, rowHeight = 50f, horizontalInset = 20f)
            assertTrue(geometry.keys.all { it.bounds.left >= 19.9f && it.bounds.right <= 980.1f }, "${info.id} fits")
        }
    }

    @Test
    fun alternatesFollowTheLayoutsLanguage() {
        fun alternates(layout: String, char: Char) =
            provider.layout(KeyboardMode.Letters, LayoutOptions(letterLayoutId = layout)).characterKeys.getValue(char).alternates
        assertEquals("à", alternates("qwerty", 'a').first())
        assertEquals("ä", alternates("qwertz", 'a').first())
        assertEquals("ß", alternates("qwertz", 's').first())
        assertEquals(listOf("3", "é"), alternates("azerty", 'e').take(2), "digit hint, then French")
        assertEquals("ç", alternates("azerty", 'c').first())
    }

    @Test
    fun holdingABCOnTheSymbolsPagesOpensTheNumberPad() {
        listOf(KeyboardMode.Symbols, KeyboardMode.SymbolsMore).forEach { mode ->
            val layout = provider.layout(mode, LayoutOptions())
            assertEquals(10f, layout.unitsPerRow)
            val abc = layout.rows.last().keys.first()
            assertEquals(KeyAction.SwitchMode(KeyboardMode.Letters), abc.action, "$mode: a tap goes back to letters")
            assertEquals(KeyAction.SwitchMode(KeyboardMode.Numeric), abc.longPressAction, "$mode: holding opens the pad")
            assertTrue(layout.rows.flatMap { it.keys }.none { it.action == KeyAction.SwitchMode(KeyboardMode.Numeric) }, "no 1234 key")
        }
        val numeric = provider.layout(KeyboardMode.Numeric, LayoutOptions())
        assertTrue(numeric.rows.flatMap { it.keys }.any { it.action == KeyAction.SwitchMode(KeyboardMode.Letters) }, "and back")
    }

    @Test
    fun theGlobeKeyListsInputMethodsWhenHeld() {
        val globe = provider.layout(KeyboardMode.Letters, LayoutOptions(switchKey = true)).rows.last().keys
            .single { it.action == KeyAction.NextInputMethod }
        assertEquals(KeyAction.ShowInputMethodPicker, globe.longPressAction)
    }

    @Test
    fun phonePadShowsKeypadLettersAndASpaceGlyph() {
        val phone = provider.layout(KeyboardMode.Phone, LayoutOptions())
        val keys = phone.rows.flatMap { it.keys }
        assertEquals("ABC", keys.single { it.label == "2" }.caption)
        assertEquals("WXYZ", keys.single { it.label == "9" }.caption)
        assertEquals(listOf("+"), keys.single { it.label == "0" }.alternates)
        val space = keys.single { it.action == KeyAction.Space }
        assertEquals(KeyIcon.Space, space.renderIcon(ShiftState.Off, ImeAction.None))
        val bar = provider.layout(KeyboardMode.Letters, LayoutOptions()).rows.last().keys.single { it.action == KeyAction.Space }
        assertEquals(null, bar.renderIcon(ShiftState.Off, ImeAction.None))
    }

    @Test
    fun switchKeyReplacesEmojiKey() {
        val withGlobe = provider.layout(KeyboardMode.Letters, LayoutOptions(switchKey = true))
        val bottom = withGlobe.rows.last().keys.map { it.action }
        assertTrue(KeyAction.NextInputMethod in bottom)
    }

    @Test
    fun numberRowAddsARowAndDropsHints() {
        val plain = provider.layout(KeyboardMode.Letters, LayoutOptions())
        val withNumbers = provider.layout(KeyboardMode.Letters, LayoutOptions(numberRow = true))
        assertEquals(plain.rows.size + 1, withNumbers.rows.size)
        assertEquals("1", plain.rows[0].keys[0].hint)
        assertEquals(null, withNumbers.rows[1].keys[0].hint)
    }

    @Test
    fun geometryCentresShortRowsAndResolvesMarginsToNearestKey() {
        val layout = provider.layout(KeyboardMode.Letters, LayoutOptions())
        val geometry = LayoutGeometry(layout, width = 1000f, rowHeight = 50f)

        val a = geometry.keyFor('a')!!
        assertEquals(50f, a.bounds.left) // 9-key row centred in 10 units: half a key of margin
        assertEquals("a", geometry.keyAt(10f, 75f)!!.key.label) // left margin of the asdf row
        assertEquals("q", geometry.keyAt(5f, 5f)!!.key.label)
        assertEquals("p", geometry.keyAt(999f, 5f)!!.key.label)
    }

    @Test
    fun numberRowIsShorterThanLetterRows() {
        val layout = provider.layout(KeyboardMode.Letters, LayoutOptions(numberRow = true))
        val geometry = LayoutGeometry(layout, width = 1000f, rowHeight = 100f)

        val one = geometry.keyFor('1')!!.bounds
        val q = geometry.keyFor('q')!!.bounds
        assertEquals(80f, one.bottom - one.top, absoluteTolerance = 0.01f)
        assertEquals(80f, q.top, absoluteTolerance = 0.01f)
        assertEquals(480f, geometry.height, absoluteTolerance = 0.01f)
        assertEquals("q", geometry.keyAt(50f, 85f)!!.key.label)
        assertEquals("5", geometry.keyAt(450f, 75f)!!.key.label)
    }

    @Test
    fun horizontalInsetKeepsKeysOffTheEdgesButStillTakesEdgeTouches() {
        val layout = provider.layout(KeyboardMode.Letters, LayoutOptions())
        val geometry = LayoutGeometry(layout, width = 1000f, rowHeight = 50f, horizontalInset = 20f)

        val q = geometry.keyFor('q')!!.bounds
        assertEquals(20f, q.left)
        assertEquals(980f, geometry.keyFor('p')!!.bounds.right)
        assertEquals("q", geometry.keyAt(2f, 25f)!!.key.label)
    }
}
