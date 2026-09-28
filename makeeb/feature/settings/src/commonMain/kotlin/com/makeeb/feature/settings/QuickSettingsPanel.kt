package com.makeeb.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makeeb.core.settings.KeyboardPreferences
import com.makeeb.core.settings.QuickSetting
import com.makeeb.ui.theme.KeyboardTheme

/**
 * The keyboard's quick settings, in place of the keys: one tile per [QuickSetting], lit while on.
 * [editable] is false where the keyboard may only read settings (iOS without Full Access); the
 * tiles then show their state and the panel says why they don't respond. [onOpenApp] is null where
 * the keyboard can't open the companion app.
 */
@Composable
fun QuickSettingsPanel(
    preferences: KeyboardPreferences,
    editable: Boolean,
    onToggle: (QuickSetting) -> Unit,
    onOpenApp: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KeyboardTheme.colors
    Column(modifier.padding(horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Quick settings", color = colors.onKey, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            if (onOpenApp != null) HeaderAction("All settings", onOpenApp)
            HeaderAction("ABC", onClose)
        }
        if (!editable) {
            Text(
                "Allow Full Access for MaKeeb to change these here. They're all in the MaKeeb app too.",
                color = colors.hint,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
            )
        }
        QuickSetting.entries.chunked(COLUMNS).forEach { row ->
            Row(
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { setting ->
                    Tile(setting, preferences, editable, { onToggle(setting) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun Tile(setting: QuickSetting, preferences: KeyboardPreferences, editable: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = KeyboardTheme.colors
    val on = setting.isOn(preferences)
    val value = setting.valueLabel(preferences)
    // A switch reads as one toggle to screen readers; the theme is a button that says its mode.
    val action = if (setting == QuickSetting.Theme) {
        Modifier.clickable(enabled = editable, role = Role.Button, onClick = onClick).semantics { stateDescription = value }
    } else {
        Modifier.toggleable(value = on, enabled = editable, role = Role.Switch, onValueChange = { onClick() })
    }
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) colors.accentKey else colors.key)
            .then(action)
            .testTag("quick-${setting.name}")
            .alpha(if (editable) 1f else 0.5f)
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val text = if (on) colors.onAccentKey else colors.onKey
        Text(setting.title, color = text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = if (on) text else colors.hint, fontSize = 12.sp, maxLines = 1)
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

private const val COLUMNS = 4
