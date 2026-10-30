package com.jhwu.clauncher.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jhwu.clauncher.data.LaunchableApp

private val TileShape = RoundedCornerShape(14.dp)

@Composable
fun AppTile(
    app: LaunchableApp,
    isMoving: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(if (focused && !isMoving) 1f else 0f, label = "labelAlpha")
    // Apple TV-style wiggle while the tile is being moved.
    val wiggle = if (isMoving) {
        rememberInfiniteTransition(label = "wiggle").animateFloat(
            initialValue = -1.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 140), RepeatMode.Reverse),
            label = "wiggleAngle",
        ).value
    } else {
        0f
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .graphicsLayer { rotationZ = wiggle }
                .onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(TileShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f, pressedScale = 1.06f),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.7f), elevation = 20.dp),
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.14f),
                focusedContainerColor = Color.White.copy(alpha = 0.24f),
                pressedContainerColor = Color.White.copy(alpha = 0.24f),
            ),
        ) {
            if (app.banner != null) {
                Image(
                    bitmap = app.banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight(0.6f)
                        .aspectRatio(1f),
                )
            }
            if (focused) {
                // Glossy sheen across the top of the focused tile.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.22f),
                                0.5f to Color.Transparent,
                            ),
                        ),
                )
            }
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 14.dp)
                .alpha(labelAlpha),
        )
    }
}
