package com.jhwu.clauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jhwu.clauncher.data.Background
import com.jhwu.clauncher.data.SettingsRepository
import java.io.File

data class BackgroundPreset(val id: String, val name: String, val colors: List<Color>) {
    val brush: Brush get() = Brush.linearGradient(colors)
}

val BackgroundPresets = listOf(
    BackgroundPreset("midnight", "Midnight", listOf(Color(0xFF0B1026), Color(0xFF1C2541), Color(0xFF3A506B))),
    BackgroundPreset("aurora", "Aurora", listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C7873))),
    BackgroundPreset("dusk", "Dusk", listOf(Color(0xFF2E1437), Color(0xFF6A3093), Color(0xFFE96443))),
    BackgroundPreset("ocean", "Ocean", listOf(Color(0xFF000428), Color(0xFF004E92))),
    BackgroundPreset("graphite", "Graphite", listOf(Color(0xFF1C1C1E), Color(0xFF3A3A3C))),
    BackgroundPreset("black", "Black", listOf(Color.Black, Color.Black)),
)

fun presetFor(id: String) = BackgroundPresets.firstOrNull { it.id == id }
    ?: BackgroundPresets.first { it.id == SettingsRepository.DEFAULT_PRESET_ID }

@Composable
fun BackgroundLayer(background: Background?, wallpaperFile: File, modifier: Modifier = Modifier) {
    when (background) {
        null -> Box(modifier.background(Color.Black))
        is Background.Preset -> Box(modifier.background(presetFor(background.id).brush))
        is Background.Image -> {
            val context = LocalContext.current
            val request = remember(background.version) {
                ImageRequest.Builder(context)
                    .data(wallpaperFile)
                    .memoryCacheKey("wallpaper-${background.version}")
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier.background(Color.Black),
            )
        }
    }
}
