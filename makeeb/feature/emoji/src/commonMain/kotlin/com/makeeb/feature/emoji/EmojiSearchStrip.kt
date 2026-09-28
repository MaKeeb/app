package com.makeeb.feature.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makeeb.engine.emoji.Emoji
import com.makeeb.ui.theme.KeyboardIcons
import com.makeeb.ui.theme.KeyboardTheme

/**
 * The suggestion strip while searching emoji: a way back to the panel, the query typed on the
 * keys below, and the matches in a scrolling row. The keyboard keeps its size and its keys stay
 * where they are; tapping a match types it and leaves the search open for another.
 */
@Composable
fun EmojiSearchStrip(
    query: String,
    results: List<Emoji>,
    onEmoji: (Emoji) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KeyboardTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.fillMaxHeight().width(44.dp).clickable(onClickLabel = "Close emoji search", onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(KeyboardIcons.Back, contentDescription = "Close emoji search", tint = colors.hint, modifier = Modifier.size(22.dp))
        }
        Row(
            Modifier
                .fillMaxHeight()
                .padding(vertical = 6.dp)
                .widthIn(min = 112.dp, max = 168.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.key)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(KeyboardIcons.Search, contentDescription = null, tint = colors.hint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = query.ifEmpty { "Search emoji" },
                color = if (query.isEmpty()) colors.hint else colors.onKey,
                fontSize = 15.sp,
                maxLines = 1,
                // The end of the query is what the user is typing.
                overflow = TextOverflow.StartEllipsis,
            )
        }
        if (results.isEmpty() && query.isNotBlank()) {
            Text("No emoji", color = colors.hint, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp))
        }
        LazyRow(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            items(results, key = { it.value }) { emoji ->
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(42.dp)
                        .semantics { contentDescription = emoji.name }
                        .clickable { onEmoji(emoji) },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji.value, fontSize = 24.sp) }
            }
        }
    }
}
