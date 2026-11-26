package com.maluku.teyfey

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.maluku.teyfey.ui.AppRoot
import com.maluku.teyfey.ui.KeyRouter
import com.maluku.teyfey.ui.TeyfeyTheme
import com.maluku.teyfey.ui.WindowCtl
import com.maluku.teyfey.ui.Z
import java.awt.Cursor

fun main() = application {
    val state = rememberWindowState(width = 1320.dp, height = 820.dp, position = WindowPosition(Alignment.Center))
    WindowCtl.state = state
    val close = {
        App.instance.playback.release()
        exitApplication()
    }
    Window(
        onCloseRequest = close,
        state = state,
        title = "Teyfey",
        icon = painterResource("icon.png"),
        undecorated = true,          // own title bar: lets full screen work like a normal window (2nd monitor stays usable)
        resizable = true,
        onKeyEvent = { KeyRouter.onWindowKey(it) },
    ) {
        WindowCtl.window = window
        window.minimumSize = java.awt.Dimension(800, 500)
        TeyfeyTheme {
            Column(Modifier.fillMaxSize().background(Z.bg)) {
                if (!WindowCtl.isFullscreen) TitleBar(close)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AppRoot()
                    if (!WindowCtl.isFullscreen && !WindowCtl.isMaximized) ResizeGrip(Modifier.align(Alignment.BottomEnd))
                }
            }
        }
    }
}

@Composable
private fun FrameWindowScope.TitleBar(onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(34.dp).background(Z.panel),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drag area: moves the window with the real mouse position, so it also works across monitors
        Row(
            Modifier.weight(1f).height(34.dp).pointerInput(Unit) {
                var startMouse = java.awt.Point()
                var startWin = java.awt.Point()
                detectDragGestures(
                    onDragStart = {
                        val w = WindowCtl.window
                        if (w != null && WindowCtl.isMaximized) {
                            // Dragging a maximized window: restore it under the mouse first
                            WindowCtl.toggleMaximize()
                            val m = java.awt.MouseInfo.getPointerInfo().location
                            w.setLocation(m.x - w.width / 2, m.y - 17)
                        }
                        startMouse = java.awt.MouseInfo.getPointerInfo().location
                        startWin = WindowCtl.window?.location ?: java.awt.Point()
                    },
                ) { change, _ ->
                    change.consume()
                    val m = java.awt.MouseInfo.getPointerInfo().location
                    WindowCtl.window?.setLocation(startWin.x + (m.x - startMouse.x), startWin.y + (m.y - startMouse.y))
                }
            }.pointerInput(Unit) { detectTapGestures(onDoubleTap = { WindowCtl.toggleMaximize() }) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(10.dp))
            Image(painterResource("icon.png"), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Teyfey", color = Z.textDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        BarButton("—", "Minimaliseren") { WindowCtl.minimize() }
        BarButton(if (WindowCtl.isMaximized) "❐" else "☐", "Maximaliseren") { WindowCtl.toggleMaximize() }
        BarButton("✕", "Sluiten", danger = true, onClick = onClose)
    }
}

@Composable
private fun BarButton(label: String, @Suppress("UNUSED_PARAMETER") hint: String, danger: Boolean = false, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Box(
        Modifier.width(46.dp).height(34.dp)
            .background(if (hovered) (if (danger) Color(0xFFE81123) else Z.surfaceHi) else Color.Transparent)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Z.text, fontSize = 14.sp)
    }
}

/** Drag the bottom-right corner to resize (the window has no Windows border). */
@Composable
private fun ResizeGrip(modifier: Modifier) {
    val density = LocalDensity.current.density
    Box(
        modifier.size(18.dp)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val w = WindowCtl.window ?: return@detectDragGestures
                    val nw = (w.width + drag.x / density).toInt().coerceAtLeast(w.minimumSize.width)
                    val nh = (w.height + drag.y / density).toInt().coerceAtLeast(w.minimumSize.height)
                    w.setSize(nw, nh)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("◢", color = Z.textDim, fontSize = 11.sp)
    }
}
