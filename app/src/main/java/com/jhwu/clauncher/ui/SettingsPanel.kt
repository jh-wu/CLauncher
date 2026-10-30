package com.jhwu.clauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jhwu.clauncher.R
import com.jhwu.clauncher.data.Background

/** Right-hand side panel, like tvOS settings sheets. A dialog window keeps D-pad focus inside it. */
@Composable
fun SettingsPanel(
    current: Background?,
    canPickImage: Boolean,
    onPresetSelected: (String) -> Unit,
    onPickImage: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val firstItem = remember { FocusRequester() }
        LaunchedEffect(Unit) { firstItem.requestFocus() }

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(380.dp)
                    .background(Color(0xF21C1C1E))
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.background),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 36.dp, bottom = 12.dp),
                    )
                }
                itemsIndexed(BackgroundPresets, key = { _, preset -> preset.id }) { index, preset ->
                    ListItem(
                        selected = current == Background.Preset(preset.id),
                        onClick = { onPresetSelected(preset.id) },
                        headlineContent = { Text(preset.name) },
                        leadingContent = {
                            Box(
                                Modifier
                                    .size(width = 56.dp, height = 32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(preset.brush),
                            )
                        },
                        modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
                    )
                }
                if (canPickImage) {
                    item {
                        ListItem(
                            selected = current is Background.Image,
                            onClick = onPickImage,
                            headlineContent = { Text(stringResource(R.string.choose_image)) },
                        )
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
                    )
                }
                item {
                    ListItem(
                        selected = false,
                        onClick = onOpenSystemSettings,
                        headlineContent = { Text(stringResource(R.string.system_settings)) },
                    )
                }
            }
        }
    }
}
