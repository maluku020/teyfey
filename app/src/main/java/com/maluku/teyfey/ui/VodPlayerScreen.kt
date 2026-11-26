package com.maluku.teyfey.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.maluku.teyfey.App
import com.maluku.teyfey.data.VodKind
import com.maluku.teyfey.player.Playable
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(play: VodPlay, onIndex: (Int) -> Unit, onBack: () -> Unit) {
    val app = App.instance
    val pb = app.playback
    val vod = app.vod
    val entry = play.entries[play.index]
    val next = play.entries.getOrNull(play.index + 1)

    val casting by pb.casting.collectAsStateWithLifecycle()
    val device by pb.castDevice.collectAsStateWithLifecycle()
    val error by pb.error.collectAsStateWithLifecycle()
    val ended by pb.ended.collectAsStateWithLifecycle()
    var controls by remember { mutableStateOf(false) }
    // Title + buttons: shown for the first seconds, when the controls are open, or near the end.
    var intro by remember(entry.key) { mutableStateOf(true) }
    var nearEnd by remember(entry.key) { mutableStateOf(false) }
    LaunchedEffect(entry.key) {
        delay(4_000)
        intro = false
    }
    LaunchedEffect(entry.key, next) {
        while (next != null) {
            val p = pb.activePlayer
            val d = p.duration
            nearEnd = d > 0 && d - p.currentPosition in 1L..90_000L
            delay(1_000)
        }
    }

    fun savePos() {
        val p = pb.activePlayer
        val d = p.duration
        if (d > 0) vod.savePosition(entry.key, p.currentPosition, d)
    }

    // Start (or switch to) the chosen film / episode.
    LaunchedEffect(entry.key) {
        val repo = app.repo
        pb.applyLanguages(repo.audioLang, repo.subLang.takeIf { it != "off" })
        pb.playVod(Playable(entry.key, entry.url, entry.title, play.image, live = false), vod.position(entry.key))
        pb.setMuted(false)
        vod.markRecent(if (play.isSeries) VodKind.SERIES else VodKind.MOVIES, play.itemId)
        if (play.isSeries) vod.setLastEpisode(play.itemId, entry.key.removePrefix("e:"))
    }
    // Remember where you are, every few seconds.
    LaunchedEffect(entry.key) {
        while (true) {
            delay(5_000)
            savePos()
        }
    }
    // Auto-play the next episode.
    val startEnded = remember(entry.key) { ended }
    LaunchedEffect(ended) {
        if (ended > startEnded) {
            val d = pb.activePlayer.duration
            if (d > 0) vod.savePosition(entry.key, d, d)
            if (next != null) onIndex(play.index + 1) else onBack()
        }
    }

    // Full screen + landscape
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(Unit) {
        val ctrl = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        val old = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            savePos()
            pb.stop()
            ctrl?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = old ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val player = if (casting && pb.castPlayer != null) pb.castPlayer else pb.exo
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    controllerShowTimeoutMs = 4_000
                    controllerAutoShow = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowSubtitleButton(true)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { v -> controls = v == android.view.View.VISIBLE }
                    )
                    this.player = player
                    post { requestFocus() }
                }
            },
            update = { if (it.player !== player) it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        val showInfo = intro || controls || casting
        if (showInfo) {
            Column(
                Modifier.fillMaxWidth().align(Alignment.TopStart)
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 30.dp)
            ) {
                Text(entry.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                entry.subtitle?.let { Text(it, color = Z.textDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (casting) Text("Speelt op ${device ?: "Chromecast"}", color = Z.accent, fontSize = 13.sp)
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            if (next != null && (showInfo || nearEnd)) {
                Button(
                    onClick = { savePos(); onIndex(play.index + 1) },
                    modifier = Modifier.focusRing(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xCC000000)),
                ) {
                    Text("Volgende aflevering", color = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.SkipNext, null, tint = Color.White)
                }
            }
            if (showInfo && pb.castSupported) CastButton()
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
