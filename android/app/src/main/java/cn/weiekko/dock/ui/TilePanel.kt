package cn.weiekko.dock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import cn.weiekko.dock.data.TileLook
import cn.weiekko.dock.data.TileStyle

val LocalTileLook = compositionLocalOf { TileLook() }

fun TileLook.fillBrush(highlighted: Boolean): Brush {
    val a = opacity
    return when (style) {
        TileStyle.Frost -> {
            val top = if (highlighted) {
                Color(0xFFE8DCC8).copy(alpha = a * 0.82f)
            } else {
                Color.White.copy(alpha = a * 0.90f)
            }
            val bot = if (highlighted) {
                Color(0xFFC4B496).copy(alpha = a * 0.45f)
            } else {
                Color.White.copy(alpha = a * 0.36f)
            }
            Brush.verticalGradient(listOf(top, bot))
        }
        TileStyle.Ink -> {
            val top = if (highlighted) {
                Color(0xFF3A3428).copy(alpha = (0.28f + a * 0.62f).coerceAtMost(0.92f))
            } else {
                Color.Black.copy(alpha = (0.22f + a * 0.68f).coerceAtMost(0.90f))
            }
            val bot = if (highlighted) {
                Color(0xFF1C1810).copy(alpha = (0.18f + a * 0.55f).coerceAtMost(0.88f))
            } else {
                Color.Black.copy(alpha = (0.14f + a * 0.52f).coerceAtMost(0.86f))
            }
            Brush.verticalGradient(listOf(top, bot))
        }
        TileStyle.Solid -> {
            val color = if (highlighted) {
                Color(0xFF2E2A22).copy(alpha = (0.55f + a * 0.40f).coerceAtMost(0.96f))
            } else {
                Color(0xFF161616).copy(alpha = (0.50f + a * 0.45f).coerceAtMost(0.96f))
            }
            SolidColor(color)
        }
        TileStyle.Line -> SolidColor(Color.White.copy(alpha = a * 0.10f))
    }
}

fun TileLook.strokeColor(highlighted: Boolean): Color {
    val a = opacity
    return when (style) {
        TileStyle.Line -> Color.White.copy(alpha = (0.28f + a * 0.42f).coerceAtMost(0.80f))
        TileStyle.Ink, TileStyle.Solid -> if (highlighted) {
            Color(0xFFE8DCC8).copy(alpha = 0.22f + a * 0.35f)
        } else {
            Color.White.copy(alpha = 0.10f + a * 0.22f)
        }
        TileStyle.Frost -> if (highlighted) {
            Color(0xFFE8DCC8).copy(alpha = 0.28f + a * 0.40f)
        } else {
            Color.White.copy(alpha = 0.16f + a * 0.32f)
        }
    }
}

fun TileLook.strokeWidth() = if (style == TileStyle.Line) 1.4.dp else 1.dp

fun TileLook.cornerShape(): Shape = RoundedCornerShape(cornerDp.coerceIn(TileLook.CORNER_MIN, TileLook.CORNER_MAX).dp)

@Composable
fun TilePanel(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    shape: Shape? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val look = LocalTileLook.current
    val resolved = shape ?: look.cornerShape()
    Box(
        modifier = modifier
            .clip(resolved)
            .background(look.fillBrush(highlighted))
            .border(
                width = look.strokeWidth(),
                color = look.strokeColor(highlighted),
                shape = resolved,
            ),
        content = content,
    )
}
