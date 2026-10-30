package com.jhwu.clauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import coil3.compose.AsyncImage
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jhwu.clauncher.data.Background
import com.jhwu.clauncher.data.LaunchableApp
import com.jhwu.clauncher.data.UpdateState
import kotlin.math.roundToInt

private enum class Page(val title: String) {
    Main("Settings"),
    Background("Background"),
    Apps("Apps"),
    About("About & updates"),
}

/**
 * Right-hand settings sheet, like tvOS. A dialog window keeps D-pad focus inside it.
 * Back goes from a sub-page to the main page, then closes the sheet.
 */
@Composable
fun SettingsPanel(
    viewModel: HomeViewModel,
    onPickImage: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onQuit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(Page.Main) }
    val background by viewModel.background.collectAsStateWithLifecycle()
    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenApps.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val firstItem = remember { FocusRequester() }
    LaunchedEffect(page) {
        if (page == Page.About && updateState == UpdateState.Idle) viewModel.checkForUpdates()
        // The list's first item is composed during a later layout pass, so retry for a few frames.
        repeat(10) {
            withFrameNanos {}
            if (runCatching { firstItem.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    BackHandler { if (page == Page.Main) onDismiss() else page = Page.Main }

    // Drawn in the home screen's own window (not a dialog), so D-pad focus always lands here;
    // the home screen makes its tiles unfocusable while this is open.
    run {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
        ) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(400.dp)
                    .background(Color(0xF21C1C1E))
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "title-${page.name}") {
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 36.dp, bottom = 12.dp),
                    )
                }
                when (page) {
                    Page.Main -> mainPage(
                        viewModel, background, hidden.size, firstItem, { page = it }, onOpenSystemSettings, onQuit,
                    )
                    Page.Background -> backgroundPage(viewModel, background, firstItem, onPickImage)
                    Page.Apps -> appsPage(viewModel, allApps, hidden, firstItem)
                    Page.About -> aboutPage(viewModel, updateState, firstItem)
                }
                item { Box(Modifier.height(24.dp)) }
            }
        }
    }
}

private fun LazyListScope.mainPage(
    viewModel: HomeViewModel,
    background: Background?,
    hiddenCount: Int,
    firstItem: FocusRequester,
    open: (Page) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onQuit: () -> Unit,
) {
    item(key = "main-background") {
        NavItem(
            title = Page.Background.title,
            detail = when (val bg = background) {
                is Background.Preset -> presetFor(bg.id).name
                is Background.Image -> "Your image"
                Background.Daily -> "Photo of the day"
                is Background.Bundled -> "Photo"
                null -> ""
            },
            onClick = { open(Page.Background) },
            modifier = Modifier.focusRequester(firstItem),
        )
    }
    item(key = "main-apps") {
        NavItem(
            title = Page.Apps.title,
            detail = if (hiddenCount == 0) "All shown" else "$hiddenCount hidden",
            onClick = { open(Page.Apps) },
        )
    }
    item(key = "main-about") {
        NavItem(
            title = Page.About.title,
            detail = "Version ${viewModel.versionName}",
            onClick = { open(Page.About) },
        )
    }
    item(key = "main-system") {
        NavItem(title = "Android TV settings", detail = null, onClick = onOpenSystemSettings)
    }
    item(key = "main-quit") {
        ListItem(
            selected = false,
            onClick = onQuit,
            headlineContent = { Text("Quit CLauncher") },
            supportingContent = { Text("Go back to the Google TV home screen") },
        )
    }
}

