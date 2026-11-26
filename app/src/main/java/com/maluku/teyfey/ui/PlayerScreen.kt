package com.maluku.teyfey.ui

import android.content.pm.ActivityInfo
import android.view.KeyEvent as AKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maluku.teyfey.App
import com.maluku.teyfey.data.liveAt
import com.maluku.teyfey.data.nextAfter
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(now: Long, group: String, onBack: () -> Unit) {
    val app = App.instance
    val repo = app.repo
    val pb = app.playback

    val all by repo.channels.collectAsStateWithLifecycle()
    val favs by repo.favorites.collectAsStateWithLifecycle()
    val epg by repo.epg.collectAsStateWithLifecycle()
    val current by pb.current.collectAsStateWithLifecycle()
    val casting by pb.casting.collectAsStateWithLifecycle()
    val device by pb.castDevice.collectAsStateWithLifecycle()
    val error by pb.error.collectAsStateWithLifecycle()

    // Zap within the group the channel was chosen from (fallback: all channels).
    val zapList = remember(all, group, favs, current?.id) {
        val g = filterGroup(all, group, favs)
        if (current != null && g.any { it.id == current?.id }) g else all
    }

    var overlay by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    fun showOverlay() {
        overlay = true
        overlayTick++
    }
    fun toggleOverlay() {
        if (overlay) overlay = false else showOverlay()
    }
    fun handleBack() {
        if (overlay) overlay = false else onBack()
    }
    LaunchedEffect(overlayTick, overlay) {
        if (overlay) {
            delay(5_000)
            overlay = false
        }
    }

    fun zap(step: Int) {
        if (zapList.isEmpty()) return
        val idx = zapList.indexOfFirst { it.id == current?.id }
        val next = zapList[((if (idx < 0) 0 else idx + step) % zapList.size + zapList.size) % zapList.size]
        pb.play(next)
        repo.lastChannelId = next.id
        showOverlay()
    }

    // Immersive full screen + landscape while watching.
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(Unit) {
        val window = activity?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        val oldOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            ctrl?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = oldOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler { handleBack() }
    // Sound on/off in full screen: ◀ ▶ or the mute key on the remote, or tap the speaker icon
    var muted by remember { mutableStateOf(false) }
    LaunchedEffect(muted) { pb.setMuted(muted) }
    DisposableEffect(Unit) { onDispose { pb.setMuted(App.instance.repo.previewMuted.value) } }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    var drag by remember { mutableStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key.nativeKeyCode) {
                    AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); true }
                    AKeyEvent.KEYCODE_DPAD_DOWN, AKeyEvent.KEYCODE_CHANNEL_UP -> { zap(1); true }
                    AKeyEvent.KEYCODE_DPAD_LEFT, AKeyEvent.KEYCODE_DPAD_RIGHT, AKeyEvent.KEYCODE_MUTE,
                    AKeyEvent.KEYCODE_VOLUME_MUTE -> {
                        muted = !muted
                        android.widget.Toast.makeText(view.context, if (muted) "Geluid uit" else "Geluid aan", android.widget.Toast.LENGTH_SHORT).show()
                        true
                    }
                    AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER, AKeyEvent.KEYCODE_NUMPAD_ENTER,
                    AKeyEvent.KEYCODE_INFO -> {
                        toggleOverlay()
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { toggleOverlay() })
            }
            .pointerInput(zapList, current?.id) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (drag < -120f) zap(1) else if (drag > 120f) zap(-1)
                        drag = 0f
                    },
                    onVerticalDrag = { _, dy -> drag += dy },
                )
            }
    ) {
        if (casting) CastingCard(device, current, Modifier.fillMaxSize())
        else PlayerSurface(pb.exo, Modifier.fillMaxSize())

        error?.let {
            Text(
                it, color = Color.White, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0xAA000000), RoundedCornerShape(8.dp)).padding(14.dp),
            )
        }

        AnimatedVisibility(
            visible = overlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val ch = current
            val progs = ch?.let { epg[it.id] }
            val live = progs?.liveAt(now)
            val next = progs?.nextAfter(now)
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))
                ).padding(start = 28.dp, end = 28.dp, top = 60.dp, bottom = 24.dp)
            ) {
                if (ch != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(ch.logo, ch.name, 56.dp)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${ch.number}  ${ch.name}", color = Z.textDim, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            live?.title ?: ch.name, color = Color.White, fontSize = 22.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (live != null) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(range(live), color = Z.textDim, fontSize = 13.sp)
                                Spacer(Modifier.width(12.dp))
                                ProgressLine(live.progress(now), Modifier.weight(1f), 4.dp)
                            }
                        }
                        if (next != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Straks ${timeOf(next.start)}  ·  ${next.title}", color = Z.textDim,
                                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(timeOf(now), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("▲▼ zappen  ·  ◀▶ geluid", color = Z.textDim, fontSize = 11.sp)
                    }
                }
            }
        }

        // Speaker icon: always visible while muted, and while the info bar is shown (tap = toggle)
        if (muted || overlay) {
            androidx.compose.material3.Icon(
                if (muted) androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeOff
                else androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeUp,
                if (muted) "Geluid aan" else "Geluid uit",
                tint = if (muted) Z.fav else Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    .background(Color(0x88000000), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) { detectTapGestures(onTap = { muted = !muted }) }
                    .padding(8.dp).size(32.dp),
            )
        }

        if (overlay && pb.castSupported) {
            CastButton(Modifier.align(Alignment.TopEnd).padding(12.dp).size(48.dp))
        }
    }
}
