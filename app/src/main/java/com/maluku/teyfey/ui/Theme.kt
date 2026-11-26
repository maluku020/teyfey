package com.maluku.teyfey.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Z {
    val bg = Color(0xFF0B0E13)
    val panel = Color(0xFF11161D)
    val surface = Color(0xFF171D26)
    val surfaceHi = Color(0xFF212938)
    val accent = Color(0xFF7C5CFF)
    val accentSoft = Color(0x337C5CFF)
    val text = Color(0xFFE9EDF3)
    val textDim = Color(0xFF8D97A6)
    val live = Color(0xFFFF4D5E)
    val fav = Color(0xFFFFC145)
    /** Focus colour for the TV remote: bright yellow, visible from the couch. */
    val focus = Color(0xFFFFD600)
}

@Composable
fun TeyfeyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Z.accent,
            onPrimary = Color.White,
            secondary = Z.accent,
            background = Z.bg,
            onBackground = Z.text,
            surface = Z.surface,
            onSurface = Z.text,
            surfaceVariant = Z.surfaceHi,
            onSurfaceVariant = Z.textDim,
            outline = Z.surfaceHi,
        ),
        content = content,
    )
}
