package com.mazharansari.x.ui.theme

import androidx.compose.runtime.Composable

/**
 * Dots theme entry point — the OpenDots / OpenFuse design language
 * (see the token table in Theme.kt). Same contract as the old SandTheme:
 * provides [LocalPalette] + MaterialTheme. The app root uses this;
 * [AxionTheme] stays as the test-suite entry point and shares the same
 * palettes, so both render identically.
 */
enum class DotsLook { System, Light, Dark }

@Composable
fun DotsTheme(look: DotsLook = DotsLook.System, textScale: Float = 1f, content: @Composable () -> Unit) {
    AxionTheme(when (look) { DotsLook.Light -> "light"; DotsLook.Dark -> "dark"; DotsLook.System -> "system" }, textScale, content)
}
