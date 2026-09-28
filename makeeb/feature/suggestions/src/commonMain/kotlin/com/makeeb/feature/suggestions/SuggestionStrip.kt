package com.makeeb.feature.suggestions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makeeb.core.model.Suggestion
import com.makeeb.core.model.bestStripSlot
import com.makeeb.core.model.stripSlots
import com.makeeb.ui.theme.KeyboardTheme

/**
 * The strip above the keys: suggestions while typing (punctuation right after a word), otherwise
 * the [toolbar]. The best suggestion always sits in the middle slot, where the thumb rests.
 */
@Composable
fun SuggestionStrip(
    suggestions: List<Suggestion>,
    onSuggestion: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
    toolbar: @Composable RowScope.() -> Unit = {},
) {
    val colors = KeyboardTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (suggestions.isEmpty()) {
            toolbar()
            return@Row
        }
        val slots = suggestions.stripSlots()
        val best = suggestions.bestStripSlot()
        slots.forEachIndexed { index, suggestion ->
            if (index > 0 && suggestion != null && slots[index - 1] != null) {
                Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp).background(colors.divider))
            }
            if (suggestion == null) {
                Box(Modifier.weight(1f).fillMaxHeight())
                return@forEachIndexed
            }
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable { onSuggestion(suggestion) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = suggestion.text,
                    color = colors.onKey,
                    fontSize = 17.sp,
                    fontWeight = if (index == best) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** A square toolbar button for the strip. */
@Composable
fun StripButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxHeight().width(52.dp).clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = KeyboardTheme.colors.hint, modifier = Modifier.size(22.dp))
    }
}