private fun LazyListScope.backgroundPage(
    viewModel: HomeViewModel,
    background: Background?,
    firstItem: FocusRequester,
    onPickImage: () -> Unit,
) {
    item(key = "bg-daily") {
        ListItem(
            selected = background == Background.Daily,
            onClick = { viewModel.setDailyBackground() },
            headlineContent = { Text("Photo of the day") },
            supportingContent = {
                val today = viewModel.dailyPhoto.collectAsStateWithLifecycle().value
                Text(
                    if (background == Background.Daily && today != null) today.credit
                    else "A new photo every day from Wikimedia Commons",
                )
            },
            modifier = Modifier.focusRequester(firstItem),
        )
    }
    itemsIndexed(viewModel.bundledPhotos, key = { _, photo -> "bg-photo-${photo.asset}" }) { _, photo ->
        ListItem(
            selected = background == Background.Bundled(photo.asset),
            onClick = { viewModel.setBundledBackground(photo.asset) },
            headlineContent = { Text(photo.location, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(photo.credit, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            leadingContent = {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 56.dp, height = 32.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            },
        )
    }
    itemsIndexed(BackgroundPresets, key = { _, preset -> "bg-${preset.id}" }) { _, preset ->
        ListItem(
            selected = background == Background.Preset(preset.id),
            onClick = { viewModel.setPresetBackground(preset.id) },
            headlineContent = { Text(preset.name) },
            leadingContent = {
                Box(
                    Modifier
                        .size(width = 56.dp, height = 32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(preset.brush),
                )
            },
        )
    }
    if (viewModel.imagePicker != ImagePicker.None) {
        item(key = "bg-image") {
            ListItem(
                selected = background is Background.Image,
                onClick = onPickImage,
                headlineContent = { Text("Choose an image…") },
            )
        }
    }
}

private fun LazyListScope.appsPage(
    viewModel: HomeViewModel,
    apps: List<LaunchableApp>,
    hidden: Set<String>,
    firstItem: FocusRequester,
) {
    item(key = "apps-hint") {
        Text(
            text = "Press OK to show or hide an app on the home screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    itemsIndexed(apps.sortedBy { it.label.lowercase() }, key = { _, app -> "app-${app.packageName}" }) { index, app ->
        val isHidden = app.packageName in hidden
        AppVisibilityItem(
            app = app,
            hidden = isHidden,
            onToggle = { viewModel.setHidden(app.packageName, !isHidden) },
            modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
        )
    }
}

private fun LazyListScope.aboutPage(viewModel: HomeViewModel, state: UpdateState, firstItem: FocusRequester) {
    item(key = "about-version") {
        Text(
            text = "CLauncher ${viewModel.versionName}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    item(key = "about-update") {
        val (title, detail) = when (val s = state) {
            UpdateState.Idle -> "Check for updates" to null
            UpdateState.Checking -> "Checking…" to null
            UpdateState.UpToDate -> "Check for updates" to "You have the latest version"
            is UpdateState.Available -> "Install update" to "Build ${s.build} is available"
            is UpdateState.Downloading -> "Downloading…" to "${(s.progress * 100).roundToInt()}%"
            UpdateState.Installing -> "Install update" to "Choose Install, then Open when it's done"
            is UpdateState.Failed -> "Check for updates" to s.message
        }
        ListItem(
            selected = false,
            onClick = {
                when (val s = state) {
                    is UpdateState.Available -> viewModel.installUpdate(s)
                    UpdateState.Checking, is UpdateState.Downloading -> Unit
                    else -> viewModel.checkForUpdates()
                }
            },
            headlineContent = { Text(title) },
            supportingContent = detail?.let { { Text(it) } },
            modifier = Modifier.focusRequester(firstItem),
        )
    }
}

@Composable
private fun NavItem(title: String, detail: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(title) },
        supportingContent = detail?.let { { Text(it) } },
        trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge) },
        modifier = modifier,
    )
}

@Composable
private fun AppVisibilityItem(
    app: LaunchableApp,
    hidden: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = false,
        onClick = onToggle,
        headlineContent = { Text(app.label) },
        leadingContent = {
            val image = app.banner ?: app.icon
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = if (app.banner != null) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier
                        .height(32.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(6.dp)),
                )
            }
        },
        trailingContent = { Text(if (hidden) "Hidden" else "Shown") },
        modifier = modifier,
    )
}
