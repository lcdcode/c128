package com.lcdcode.c128

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lcdcode.c128.basic.BasicConsole
import com.lcdcode.c128.basic.Interpreter
import com.lcdcode.c128.terminal.COLS
import com.lcdcode.c128.terminal.TextBuffer
import com.lcdcode.c128.ui.C128Palette
import com.lcdcode.c128.ui.C128Screen
import com.lcdcode.c128.ui.TOTAL_COLS
import com.lcdcode.c128.ui.TOTAL_ROWS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val LAUNCH_KEY_WINDOW_MS = 1500L

class MainActivity : ComponentActivity() {
    private val c64Mode = mutableStateOf(false)
    private var startElapsed: Long = 0L
    var onBreakRequest: () -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startElapsed = SystemClock.elapsedRealtime()
        setContent { C128App(c64Mode, this) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isVolDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        val isVolUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val withinWindow = SystemClock.elapsedRealtime() - startElapsed < LAUNCH_KEY_WINDOW_MS
        if ((isVolDown || isVolUp) && withinWindow) {
            if (event.action == KeyEvent.ACTION_DOWN) c64Mode.value = true
            return true // swallow so volume doesn't change
        }
        if (isVolDown && !withinWindow) {
            if (event.action == KeyEvent.ACTION_DOWN) onBreakRequest()
            return true // swallow: volume-down is the RUN/STOP gesture once the app is up
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
private fun C128App(modeState: MutableState<Boolean>, activity: MainActivity? = null) {
    val c64 by modeState
    val colors = if (c64) C128Palette.MODE_C64 else C128Palette.MODE_C128
    val buffer = remember { TextBuffer() }

    var anchorX by remember { mutableStateOf(0) }
    var anchorY by remember { mutableStateOf(0) }
    var inputText by remember { mutableStateOf("") }
    var executing by remember { mutableStateOf(false) }
    var pendingInput by remember { mutableStateOf<CompletableDeferred<String>?>(null) }
    var currentJob by remember { mutableStateOf<Job?>(null) }

    val console = remember {
        object : BasicConsole {
            override fun print(s: String) { buffer.putString(s) }
            override fun newline() { buffer.newline() }
            override val column: Int get() = buffer.cursorX
            override suspend fun readLine(): String {
                anchorX = buffer.cursorX
                anchorY = buffer.cursorY
                val d = CompletableDeferred<String>()
                pendingInput = d
                return try {
                    d.await()
                } finally {
                    pendingInput = null
                }
            }
            override fun switchMode(c64New: Boolean) {
                modeState.value = c64New
            }
        }
    }
    val interp = remember { Interpreter(console) }
    val scope = rememberCoroutineScope()

    DisposableEffect(activity) {
        activity?.onBreakRequest = {
            interp.breakRequested = true
            pendingInput?.cancel(CancellationException("BREAK"))
        }
        onDispose {
            activity?.onBreakRequest = {}
        }
    }

    LaunchedEffect(c64) {
        currentJob?.cancel(CancellationException("mode switch"))
        currentJob = null
        pendingInput?.cancel(CancellationException("mode switch"))
        pendingInput = null
        interp.breakRequested = false
        buffer.clear()
        if (c64) c64BootBanner(buffer) else c128BootBanner(buffer)
        anchorX = buffer.cursorX
        anchorY = buffer.cursorY
        inputText = ""
        executing = false
    }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                focusRequester.requestFocus()
                keyboard?.show()
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        C128Screen(
            buffer = buffer,
            colors = colors,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(TOTAL_COLS.toFloat() / TOTAL_ROWS.toFloat()),
        )

        BasicTextField(
            value = inputText,
            onValueChange = { raw ->
                val clean = raw.replace("\n", "").take(COLS - 1)
                inputText = clean
                redrawInput(buffer, anchorX, anchorY, clean)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    val line = inputText
                    inputText = ""
                    buffer.setCursor(0, anchorY)
                    redrawInput(buffer, anchorX, anchorY, line)
                    buffer.newline()
                    anchorX = buffer.cursorX
                    anchorY = buffer.cursorY

                    val pending = pendingInput
                    if (pending != null) {
                        pending.complete(line)
                        return@KeyboardActions
                    }
                    if (executing) return@KeyboardActions
                    executing = true
                    val trimmed = line.trim()
                    val isProgramEntry = trimmed.isEmpty() ||
                        trimmed.firstOrNull()?.isDigit() == true
                    currentJob = scope.launch {
                        try {
                            interp.execLine(line)
                        } catch (_: CancellationException) {
                            // Cancelled by mode switch; suppress.
                        } finally {
                            if (!isProgramEntry) {
                                buffer.putString("READY.")
                                buffer.newline()
                            }
                            anchorX = buffer.cursorX
                            anchorY = buffer.cursorY
                            executing = false
                            currentJob = null
                        }
                    }
                },
            ),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .focusRequester(focusRequester),
        )
    }
}

private fun redrawInput(buffer: TextBuffer, ax: Int, ay: Int, text: String) {
    buffer.setCursor(ax, ay)
    buffer.clearFromCursorToEndOfLine()
    buffer.setCursor(ax, ay)
    buffer.putString(text)
}

private fun c128BootBanner(buffer: TextBuffer) {
    buffer.putString("\n")
    buffer.putString(" COMMODORE BASIC V7.0 122365 BYTES FREE\n")
    buffer.putString("   (C)1986 COMMODORE ELECTRONICS, LTD.\n")
    buffer.putString("         (C)1977 MICROSOFT CORP.\n")
    buffer.putString("           ALL RIGHTS RESERVED\n")
    buffer.putString("\n")
    buffer.putString("READY.\n")
}

private fun c64BootBanner(buffer: TextBuffer) {
    buffer.putString("\n")
    buffer.putString("    **** COMMODORE 64 BASIC V2 ****\n")
    buffer.putString("\n")
    buffer.putString(" 64K RAM SYSTEM  38911 BASIC BYTES FREE\n")
    buffer.putString("\n")
    buffer.putString("READY.\n")
}
