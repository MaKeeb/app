package com.makeeb.feature.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makeeb.engine.emoji.Emoji
import com.makeeb.engine.emoji.EmojiCatalog
import com.makeeb.engine.emoji.EmojiCategory
import com.makeeb.ui.theme.KeyboardIcons
import com.makeeb.ui.theme.KeyboardTheme

/** Replaces the keys with a categorised emoji grid. `null` category = recents. */
@Composable
fun EmojiPanel(
    catalog: EmojiCatalog,
    recents: List<Emoji>,
    onEmoji: (Emoji) -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KeyboardTheme.colors
    var category by remember { mutableStateOf(if (recents.isEmpty()) EmojiCategory.SmileysAndPeople else null) }
    val emojis = category?.let(catalog::emojis) ?: recents

    Column(modifier) {
        LazyRow(
            Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item { CategoryTab("🕘", selected = category == null) { category = null } }
            items(catalog.categories) { tab ->
                CategoryTab(tab.icon, selected = category == tab) { category = tab }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(44.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp),
        ) {
            items(emojis, key = { it.value }) { emoji ->
                Box(
                    Modifier.size(44.dp).clickable { onEmoji(emoji) },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji.value, fontSize = 26.sp) }
            }
        }
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelButton("ABC", onClose)
            Spacer(Modifier.weight(1f))
            PanelButton("Delete", onBackspace, icon = KeyboardIcons.Backspace)
        }
    }
    if (emojis.isEmpty()) {
        Text("No recent emoji yet", color = colors.hint, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun CategoryTab(icon: String, selected: Boolean, onClick: () -> Unit) {
    val colors = KeyboardTheme.colors
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.modifierKey else colors.background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(icon, fontSize = 20.sp) }
}

@Composable
private fun PanelButton(label: String, onClick: () -> Unit, icon: ImageVector? = null) {
    val colors = KeyboardTheme.colors
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .size(width = 64.dp, height = 38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.modifierKey)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = label, tint = colors.onKey, modifier = Modifier.size(22.dp))
        } else {
            Text(label, color = colors.onKey, fontSize = 15.sp)
        }
    }
}
