package com.lcdcode.c128.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.lcdcode.c128.terminal.COLS
import com.lcdcode.c128.terminal.PetsciiFont
import com.lcdcode.c128.terminal.ROWS
import com.lcdcode.c128.terminal.TextBuffer
import kotlinx.coroutines.delay

const val BORDER_COLS = 2
const val BORDER_ROWS = 4
const val TOTAL_COLS = COLS + 2 * BORDER_COLS  // 44
const val TOTAL_ROWS = ROWS + 2 * BORDER_ROWS  // 33

@Composable
fun C128Screen(
    buffer: TextBuffer,
    colors: ScreenColors = C128Palette.MODE_C128,
    modifier: Modifier = Modifier,
) {
    var blinkOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) { delay(500); blinkOn = !blinkOn }
    }

    val atlas = remember { PetsciiFont.atlas() }
    val fgFilter = remember(colors.fg) { ColorFilter.tint(colors.fg) }
    val tick = buffer.version

    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION") tick
        val scale = kotlin.math.min(
            size.width / TOTAL_COLS,
            size.height / TOTAL_ROWS,
        ).toInt().coerceAtLeast(1).toFloat()
        val totalW = scale * TOTAL_COLS
        val totalH = scale * TOTAL_ROWS
        val originX = (size.width - totalW) / 2f
        val originY = (size.height - totalH) / 2f

        drawRect(
            color = colors.border,
            topLeft = Offset(originX, originY),
            size = Size(totalW, totalH),
        )
        val screenLeft = originX + BORDER_COLS * scale
        val screenTop = originY + BORDER_ROWS * scale
        drawRect(
            color = colors.bg,
            topLeft = Offset(screenLeft, screenTop),
            size = Size(COLS * scale, ROWS * scale),
        )

        for (y in 0 until ROWS) {
            for (x in 0 until COLS) {
                val code = buffer.cellAt(x, y)
                val isCursor = (x == buffer.cursorX && y == buffer.cursorY && blinkOn)
                val dstLeft = screenLeft + x * scale
                val dstTop = screenTop + y * scale
                if (isCursor) {
                    drawRect(
                        color = colors.fg,
                        topLeft = Offset(dstLeft, dstTop),
                        size = Size(scale, scale),
                    )
                    continue
                }
                if (code in PetsciiFont.FIRST..PetsciiFont.LAST) {
                    val srcX = (code - PetsciiFont.FIRST) * PetsciiFont.CELL
                    drawImage(
                        image = atlas,
                        srcOffset = IntOffset(srcX, 0),
                        srcSize = IntSize(PetsciiFont.CELL, PetsciiFont.CELL),
                        dstOffset = IntOffset(dstLeft.toInt(), dstTop.toInt()),
                        dstSize = IntSize(scale.toInt(), scale.toInt()),
                        colorFilter = fgFilter,
                        filterQuality = FilterQuality.None,
                    )
                }
            }
        }
    }
}
