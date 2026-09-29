package com.makeeb.feature.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makeeb.core.model.ImeAction
import com.makeeb.core.model.KeyAction
import com.makeeb.core.model.KeyIcon
import com.makeeb.core.model.KeyboardTokens
import com.makeeb.core.model.ShiftState
import com.makeeb.engine.layout.KeyBounds
import com.makeeb.engine.layout.KeyStyle
import com.makeeb.engine.layout.LayoutGeometry
import com.makeeb.engine.layout.PlacedKey
import com.makeeb.engine.layout.renderIcon
import com.makeeb.engine.layout.renderLabel
import com.makeeb.engine.layout.spokenLabel
import com.makeeb.engine.layout.spokenLongPress
import com.makeeb.engine.touch.AlternatesPopup
import com.makeeb.engine.touch.KeyPreview
import com.makeeb.engine.touch.TouchController
import com.makeeb.ui.theme.KeyboardIcons
import com.makeeb.ui.theme.KeyboardTheme
import kotlin.math.roundToInt

/**
 * Draws the key area. A thin renderer: key positions come from the shared [LayoutGeometry], and
 * all touch interpretation happens in the shared [TouchController]; this only forwards pointers
 * and draws [TouchController.state].
 *
 * Each key is also an accessibility node, so TalkBack's explore-by-touch can find it: it reads the
 * shared spoken label, and activating it (double tap, or lifting the finger with TalkBack's
 * lift-to-type) types through [TouchController.perform] like a tap. Alternates and long-press
 * actions are custom actions.
 */
@Composable
fun KeyboardKeys(
    geometry: LayoutGeometry?,
    touch: TouchController,
    shift: ShiftState,
    imeAction: ImeAction,
    onSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
    spaceLabel: String = "",
) {
    val touchState by touch.state.collectAsState()
    DisposableEffect(touch) { onDispose { touch.cancelAll() } }

    Box(
        modifier
            .onSizeChanged(onSizeChanged)
            .pointerInput(touch) { forwardPointers(touch) },
    ) {
        geometry?.keys?.forEach { placed ->
            KeyCell(
                placed = placed,
                icon = placed.key.renderIcon(shift, imeAction),
                label = placed.key.renderLabel(shift, imeAction, spaceLabel),
                pressed = placed in touchState.pressed,
                active = placed.key.action == KeyAction.Shift && shift != ShiftState.Off,
                modifier = Modifier.keySemantics(placed, touch, shift, imeAction),
            )
        }
        touchState.preview?.let { PreviewBubble(it) }
        touchState.popup?.let { AlternatesBubble(it) }
    }
}

private suspend fun PointerInputScope.forwardPointers(touch: TouchController) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { change ->
                val id = change.id.value
                val x = change.position.x
                val y = change.position.y
                when {
                    change.changedToDownIgnoreConsumed() -> touch.down(id, x, y)
                    // A release that arrives already consumed is Compose's synthetic cancel (the
                    // system took the gesture, e.g. a navigation swipe): type nothing.
                    change.changedToUpIgnoreConsumed() -> if (change.isConsumed) touch.cancel(id) else touch.up(id, x, y)
                    change.pressed && change.position != change.previousPosition -> touch.move(id, x, y)
                }
                change.consume()
            }
        }
    }
}

private fun Modifier.keySemantics(placed: PlacedKey, touch: TouchController, shift: ShiftState, imeAction: ImeAction): Modifier {
    val key = placed.key
    val spoken = key.spokenLabel(shift, imeAction)
    val longPress = key.spokenLongPress(shift, imeAction)
    val alternates = key.displayAlternates(shift)
    return clearAndSetSemantics {
        contentDescription = spoken
        role = Role.Button
        onClick(label = spoken) { touch.perform(placed); true }
        if (longPress != null) onLongClick(label = longPress) { touch.performLongPress(placed); true }
        if (longPress == null && alternates.isNotEmpty()) {
            customActions = alternates.mapIndexed { index, alternate ->
                CustomAccessibilityAction(alternate) { touch.performAlternate(placed, index); true }
            }
        }
    }
}

