package com.maluku.teyfey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maluku.teyfey.App
import com.maluku.teyfey.data.VodKind
import com.maluku.teyfey.player.Playable
import com.maluku.teyfey.player.Track
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VodPlayerScreen(play: VodPlay, onIndex: (Int) -> Unit, onBack: () -> Unit) {
    val app = App.instance
    val pb = app.playback
    val vod = app.vod
    val repo = app.repo
    val entry = play.entries[play.index]
    val next = play.entries.getOrNull(play.index + 1)

    val error by pb.error.collectAsState()
    val ended by pb.ended.collectAsState()
    val playing by pb.playing.collectAsState()
    val buffering by pb.buffering.collectAsState()

    // Controls: visible at the start, on mouse move / key press; hide after 3 s.
    var controlsTick by remember { mutableIntStateOf(0) }
    var controls by remember { mutableStateOf(true) }
    fun poke() {
        controls = true
        controlsTick++
    }
    LaunchedEffect(controlsTick) {
        delay(3_500)
        controls = false
    }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var nearEnd by remember(entry.key) { mutableStateOf(false) }

    fun savePos() {
        val d = pb.duration()
        if (d > 0) vod.savePosition(entry.key, pb.position(), d)
    }

    LaunchedEffect(entry.key) {
        pb.playVod(
            Playable(entry.key, entry.url, entry.title, play.image, live = false),
            startMs = vod.position(entry.key),
            audioLang = repo.audioLang,
            subLang = repo.subLang.takeIf { it != "off" },
        )
        pb.setMuted(false)
        vod.markRecent(if (play.isSeries) VodKind.SERIES else VodKind.MOVIES, play.itemId)
        if (play.isSeries) vod.setLastEpisode(play.itemId, entry.key.removePrefix("e:"))
        poke()
    }
    // Progress + save position.
    LaunchedEffect(entry.key) {
        var n = 0
        while (true) {
            delay(500)
            if (!seeking) position = pb.position()
            duration = pb.duration()
            nearEnd = next != null && duration > 0 && duration - position in 1L..90_000L
            if (++n % 10 == 0) savePos()
        }
    }
    // Next episode automatically.
    val startEnded = remember(entry.key) { ended }
    LaunchedEffect(ended) {
        if (ended > startEnded) {
            val d = pb.duration()
            if (d > 0) vod.savePosition(entry.key, d, d)
            if (next != null) onIndex(play.index + 1) else onBack()
        }
    }

    DisposableEffect(Unit) {
        val was = WindowCtl.isFullscreen
        WindowCtl.setFullscreen(true)
        onDispose {
            savePos()
            pb.stop()
            if (!was) WindowCtl.setFullscreen(false)
        }
    }

    BackHandler { onBack() }   // Esc = straight back (position is saved)

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                poke()
                when (e.key) {
                    Key.Spacebar, Key.K -> { pb.togglePause(); true }
                    Key.DirectionLeft, Key.J -> { pb.skip(-10_000); true }
                    Key.DirectionRight, Key.L -> { pb.skip(10_000); true }
                    Key.F -> { WindowCtl.toggleFullscreen(); true }
                    Key.N -> { if (next != null) { savePos(); onIndex(play.index + 1) }; true }
                    Key.DirectionUp -> { pb.setVolume(pb.volume() + 10); Toaster.show("Volume ${pb.volume()}%"); true }
                    Key.DirectionDown -> { pb.setVolume(pb.volume() - 10); Toaster.show("Volume ${pb.volume()}%"); true }
                    else -> false
                }
            }
            .onPointerEvent(PointerEventType.Move) { poke() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { poke(); pb.togglePause() },
                    onDoubleTap = { WindowCtl.toggleFullscreen() },
                )
            }
    ) {
        PlayerSurface(Modifier.fillMaxSize())

        if (buffering && !playing) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Z.focus)
        }

        // ---------- top: title
        if (controls) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopStart)
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .padding(start = 12.dp, end = 24.dp, top = 12.dp, bottom = 30.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.tvIconFocus()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    entry.subtitle?.let { Text(it, color = Z.textDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }

        // ---------- next episode
        if (next != null && (controls || nearEnd)) {
            Button(
                onClick = { savePos(); onIndex(play.index + 1) },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).focusRing(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xCC000000)),
            ) {
                Text("Volgende aflevering", color = Color.White)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.SkipNext, null, tint = Color.White)
            }
        }

        // ---------- bottom: controls
        if (controls) {
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatMs(if (seeking) seekValue.toLong() else position), color = Color.White, fontSize = 13.sp)
                    Slider(
                        value = if (seeking) seekValue else position.toFloat().coerceAtMost(duration.toFloat().coerceAtLeast(1f)),
                        onValueChange = { seeking = true; seekValue = it; poke() },
                        onValueChangeFinished = {
                            pb.seekTo(seekValue.toLong())
                            position = seekValue.toLong()
                            seeking = false
                        },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(thumbColor = Z.focus, activeTrackColor = Z.accent),
                        modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                    )
                    Text(formatMs(duration), color = Color.White, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pb.skip(-10_000); poke() }, modifier = Modifier.tvIconFocus()) {
                        Icon(Icons.Filled.Replay10, "10 s terug", tint = Color.White)
                    }
                    IconButton(onClick = { pb.togglePause(); poke() }, modifier = Modifier.tvIconFocus()) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Pauze", tint = Color.White)
                    }
                    IconButton(onClick = { pb.skip(10_000); poke() }, modifier = Modifier.tvIconFocus()) {
                        Icon(Icons.Filled.Forward10, "10 s vooruit", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    TrackMenu(Icons.Filled.Audiotrack, "Audio", { pb.audioTracks() }, { pb.audioTrack() }, { pb.setAudioTrack(it) }, ::poke)
                    TrackMenu(Icons.Filled.Subtitles, "Ondertitels", { pb.subtitleTracks() }, { pb.subtitleTrack() }, { pb.setSubtitleTrack(it) }, ::poke)
                    IconButton(onClick = { WindowCtl.toggleFullscreen(); poke() }, modifier = Modifier.tvIconFocus()) {
                        Icon(
                            if (WindowCtl.isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            "Volledig scherm", tint = Color.White,
                        )
                    }
                }
                Text(
                    "Spatie = pauze · ←/→ = 10 s · ↑/↓ = volume · F = volledig scherm · Esc = terug",
                    color = Z.textDim, fontSize = 11.sp,
                )
            }
        }

        error?.let {
            Text(
                it, color = Color.White, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0xAA000000), RoundedCornerShape(8.dp)).padding(14.dp),
            )
        }
    }
}

@Composable
private fun TrackMenu(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tracks: () -> List<Track>,
    current: () -> Int,
    select: (Int) -> Unit,
    poke: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var list by remember { mutableStateOf(emptyList<Track>()) }
    var cur by remember { mutableIntStateOf(-1) }
    Box {
        IconButton(
            onClick = { list = tracks(); cur = current(); open = true; poke() },
            modifier = Modifier.tvIconFocus(),
        ) { Icon(icon, label, tint = Color.White) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (list.isEmpty()) {
                DropdownMenuItem(text = { Text("Geen keuze beschikbaar") }, onClick = { open = false })
            }
            list.forEach { t ->
                DropdownMenuItem(
                    text = { Text((if (t.id == cur) "✓  " else "     ") + t.name) },
                    onClick = { select(t.id); open = false; poke() },
                )
            }
        }
    }
}
