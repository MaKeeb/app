package com.makeeb.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Keyboard glyphs from Material Symbols (Rounded, weight 400), Apache License 2.0,
 * https://github.com/google/material-design-icons. Kept as path data so no icon library ships
 * in the keyboard. Tint them through `Icon`; iOS draws the matching SF Symbols instead.
 */
object KeyboardIcons {
    // Shift, caps lock and backspace are drawn for the keyboard rather than taken from Material
    // Symbols, whose shift arrow is narrow and whose backspace is small at key size. Proportions
    // follow the platform keyboards: a broad arrow head, a wide delete tag, a 2dp rounded stroke.
    val Shift: ImageVector by lazy { keyGlyph("Shift", outline(SHIFT_ARROW)) }
    val ShiftActive: ImageVector by lazy { keyGlyph("ShiftActive", solid(SHIFT_ARROW)) }
    val CapsLock: ImageVector by lazy { keyGlyph("CapsLock", solid(CAPS_ARROW), outline(CAPS_BAR)) }
    val Backspace: ImageVector by lazy { keyGlyph("Backspace", outline(BACKSPACE_TAG), outline(BACKSPACE_CROSS)) }

    val Return: ImageVector by lazy { symbol("Return", "m272-440 116 116q11 11 11 28t-11 28q-11 11-28 11t-28-11L148-452q-6-6-8.5-13t-2.5-15q0-8 2.5-15t8.5-13l184-184q11-11 28-11t28 11q11 11 11 28t-11 28L272-520h488v-120q0-17 11.5-28.5T800-680q17 0 28.5 11.5T840-640v120q0 33-23.5 56.5T760-440H272Z") }

    val Search: ImageVector by lazy { symbol("Search", "M380-320q-109 0-184.5-75.5T120-580q0-109 75.5-184.5T380-840q109 0 184.5 75.5T640-580q0 44-14 83t-38 69l224 224q11 11 11 28t-11 28q-11 11-28 11t-28-11L532-372q-30 24-69 38t-83 14Zm0-80q75 0 127.5-52.5T560-580q0-75-52.5-127.5T380-760q-75 0-127.5 52.5T200-580q0 75 52.5 127.5T380-400Z") }

    val Send: ImageVector by lazy { symbol("Send", "M792-443 176-183q-20 8-38-3.5T120-220v-520q0-22 18-33.5t38-3.5l616 260q25 11 25 37t-25 37ZM200-280l474-200-474-200v140l240 60-240 60v140Zm0 0v-400 400Z") }

    val Go: ImageVector by lazy { symbol("Go", "M647-440H200q-17 0-28.5-11.5T160-480q0-17 11.5-28.5T200-520h447L451-716q-12-12-11.5-28t12.5-28q12-11 28-11.5t28 11.5l264 264q6 6 8.5 13t2.5 15q0 8-2.5 15t-8.5 13L508-188q-11 11-27.5 11T452-188q-12-12-12-28.5t12-28.5l195-195Z") }

    val Next: ImageVector by lazy { symbol("Next", "M840-240q-17 0-28.5-11.5T800-280v-400q0-17 11.5-28.5T840-720q17 0 28.5 11.5T880-680v400q0 17-11.5 28.5T840-240ZM567-440H120q-17 0-28.5-11.5T80-480q0-17 11.5-28.5T120-520h447L452-636q-11-11-11.5-27.5T452-692q11-11 28-11t28 11l184 184q6 6 8.5 13t2.5 15q0 8-2.5 15t-8.5 13L508-268q-11 11-27.5 11T452-268q-12-12-12-28.5t12-28.5l115-115Z") }

    val Previous: ImageVector by lazy { symbol("Previous", "M120-240q-17 0-28.5-11.5T80-280v-400q0-17 11.5-28.5T120-720q17 0 28.5 11.5T160-680v400q0 17-11.5 28.5T120-240Zm273-200 115 115q12 12 12 28.5T508-268q-12 11-28.5 11T452-268L268-452q-6-6-8.5-13t-2.5-15q0-8 2.5-15t8.5-13l184-184q11-11 28-11t28 11q12 12 11.5 28.5T508-636L393-520h447q17 0 28.5 11.5T880-480q0 17-11.5 28.5T840-440H393Z") }

    val Done: ImageVector by lazy { symbol("Done", "m382-354 339-339q12-12 28-12t28 12q12 12 12 28.5T777-636L410-268q-12 12-28 12t-28-12L182-440q-12-12-11.5-28.5T183-497q12-12 28.5-12t28.5 12l142 143Z") }

    val Globe: ImageVector by lazy { symbol("Globe", "M480-80q-82 0-155-31.5t-127.5-86Q143-252 111.5-325T80-480q0-83 31.5-155.5t86-127Q252-817 325-848.5T480-880q83 0 155.5 31.5t127 86q54.5 54.5 86 127T880-480q0 82-31.5 155t-86 127.5q-54.5 54.5-127 86T480-80Zm0-82q26-36 45-75t31-83H404q12 44 31 83t45 75Zm-104-16q-18-33-31.5-68.5T322-320H204q29 50 72.5 87t99.5 55Zm208 0q56-18 99.5-55t72.5-87H638q-9 38-22.5 73.5T584-178ZM170-400h136q-3-20-4.5-39.5T300-480q0-21 1.5-40.5T306-560H170q-5 20-7.5 39.5T160-480q0 21 2.5 40.5T170-400Zm216 0h188q3-20 4.5-39.5T580-480q0-21-1.5-40.5T574-560H386q-3 20-4.5 39.5T380-480q0 21 1.5 40.5T386-400Zm268 0h136q5-20 7.5-39.5T800-480q0-21-2.5-40.5T790-560H654q3 20 4.5 39.5T660-480q0 21-1.5 40.5T654-400Zm-16-240h118q-29-50-72.5-87T584-782q18 33 31.5 68.5T638-640Zm-234 0h152q-12-44-31-83t-45-75q-26 36-45 75t-31 83Zm-200 0h118q9-38 22.5-73.5T376-782q-56 18-99.5 55T204-640Z") }

