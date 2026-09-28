package com.makeeb.feature.clipboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makeeb.engine.clipboard.ClipboardEntry
import com.makeeb.ui.theme.KeyboardTheme

/**
 * Clipboard history in place of the keys. [available] is false where the OS withholds the
 * clipboard (iOS without Full Access); the panel then explains why it is empty.
 */
@Composable
fun ClipboardPanel(
    entries: List<ClipboardEntry>,
    available: Boolean,
    onPaste: (ClipboardEntry) -> Unit,
    onTogglePin: (ClipboardEntry) -> Unit,
    onDelete: (ClipboardEntry) -> Unit,
    onClearUnpinned: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    snippets: List<String> = emptyList(),
    onSnippet: (String) -> Unit = {},
) {
    val colors = KeyboardTheme.colors
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Clipboard", color = colors.onKey, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            HeaderAction("Clear", onClearUnpinned)
            HeaderAction("ABC", onClose)
        }
        // The user's own texts: they don't need the clipboard, so they show even without it.
        if (snippets.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().height(40.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(snippets) { snippet ->
                    Text(
                        snippet.replace('\n', ' '),
                        color = colors.onKey,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.key)
                            .clickable { onSnippet(snippet) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        when {
            !available -> Message("Allow Full Access for MaKeeb in Settings to use clipboard history.")
            entries.isEmpty() -> Message("Copied text shows up here.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    ClipCard(entry, onPaste = { onPaste(entry) }, onTogglePin = { onTogglePin(entry) }, onDelete = { onDelete(entry) })
                }
            }
        }
    }
}

@Composable
private fun ClipCard(entry: ClipboardEntry, onPaste: () -> Unit, onTogglePin: () -> Unit, onDelete: () -> Unit) {
    val colors = KeyboardTheme.colors
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.key)
            .clickable(onClick = onPaste)
            .padding(10.dp),
    ) {
        Text(entry.text, color = colors.onKey, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
            SmallAction(if (entry.pinned) "Unpin" else "Pin", onTogglePin)
            SmallAction("Delete", onDelete)
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = KeyboardTheme.colors.hint, fontSize = 14.sp)
    }
}

@Composable
private fun HeaderAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = KeyboardTheme.colors.onKey,
        fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = KeyboardTheme.colors.hint,
        fontSize = 12.sp,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
