package com.lcdcode.c128.ui

import androidx.compose.ui.graphics.Color

data class ScreenColors(val border: Color, val bg: Color, val fg: Color)

/**
 * Canonical 16-entry VIC-IIe palette (approximate sRGB values commonly cited).
 * C128 40-col defaults: dark-gray screen, light-green border + text.
 * C64 defaults: blue screen, light-blue border + text.
 */
object C128Palette {
    val BLACK        = Color(0xFF000000)
    val WHITE        = Color(0xFFFFFFFF)
    val RED          = Color(0xFF883932)
    val CYAN         = Color(0xFF67B6BD)
    val PURPLE       = Color(0xFF8B3F96)
    val GREEN        = Color(0xFF55A049)
    val BLUE         = Color(0xFF40318D)
    val YELLOW       = Color(0xFFBFCE72)
    val ORANGE       = Color(0xFF8B5429)
    val BROWN        = Color(0xFF574200)
    val LIGHT_RED    = Color(0xFFB86962)
    val DARK_GRAY    = Color(0xFF505050)
    val MEDIUM_GRAY  = Color(0xFF787878)
    val LIGHT_GREEN  = Color(0xFF94E089)
    val LIGHT_BLUE   = Color(0xFF7869C4)
    val LIGHT_GRAY   = Color(0xFF9F9F9F)

    val MODE_C128 = ScreenColors(border = LIGHT_GREEN, bg = DARK_GRAY, fg = LIGHT_GREEN)
    val MODE_C64  = ScreenColors(border = LIGHT_BLUE,  bg = BLUE,      fg = LIGHT_BLUE)
}