    val Emoji: ImageVector by lazy { symbol("Emoji", "M620-520q25 0 42.5-17.5T680-580q0-25-17.5-42.5T620-640q-25 0-42.5 17.5T560-580q0 25 17.5 42.5T620-520Zm-280 0q25 0 42.5-17.5T400-580q0-25-17.5-42.5T340-640q-25 0-42.5 17.5T280-580q0 25 17.5 42.5T340-520ZM480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-400Zm0 320q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-100q58 0 107-28t79-76q6-12-1-24t-21-12H316q-14 0-21 12t-1 24q30 48 79.5 76T480-260Z") }

    /** A space-bar bracket, for compact space keys (phone pad). */
    val Space: ImageVector by lazy { symbol("Space", "M200-600h80v160h400v-160h80v240H200v-240Z") }

    val Clipboard: ImageVector by lazy { symbol("Clipboard", "M200-120q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h167q11-35 43-57.5t70-22.5q40 0 71.5 22.5T594-840h166q33 0 56.5 23.5T840-760v560q0 33-23.5 56.5T760-120H200Zm0-80h560v-560h-80v80q0 17-11.5 28.5T640-640H320q-17 0-28.5-11.5T280-680v-80h-80v560Zm280-560q17 0 28.5-11.5T520-800q0-17-11.5-28.5T480-840q-17 0-28.5 11.5T440-800q0 17 11.5 28.5T480-760Z") }

    val Settings: ImageVector by lazy { symbol("Settings", "M433-80q-27 0-46.5-18T363-142l-9-66q-13-5-24.5-12T307-235l-62 26q-25 11-50 2t-39-32l-47-82q-14-23-8-49t27-43l53-40q-1-7-1-13.5v-27q0-6.5 1-13.5l-53-40q-21-17-27-43t8-49l47-82q14-23 39-32t50 2l62 26q11-8 23-15t24-12l9-66q4-26 23.5-44t46.5-18h94q27 0 46.5 18t23.5 44l9 66q13 5 24.5 12t22.5 15l62-26q25-11 50-2t39 32l47 82q14 23 8 49t-27 43l-53 40q1 7 1 13.5v27q0 6.5-2 13.5l53 40q21 17 27 43t-8 49l-48 82q-14 23-39 32t-50-2l-60-26q-11 8-23 15t-24 12l-9 66q-4 26-23.5 44T527-80h-94Zm7-80h79l14-106q31-8 57.5-23.5T639-327l99 41 39-68-86-65q5-14 7-29.5t2-31.5q0-16-2-31.5t-7-29.5l86-65-39-68-99 42q-22-23-48.5-38.5T533-694l-13-106h-79l-14 106q-31 8-57.5 23.5T321-633l-99-41-39 68 86 64q-5 15-7 30t-2 32q0 16 2 31t7 30l-86 65 39 68 99-42q22 23 48.5 38.5T427-266l13 106Zm42-180q58 0 99-41t41-99q0-58-41-99t-99-41q-59 0-99.5 41T342-480q0 58 40.5 99t99.5 41Zm-2-140Z") }

    /** An eye with a slash: incognito (SF Symbols' eye.slash on iOS). */
    val Incognito: ImageVector by lazy { keyGlyph("Incognito", outline(EYE), outline(PUPIL), outline(SLASH)) }

    private const val SHIFT_ARROW = "M12,2.8 L21.2,12.2 L16.4,12.2 L16.4,20.8 L7.6,20.8 L7.6,12.2 L2.8,12.2 Z"
    private const val CAPS_ARROW = "M12,2.6 L21.2,11.4 L16.4,11.4 L16.4,16.4 L7.6,16.4 L7.6,11.4 L2.8,11.4 Z"
    private const val CAPS_BAR = "M7.6,20.4 L16.4,20.4"
    private const val BACKSPACE_TAG = "M8.6,5.2 L20,5.2 Q21.8,5.2 21.8,7 L21.8,17 Q21.8,18.8 20,18.8 L8.6,18.8 L2.2,12 Z"
    private const val BACKSPACE_CROSS = "M11.8,9.2 L16.8,14.8 M16.8,9.2 L11.8,14.8"

    private const val EYE = "M2.5,12 Q7,5.5 12,5.5 Q17,5.5 21.5,12 Q17,18.5 12,18.5 Q7,18.5 2.5,12 Z"
    private const val PUPIL = "M12,9.2 A2.8,2.8 0 1,0 12,14.8 A2.8,2.8 0 1,0 12,9.2 Z"
    private const val SLASH = "M4,3.5 L20,20.5"

    private class GlyphPath(val data: String, val filled: Boolean)

    private fun outline(data: String) = GlyphPath(data, filled = false)
    private fun solid(data: String) = GlyphPath(data, filled = true)

    /** 24-unit viewport, 1.75-unit stroke (~2dp at the 28dp key icon size), round joins and caps. */
    private fun keyGlyph(name: String, vararg paths: GlyphPath): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        paths.forEach { path ->
            builder.addPath(
                pathData = addPathNodes(path.data),
                fill = if (path.filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    /** Material Symbols use a 960-unit viewport with y running from -960 to 0. */
    private fun symbol(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        )
            .addGroup(translationY = 960f)
            .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
            .clearGroup()
            .build()
}
