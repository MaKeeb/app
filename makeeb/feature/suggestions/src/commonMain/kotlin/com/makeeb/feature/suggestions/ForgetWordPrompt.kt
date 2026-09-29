package com.makeeb.feature.suggestions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makeeb.ui.theme.KeyboardTheme

/**
 * Takes the strip's place after a long press on a learned word: forget it, or keep it. Typing on
 * keeps it too (the engine drops the offer). The iOS renderer draws the same prompt.
 */
@Composable
fun ForgetWordPrompt(word: String, onForget: () -> Unit, onKeep: () -> Unit, modifier: Modifier = Modifier) {
    val colors = KeyboardTheme.colors
    Row(modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Forget “$word”?",
            color = colors.onKey,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Screen readers announce the question when it appears.
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        PromptButton("Cancel", colors.onKey, FontWeight.Normal, onKeep, Modifier.testTag("forget-cancel"))
        PromptButton("Forget", colors.accentKey, FontWeight.SemiBold, onForget, Modifier.testTag("forget-confirm"))
    }
}

@Composable
private fun PromptButton(label: String, color: Color, weight: FontWeight, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.fillMaxHeight().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 15.sp, fontWeight = weight)
    }
}
