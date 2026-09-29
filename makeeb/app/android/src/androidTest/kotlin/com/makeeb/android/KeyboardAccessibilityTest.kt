package com.makeeb.android

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The keyboard as a screen reader sees it: every key is a node in the input method window with
 * the shared spoken label, and activating a node types exactly as a tap would. The nodes are
 * driven through UiAutomation, the accessibility API TalkBack itself uses, because touches
 * injected with `adb shell input` bypass TalkBack's explore-by-touch and prove nothing.
 *
 * It selects MaKeeb as the keyboard and puts the previous one back afterwards. Run it on one
 * device with `am instrument`, never `connectedAndroidTest`: `.ai/skills/build-and-verify`.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardAccessibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val automation = instrumentation.uiAutomation
    private val packageName = instrumentation.targetContext.packageName
    private lateinit var previousIme: String
    private lateinit var field: UiObject2

    @Before
    fun openTheTryItField() {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        previousIme = device.executeShellCommand("settings get secure default_input_method").trim()
        val ime = "$packageName/com.makeeb.android.MaKeebInputMethodService"
        device.executeShellCommand("ime enable $ime")
        device.executeShellCommand("ime set $ime")

        val context = instrumentation.targetContext
        context.startActivity(
            context.packageManager.getLaunchIntentForPackage(packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        assertTrue("companion app", device.wait(Until.hasObject(By.text("Try it")), LAUNCH_TIMEOUT))
        device.findObject(By.text("Try it")).click()
        assertTrue("Try it screen", device.wait(Until.hasObject(By.text("Try MaKeeb")), TIMEOUT))
        field = device.findObject(By.clazz("android.widget.EditText").hasDescendant(By.text("Text")))
        field.click()
        field.text = ""
        waitUntil("the keyboard's keys") { keys().size > 26 }
    }

    @After
    fun restoreTheKeyboard() {
        if (previousIme.isNotEmpty() && previousIme != "null") device.executeShellCommand("ime set $previousIme")
    }

    @Test
    fun everyKeyIsALabelledButton() {
        val keys = keys()
        val labels = keys.map { it.contentDescription.toString() }
        for (expected in listOf("Delete", "Symbols", "Space", "Emoji")) {
            assertTrue("\"$expected\" among $labels", expected in labels)
        }
        assertTrue("a shift key among $labels", labels.any { it == "Shift" || it == "Shift, on" || it == "Caps lock" })
        val letters = keys.filter { it.contentDescription.toString().removePrefix("Capital ").let { l -> l.length == 1 && l[0].isLetter() } }
        assertEquals("26 letters in $labels", 26, letters.size)
        // Keys are buttons themselves; a strip icon may carry the label inside its clickable
        // button, which TalkBack focuses and reads as one.
        for (key in keys) {
            val self = key.actionList.contains(AccessibilityAction.ACTION_CLICK)
            val activatable = self || generateSequence(key.parent) { it.parent }.any { it.actionList.contains(AccessibilityAction.ACTION_CLICK) }
            val mustBeSelf = key in letters || key.contentDescription.toString() in listOf("Delete", "Symbols", "Space", "Emoji")
            assertTrue("${key.contentDescription} can be activated", if (mustBeSelf) self else activatable)
            val bounds = Rect().also(key::getBoundsInScreen)
            assertTrue("${key.contentDescription} has a size", bounds.width() > 0 && bounds.height() > 0)
        }
    }

    @Test
    fun activatingKeysTypesAndDeletes() {
        press(letter('h'))
        press(letter('i'))
        assertFieldText("hi", ignoreCase = true)
        press("Delete")
        assertFieldText("h", ignoreCase = true)
    }

    @Test
    fun shiftIsAnnouncedAndCapitalisesOneLetter() {
        typeFirstLetter()
        press("Shift")
        waitUntil("letters read as capitals") { key("Shift, on") != null && key("Capital A") != null }
        press("Capital A")
        waitUntil("shift released after one letter") { key("Shift") != null && key("b") != null }
        press("b")
        assertFieldText("xAb", ignoreCase = false, ignoreFirst = true)
    }

    @Test
    fun alternatesAreCustomActions() {
        typeFirstLetter()
        val e = waitFor("the e key") { key("e") }
        val acute = e.actionList.firstOrNull { it.label == "é" }
            ?: throw AssertionError("é among ${e.actionList.map { it.label }}")
        assertTrue(e.performAction(acute.id))
        assertFieldText("xé", ignoreFirst = true)
    }

    @Test
    fun modeKeysSwitchPages() {
        press("Symbols")
        waitUntil("the symbols page") { key("Letters") != null && key("1") != null }
        press("Letters")
        waitUntil("the letters page") { key("Symbols") != null }
    }

    /**
     * Types an x, whatever case the empty field starts in, and waits for shift to be off: after
     * a letter, auto-capitalisation no longer applies, so shift is in a known state.
     */
    private fun typeFirstLetter() {
        press(letter('x'))
        waitUntil("shift off after a letter") { key("Shift") != null }
    }

    private fun press(label: String) {
        val key = waitFor("key \"$label\"") { key(label) }
        assertTrue("click $label", key.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        device.waitForIdle()
    }

    /** The label of the key typing [char], whatever the shift state. */
    private fun letter(char: Char): String =
        waitFor("key $char") { keys().map { it.contentDescription.toString() }.firstOrNull { it == "$char" || it == "Capital ${char.uppercaseChar()}" } }

    private fun key(label: String): AccessibilityNodeInfo? = keys().firstOrNull { it.contentDescription?.toString() == label }

    /** Nodes with a description in the input method window: the keys (the strip shows text). */
    private fun keys(): List<AccessibilityNodeInfo> {
        if (Build.VERSION.SDK_INT >= 34) automation.clearCache()
        val root = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.root ?: return emptyList()
        val found = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo) {
            if (!node.contentDescription.isNullOrEmpty()) found += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }
        walk(root)
        return found
    }

    /** [ignoreFirst]: the first letter's case depends on whether the field auto-capitalised. */
    private fun assertFieldText(expected: String, ignoreCase: Boolean = false, ignoreFirst: Boolean = false) {
        waitFor("field text \"$expected\"") {
            field.text?.takeIf { text ->
                if (ignoreFirst) text.take(1).equals(expected.take(1), ignoreCase = true) && text.drop(1).equals(expected.drop(1), ignoreCase)
                else text.equals(expected, ignoreCase)
            }
        }
    }

    private fun <T : Any> waitFor(what: String, check: () -> T?): T {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT
        while (SystemClock.uptimeMillis() < deadline) {
            check()?.let { return it }
            SystemClock.sleep(100)
        }
        return check() ?: throw AssertionError("timed out waiting for $what")
    }

    private fun waitUntil(what: String, check: () -> Boolean) {
        waitFor(what) { true.takeIf { check() } }
    }

    private companion object {
        const val TIMEOUT = 10_000L

        /** A cold start on a busy emulator can take this long. */
        const val LAUNCH_TIMEOUT = 30_000L
    }
}