@Composable
private fun KeyCell(placed: PlacedKey, icon: KeyIcon?, label: String, pressed: Boolean, active: Boolean, modifier: Modifier) {
    val colors = KeyboardTheme.colors
    val dims = KeyboardTheme.dimensions
    val key = placed.key
    val background = when {
        pressed -> colors.keyPressed
        key.style == KeyStyle.Enter -> colors.accentKey
        // An engaged shift reads like a lit key: character-key colour, filled glyph.
        key.style == KeyStyle.Modifier && !active -> colors.modifierKey
        else -> colors.key
    }
    val foreground = if (key.style == KeyStyle.Enter && !pressed) colors.onAccentKey else colors.onKey
    val caption = key.caption
    val fontSize = when {
        key.style == KeyStyle.Character && label.length <= 2 -> dims.keyTextSize
        // Four characters (".com", "?123") only fit their key a size down.
        label.length >= 4 -> dims.modifierTextSize * 0.85f
        else -> dims.modifierTextSize
    }

    Positioned(placed.bounds, modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = dims.keyGap / 2, vertical = dims.rowGap / 2)
                .clip(RoundedCornerShape(dims.keyCornerRadius))
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(icon.vector(), contentDescription = null, tint = foreground, modifier = Modifier.size(dims.iconSize))
            } else if (caption != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = foreground, fontSize = fontSize, lineHeight = fontSize)
                    Text(caption, color = colors.hint, fontSize = dims.hintTextSize, lineHeight = dims.hintTextSize)
                }
            } else {
                Text(label, color = foreground, fontSize = fontSize, maxLines = 1, softWrap = false)
            }
            key.hint?.let { hint ->
                Text(
                    hint,
                    color = colors.hint,
                    fontSize = dims.hintTextSize,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PreviewBubble(preview: KeyPreview) {
    val colors = KeyboardTheme.colors
    val dims = KeyboardTheme.dimensions
    Positioned(preview.bounds) {
        Box(
            Modifier
                .fillMaxSize()
                .shadow(8.dp, RoundedCornerShape(dims.keyCornerRadius + 2.dp))
                .background(colors.popup, RoundedCornerShape(dims.keyCornerRadius + 2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(preview.label, color = colors.onPopup, fontSize = KeyboardTokens.PREVIEW_TEXT_SIZE.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AlternatesBubble(popup: AlternatesPopup) {
    val colors = KeyboardTheme.colors
    val dims = KeyboardTheme.dimensions
    val first = popup.cells.first()
    val last = popup.cells.last()
    Positioned(KeyBounds(first.left, first.top, last.right, last.bottom)) {
        Row(
            Modifier
                .fillMaxSize()
                .shadow(8.dp, RoundedCornerShape(dims.keyCornerRadius))
                .background(colors.popup, RoundedCornerShape(dims.keyCornerRadius)),
        ) {
            popup.options.forEachIndexed { index, option ->
                val selected = index == popup.selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(3.dp)
                        .clip(RoundedCornerShape(dims.keyCornerRadius - 2.dp))
                        .background(if (selected) colors.popupSelected else colors.popup),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(option, color = if (selected) colors.onPopupSelected else colors.onPopup, fontSize = dims.keyTextSize)
                }
            }
        }
    }
}

/** Places [content] at [bounds], which are in pixels relative to the key area. */
@Composable
private fun Positioned(bounds: KeyBounds, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val width: Dp = with(density) { bounds.width.toDp() }
    val height: Dp = with(density) { (bounds.bottom - bounds.top).toDp() }
    Box(
        Modifier
            .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .size(width, height)
            .then(modifier),
    ) { content() }
}

private fun KeyIcon.vector(): ImageVector = when (this) {
    KeyIcon.Shift -> KeyboardIcons.Shift
    KeyIcon.ShiftActive -> KeyboardIcons.ShiftActive
    KeyIcon.CapsLock -> KeyboardIcons.CapsLock
    KeyIcon.Backspace -> KeyboardIcons.Backspace
    KeyIcon.Return -> KeyboardIcons.Return
    KeyIcon.Search -> KeyboardIcons.Search
    KeyIcon.Send -> KeyboardIcons.Send
    KeyIcon.Go -> KeyboardIcons.Go
    KeyIcon.Next -> KeyboardIcons.Next
    KeyIcon.Previous -> KeyboardIcons.Previous
    KeyIcon.Done -> KeyboardIcons.Done
    KeyIcon.Globe -> KeyboardIcons.Globe
    KeyIcon.Emoji -> KeyboardIcons.Emoji
    KeyIcon.Space -> KeyboardIcons.Space
}
