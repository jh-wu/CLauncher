package com.jhwu.clauncher.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jhwu.clauncher.R
import com.jhwu.clauncher.data.Background
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val COLUMNS = 5

/** Empty space above the first row, where the wallpaper shows through like the tvOS top shelf. */
private val TopShelfHeight = 250.dp

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val movingPackage by viewModel.movingPackage.collectAsStateWithLifecycle()
    val background by viewModel.background.collectAsStateWithLifecycle()
    val dailyPhoto by viewModel.dailyPhoto.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    val activity = LocalActivity.current

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // The tile focus returns to when leaving the gear: the last focused one, or the first.
    val returnTile = remember { FocusRequester() }
    var lastFocusedPackage by remember { mutableStateOf<String?>(null) }
    val returnPackage = lastFocusedPackage?.takeIf { pkg -> apps.any { it.packageName == pkg } }
        ?: apps.firstOrNull()?.packageName
    // Only once the first row has scrolled away; focusing a tile nudges the grid by a few pixels.
    val scrolled by remember { derivedStateOf { gridState.firstVisibleItemIndex >= COLUMNS } }
    val backgroundBlur by animateDpAsState(if (scrolled || showSettings) 28.dp else 0.dp, label = "blur")

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::setImageBackground)
    }
    val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::setImageBackground)
    }

    // A launcher has nowhere to go back to.
    BackHandler {}
    BackHandler(enabled = movingPackage != null) { viewModel.finishMove() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                val isMenu = event.key == Key.Menu && event.type == KeyEventType.KeyUp
                if (isMenu && movingPackage == null) showSettings = true
                isMenu
            },
    ) {
        BackgroundLayer(
            background = background,
            wallpaperFile = viewModel.wallpaperFile,
            dailyPhoto = dailyPhoto,
            modifier = Modifier
                .fillMaxSize()
                .blur(backgroundBlur),
        )
        // Darken the lower half so labels stay readable on bright wallpapers.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
        )

        AppGrid(
            viewModel = viewModel,
            gridState = gridState,
            movingPackage = movingPackage,
            returnTile = returnTile,
            returnPackage = returnPackage,
            onTileFocused = { lastFocusedPackage = it },
        )

        // Where the photo was taken. The full credit is in Settings → Background.
        val caption = when (val bg = background) {
            Background.Daily -> dailyPhoto?.location
            is Background.Bundled -> viewModel.bundledPhotos.firstOrNull { it.asset == bg.asset }?.location
            else -> null
        }
        AnimatedVisibility(
            visible = caption != null && !scrolled && movingPackage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 30.dp, start = 58.dp)
                .fillMaxWidth(0.55f),
        ) {
            if (caption != null) PhotoCaption(caption)
        }

        AnimatedVisibility(
            // Always shown, so Up from the first row can always reach the gear.
            visible = movingPackage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 58.dp),
        ) {
            TopBar(
                onOpenSettings = { showSettings = true },
                onLeave = {
                    scope.launch {
                        val index = apps.indexOfFirst { it.packageName == returnPackage }
                        if (index >= 0 && gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                            gridState.scrollToItem(index)
                            withFrameNanos {}
                        }
                        runCatching { returnTile.requestFocus() }
                    }
                },
            )
        }

        if (movingPackage != null) {
            Text(
                text = stringResource(R.string.move_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )
        }
    }

    if (showSettings) {
        SettingsPanel(
            viewModel = viewModel,
            onPickImage = {
                when (viewModel.imagePicker) {
                    ImagePicker.OpenDocument -> openDocument.launch(arrayOf("image/*"))
                    ImagePicker.GetContent -> getContent.launch("image/*")
                    ImagePicker.None -> Unit
                }
            },
            onOpenSystemSettings = {
                showSettings = false
                viewModel.openSystemSettings()
            },
            onQuit = {
                showSettings = false
                // Hand over to the stock home screen first, otherwise Android just brings CLauncher back.
                viewModel.otherHomeIntent()?.let { runCatching { activity?.startActivity(it) } }
                activity?.finishAndRemoveTask()
            },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun AppGrid(
    viewModel: HomeViewModel,
    gridState: LazyGridState,
    movingPackage: String?,
    returnTile: FocusRequester,
    returnPackage: String?,
    onTileFocused: (String) -> Unit,
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    var initialFocusDone by remember { mutableStateOf(false) }
    // Set when OK goes down during a move, so the key-up of the long press that started the move is ignored.
    var okArmed by remember { mutableStateOf(false) }

    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty() && !initialFocusDone) {
            initialFocusDone = runCatching { returnTile.requestFocus() }.isSuccess
        }
    }

    // Keep the moving tile on screen as it travels through the grid.
    LaunchedEffect(apps, movingPackage) {
        val index = apps.indexOfFirst { it.packageName == movingPackage }
        if (index >= 0 && gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            gridState.animateScrollToItem(index)
        }
    }

    fun handleMoveKey(event: KeyEvent): Boolean {
        val isDown = event.type == KeyEventType.KeyDown
        return when (event.key) {
            Key.DirectionLeft -> { if (isDown) viewModel.moveBy(-1); true }
            Key.DirectionRight -> { if (isDown) viewModel.moveBy(1); true }
            Key.DirectionUp -> { if (isDown) viewModel.moveBy(-COLUMNS); true }
            Key.DirectionDown -> { if (isDown) viewModel.moveBy(COLUMNS); true }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> when {
                isDown -> {
                    if (event.nativeKeyEvent.repeatCount == 0) okArmed = true
                    true
                }
                okArmed -> {
                    okArmed = false
                    viewModel.finishMove()
                    true
                }
                // Key-up of the long press itself: let the tile see it so its pressed state clears.
                else -> false
            }
            else -> false
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        state = gridState,
        contentPadding = PaddingValues(start = 58.dp, end = 58.dp, top = TopShelfHeight, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event -> movingPackage != null && handleMoveKey(event) },
    ) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { _, app ->
            AppTile(
                app = app,
                isMoving = app.packageName == movingPackage,
                onClick = { viewModel.launch(app) },
                onLongClick = {
                    okArmed = false
                    viewModel.startMove(app.packageName)
                },
                modifier = Modifier
                    .animateItem()
                    .onFocusChanged { if (it.hasFocus) onTileFocused(app.packageName) }
                    .then(if (app.packageName == returnPackage) Modifier.focusRequester(returnTile) else Modifier),
            )
        }
    }
}

@Composable
private fun TopBar(onOpenSettings: () -> Unit, onLeave: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Clock()
        Surface(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(36.dp)
                // Default focus search doesn't find a way back into the grid from here, so do it explicitly.
                .onPreviewKeyEvent { event ->
                    val leaving = event.key == Key.DirectionDown || event.key == Key.DirectionLeft
                    if (leaving && event.type == KeyEventType.KeyDown) onLeave()
                    leaving
                },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
            // Barely there until focused, like the tvOS status area.
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Color.White.copy(alpha = 0.4f),
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.settings),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(20.dp),
            )
        }
    }
}

@Composable
private fun PhotoCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.55f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Clock() {
    val context = LocalContext.current
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    // e.g. "Fri, Oct 30", in the TV's locale.
    val dateFormat = remember {
        val locale = Locale.getDefault()
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    val date = Date(now)
    Text(
        text = "${dateFormat.format(date)}  ${timeFormat.format(date)}",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.55f),
    )
}
