package com.maluku.teyfey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

// ---------------------------------------------------------------- back (Esc)

object BackDispatcher {
    class Entry(var enabled: Boolean, var onBack: () -> Unit)

    private val stack = ArrayList<Entry>()

    fun add(e: Entry) {
        stack.add(e)
    }

    fun remove(e: Entry) {
        stack.remove(e)
    }

    /** Runs the newest enabled handler. Returns false when nothing handled it. */
    fun back(): Boolean {
        val e = stack.lastOrNull { it.enabled } ?: return false
        e.onBack()
        return true
    }
}

/** Same idea as Android's BackHandler: Esc runs [onBack]. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val entry = remember { BackDispatcher.Entry(enabled, onBack) }
    SideEffect {
        entry.enabled = enabled
        entry.onBack = onBack
    }
    DisposableEffect(entry) {
        BackDispatcher.add(entry)
        onDispose { BackDispatcher.remove(entry) }
    }
}

// ---------------------------------------------------------------- window / fullscreen

/**
 * Our own "borderless" full screen. Compose's WindowPlacement.Fullscreen uses Windows' exclusive
 * full-screen mode: the window minimizes as soon as you click on a second monitor, and Esc can get lost.
 * The window is undecorated (own title bar, see Main.kt); full screen = cover the whole monitor.
 */
object WindowCtl {
    var state: WindowState? = null
    var window: java.awt.Frame? = null

    private val fs = androidx.compose.runtime.mutableStateOf(false)
    private val max = androidx.compose.runtime.mutableStateOf(false)
    private var savedBounds: java.awt.Rectangle? = null      // before full screen / maximize
    private var fsWasMax = false

    val isFullscreen: Boolean get() = fs.value
    val isMaximized: Boolean get() = max.value

    private fun monitor(w: java.awt.Window): java.awt.Rectangle = w.graphicsConfiguration.bounds
    private fun workArea(w: java.awt.Window): java.awt.Rectangle {
        val b = monitor(w)
        val ins = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(w.graphicsConfiguration)
        return java.awt.Rectangle(b.x + ins.left, b.y + ins.top, b.width - ins.left - ins.right, b.height - ins.top - ins.bottom)
    }

    fun setFullscreen(on: Boolean) {
        val w = window ?: return
        if (on == fs.value) return
        if (on) {
            fsWasMax = max.value
            if (!max.value) savedBounds = w.bounds
            w.extendedState = java.awt.Frame.NORMAL
            w.bounds = monitor(w)
            fs.value = true
        } else {
            fs.value = false
            if (fsWasMax) w.bounds = workArea(w) else savedBounds?.let { w.bounds = it }
        }
        w.toFront(); w.requestFocus()
    }

    fun toggleFullscreen() = setFullscreen(!isFullscreen)

    fun toggleMaximize() {
        val w = window ?: return
        if (fs.value) { setFullscreen(false); return }
        if (max.value) {
            max.value = false
            savedBounds?.let { w.bounds = it }
        } else {
            savedBounds = w.bounds
            max.value = true
            w.bounds = workArea(w)
        }
    }

    fun minimize() { window?.extendedState = java.awt.Frame.ICONIFIED }
}

// ---------------------------------------------------------------- keyboard

/** Keyboard like a TV remote: arrows move the yellow focus, Enter = OK, Esc = back, F11 = full screen. */
object KeyRouter {
    var focusManager: FocusManager? = null

    fun handle(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        return when (e.key) {
            Key.Escape -> BackDispatcher.back() || run {
                if (WindowCtl.isFullscreen) { WindowCtl.setFullscreen(false); true } else false
            }
            Key.F11 -> { WindowCtl.toggleFullscreen(); true }
            Key.DirectionUp -> focusManager?.moveFocus(FocusDirection.Up) ?: false
            Key.DirectionDown -> focusManager?.moveFocus(FocusDirection.Down) ?: false
            Key.DirectionLeft -> focusManager?.moveFocus(FocusDirection.Left) ?: false
            Key.DirectionRight -> focusManager?.moveFocus(FocusDirection.Right) ?: false
            else -> false
        }
    }

    /** Keys that reach the window when nothing has focus yet. */
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    fun onWindowKey(e: KeyEvent): Boolean {
        if (e.type == KeyEventType.KeyDown && e.key in setOf(Key.DirectionDown, Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight)) {
            val fm = focusManager ?: return false
            if (fm.moveFocus(FocusDirection.Enter)) return true
            return fm.moveFocus(FocusDirection.Next)
        }
        return handle(e)
    }
}

// ---------------------------------------------------------------- toast

object Toaster {
    val message = MutableStateFlow<Pair<String, Long>?>(null)
    fun show(text: String) {
        message.value = text to System.nanoTime()
    }
}

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val msg by Toaster.message.collectAsState()
    LaunchedEffect(msg) {
        if (msg != null) {
            delay(2_500)
            Toaster.message.value = null
        }
    }
    msg?.let { (text, _) ->
        Box(
            modifier
                .padding(bottom = 32.dp)
                .background(Color(0xE6222833), RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(text, color = Color.White, fontSize = 14.sp)
        }
    }
}
