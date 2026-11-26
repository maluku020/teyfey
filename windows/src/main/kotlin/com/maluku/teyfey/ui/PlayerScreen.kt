package com.maluku.teyfey.ui

import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.maluku.teyfey.App
import com.maluku.teyfey.data.liveAt
import com.maluku.teyfey.data.nextAfter
import kotlinx.coroutines.delay

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(now: Long, group: String, onBack: () -> Unit) {
    val app = App.instance
    val repo = app.repo
    val pb = app.playback

    val all by repo.channels.collectAsState()
    val favs by repo.favorites.collectAsState()
    val epg by repo.epg.collectAsState()
    val current by pb.current.collectAsState()
    val casting by pb.casting.collectAsState()
    val device by pb.castDevice.collectAsState()
    val error by pb.error.collectAsState()

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

    // Full screen while watching (F11 or double-click toggles).
    DisposableEffect(Unit) {
        val was = WindowCtl.isFullscreen
        WindowCtl.setFullscreen(true)
        onDispose { if (!was) WindowCtl.setFullscreen(false) }
    }

    // Esc: first leave full screen (window stays open), then back to the list
    // Esc = straight back to the menu (the window becomes small again when the player closes)
    BackHandler { onBack() }
    // Leaving full screen in another way (F11, F, double-click) also goes back to the menu
    LaunchedEffect(Unit) {
        var seenFull = false
        androidx.compose.runtime.snapshotFlow { WindowCtl.isFullscreen }.collect { full ->
            if (full) seenFull = true else if (seenFull) onBack()
        }
    }
    // Sound on/off in full screen: M, or ◀ ▶ to select the speaker + Enter, or click the speaker
    var muted by remember { mutableStateOf(false) }
    LaunchedEffect(muted) { pb.setMuted(muted) }
    DisposableEffect(Unit) { onDispose { pb.setMuted(App.instance.repo.previewMuted.value) } }
    var speakerSel by remember { mutableStateOf(false) }
    LaunchedEffect(overlay) { if (!overlay) speakerSel = false }
    fun toggleMute() {
        muted = !muted
        Toaster.show(if (muted) "Geluid uit" else "Geluid aan")
    }
    DisposableEffect(Unit) {
        PlayerMute.onToggle = { toggleMute(); showOverlay() }
        onDispose { PlayerMute.onToggle = null }
    }

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
                when (e.key) {
                    Key.DirectionUp, Key.PageUp -> { zap(-1); true }
                    Key.DirectionDown, Key.PageDown -> { zap(1); true }
                    Key.DirectionLeft, Key.DirectionRight -> {
                        speakerSel = if (overlay) !speakerSel else true
                        showOverlay()
                        true
                    }
                    Key.M -> { toggleMute(); true }
                    Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.I -> {
                        if (overlay && speakerSel) { toggleMute(); showOverlay() } else toggleOverlay()
                        true
                    }
                    Key.F -> { WindowCtl.toggleFullscreen(); true }
                    else -> false
                }
            }
            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move) { showOverlay() }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { toggleOverlay() }, onDoubleTap = { WindowCtl.toggleFullscreen() })
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
        else PlayerSurface(Modifier.fillMaxSize())

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
                        Text("▲▼ zappen · F volledig scherm", color = Z.textDim, fontSize = 11.sp)
                    }
                }
            }
        }

        if (muted || overlay) {
            Icon(
                if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                if (muted) "Geluid aan" else "Geluid uit",
                tint = if (muted) Z.fav else Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    .then(
                        if (speakerSel && overlay) Modifier
                            .border(4.dp, Z.focus, RoundedCornerShape(10.dp))
                            .background(Z.focus.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        else Modifier.background(Color(0x88000000), RoundedCornerShape(8.dp))
                    )
                    .clickable { toggleMute(); showOverlay() }
                    .padding(8.dp).size(if (speakerSel && overlay) 40.dp else 32.dp),
            )
            if (speakerSel && overlay) Text(
                if (muted) "Enter = geluid aan" else "Enter = geluid uit",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 92.dp, top = 30.dp)
                    .background(Color(0x88000000), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        if (overlay && pb.castSupported) {
            CastButton(Modifier.align(Alignment.TopEnd).padding(12.dp).size(48.dp))
        }
    }
}
