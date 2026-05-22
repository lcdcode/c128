package com.lcdcode.c128.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

const val COLS = 40
const val ROWS = 25

// PETSCII control codes recognized by putChar/putString. These are the
// Commodore conventions used by `PRINT CHR$(147)` (clear screen) and
// `PRINT CHR$(19)` (home cursor).
private const val PETSCII_CLR = ''
private const val PETSCII_HOME = ''

class TextBuffer {
    private val cells = IntArray(COLS * ROWS) // ASCII code, 0 = blank
    var cursorX: Int = 0
    var cursorY: Int = 0
    private var versionState by mutableStateOf(0)
    val version: Int get() = versionState

    init { clear() }

    fun cellAt(x: Int, y: Int): Int = cells[y * COLS + x]

    fun clear() {
        cells.fill(' '.code)
        cursorX = 0; cursorY = 0
        bump()
    }

    fun putChar(c: Char) {
        when (c) {
            '\n' -> newline()
            '\r' -> cursorX = 0
            PETSCII_CLR -> clear()
            PETSCII_HOME -> { cursorX = 0; cursorY = 0 }
            else -> {
                if (cursorX >= COLS) newline()
                cells[cursorY * COLS + cursorX] = c.code and 0xFF
                cursorX++
            }
        }
        bump()
    }

    fun putString(s: String) {
        for (c in s) {
            when (c) {
                '\n' -> newline()
                '\r' -> cursorX = 0
                PETSCII_CLR -> clear()
                PETSCII_HOME -> { cursorX = 0; cursorY = 0 }
                else -> {
                    if (cursorX >= COLS) newline()
                    cells[cursorY * COLS + cursorX] = c.code and 0xFF
                    cursorX++
                }
            }
        }
        bump()
    }

    fun newline() {
        cursorX = 0
        cursorY++
        if (cursorY >= ROWS) scrollUp()
        bump()
    }

    fun backspace() {
        if (cursorX > 0) {
            cursorX--
            cells[cursorY * COLS + cursorX] = ' '.code
            bump()
        }
    }

    fun setCursor(x: Int, y: Int) {
        cursorX = x.coerceIn(0, COLS - 1)
        cursorY = y.coerceIn(0, ROWS - 1)
        bump()
    }

    fun clearFromCursorToEndOfLine() {
        for (x in cursorX until COLS) cells[cursorY * COLS + x] = ' '.code
        bump()
    }

    private fun scrollUp() {
        for (y in 1 until ROWS) {
            for (x in 0 until COLS) {
                cells[(y - 1) * COLS + x] = cells[y * COLS + x]
            }
        }
        for (x in 0 until COLS) cells[(ROWS - 1) * COLS + x] = ' '.code
        cursorY = ROWS - 1
    }

    private fun bump() { versionState++ }
}
