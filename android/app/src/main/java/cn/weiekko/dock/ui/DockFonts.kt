package cn.weiekko.dock.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import cn.weiekko.dock.R
import cn.weiekko.dock.data.DockFont
import cn.weiekko.dock.data.TypeLook

val LocalTypeLook = compositionLocalOf { TypeLook() }

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int): FontFamily = FontFamily(
    Font(resId, FontWeight.ExtraLight, variationSettings = FontVariation.Settings(FontVariation.weight(200))),
    Font(resId, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(resId, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(resId, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(resId, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

private val InterFamily by lazy { variableFamily(R.font.inter) }
private val OutfitFamily by lazy { variableFamily(R.font.outfit) }
private val JetBrainsFamily by lazy { variableFamily(R.font.jetbrains_mono) }
private val SmileyFamily by lazy { FontFamily(Font(R.font.smiley_sans, FontWeight.Normal)) }

fun DockFont.toFamily(): FontFamily = when (this) {
    DockFont.System -> FontFamily.Default
    DockFont.Sans -> FontFamily.SansSerif
    DockFont.Serif -> FontFamily.Serif
    DockFont.Mono -> FontFamily.Monospace
    DockFont.Inter -> InterFamily
    DockFont.Outfit -> OutfitFamily
    DockFont.JetBrainsMono -> JetBrainsFamily
    DockFont.Smiley -> SmileyFamily
}

@Composable
fun typeLookOrDefault(): TypeLook = LocalTypeLook.current
