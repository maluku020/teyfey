package com.maluku.teyfey.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maluku.teyfey.App
import com.maluku.teyfey.data.Channel
import com.maluku.teyfey.data.VodItem
import com.maluku.teyfey.data.VodKind
import kotlinx.coroutines.delay

enum class Screen { Setup, Home, Guide, Player, Settings, Movies, Series, MovieDetail, SeriesDetail, VodPlayer, Catchup, Gemist }

const val GROUP_FAV = "★ Favorieten"
const val GROUP_ALL = "Alle kanalen"

fun filterGroup(all: List<Channel>, group: String, favs: Set<String>): List<Channel> = when (group) {
    GROUP_ALL -> all
    GROUP_FAV -> all.filter { it.id in favs }
    else -> if (group.startsWith(FOLDER_KEY)) {
        val code = group.removePrefix(FOLDER_KEY)
        all.filter { countryCode(it.group) == code }
    } else {
        all.filter { it.group == group }
    }
}

@Composable
fun AppRoot() {
    val app = App.instance
    val repo = app.repo
    val config by repo.config.collectAsStateWithLifecycle()

    var screen by rememberSaveable { mutableStateOf(if (config == null) Screen.Setup else Screen.Home) }
    var group by rememberSaveable { mutableStateOf(repo.lastGroup ?: GROUP_ALL) }

    // Global clock for "now playing" + progress bars.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(20_000)
        }
    }

    LaunchedEffect(config) {
        if (config != null) runCatching { repo.start() }
    }

    val setGroup: (String) -> Unit = { group = it; repo.lastGroup = it }

    // Films & series state
    var vodItem by remember { mutableStateOf<VodItem?>(null) }
    var vodPlay by remember { mutableStateOf<VodPlay?>(null) }
    var vodFrom by remember { mutableStateOf(Screen.Movies) }
    var catchupChannel by remember { mutableStateOf<Channel?>(null) }
    var catchupFrom by remember { mutableStateOf(Screen.Home) }
    val goSection: (Section) -> Unit = { s ->
        screen = when (s) {
            Section.LIVE -> Screen.Home
            Section.GEMIST -> Screen.Gemist
            Section.MOVIES -> Screen.Movies
            Section.SERIES -> Screen.Series
        }
    }

    val fullscreen = screen == Screen.Player || screen == Screen.VodPlayer
    Box(
        Modifier
            .fillMaxSize()
            .background(if (fullscreen) androidx.compose.ui.graphics.Color.Black else Z.bg)
            .padding(tvSafePadding())
    ) {
        when (screen) {
            Screen.Setup -> SetupScreen(
                existing = config,
                onDone = { screen = Screen.Home },
                onCancel = if (config != null) ({ screen = Screen.Settings }) else null,
            )
            Screen.Home -> HomeScreen(
                now = now,
                group = group,
                onGroup = setGroup,
                onFullscreen = { screen = Screen.Player },
                onGuide = { screen = Screen.Guide },
                onSettings = { screen = Screen.Settings },
                onSection = goSection,
                onCatchup = { catchupChannel = it; catchupFrom = Screen.Home; screen = Screen.Catchup },
            )
            Screen.Gemist -> GemistScreen(
                now = now,
                onSection = goSection,
                onOpen = { catchupChannel = it; catchupFrom = Screen.Gemist; screen = Screen.Catchup },
                onSettings = { screen = Screen.Settings },
            )
            Screen.Catchup -> catchupChannel?.let { ch ->
                CatchupScreen(
                    channel = ch,
                    onPlay = { vodPlay = it; vodFrom = Screen.Catchup; screen = Screen.VodPlayer },
                    onBack = { screen = catchupFrom },
                )
            } ?: LaunchedEffect(Unit) { screen = catchupFrom }
            Screen.Movies -> VodBrowseScreen(
                kind = VodKind.MOVIES,
                onSection = goSection,
                onOpen = { vodItem = it; screen = Screen.MovieDetail },
                onSettings = { screen = Screen.Settings },
            )
            Screen.Series -> VodBrowseScreen(
                kind = VodKind.SERIES,
                onSection = goSection,
                onOpen = { vodItem = it; screen = Screen.SeriesDetail },
                onSettings = { screen = Screen.Settings },
            )
            Screen.MovieDetail -> vodItem?.let { item ->
                MovieDetailScreen(
                    item = item,
                    onPlay = { vodPlay = it; vodFrom = Screen.MovieDetail; screen = Screen.VodPlayer },
                    onBack = { screen = Screen.Movies },
                )
            } ?: LaunchedEffect(Unit) { screen = Screen.Movies }
            Screen.SeriesDetail -> vodItem?.let { item ->
                SeriesDetailScreen(
                    item = item,
                    onPlay = { vodPlay = it; vodFrom = Screen.SeriesDetail; screen = Screen.VodPlayer },
                    onBack = { screen = Screen.Series },
                )
            } ?: LaunchedEffect(Unit) { screen = Screen.Series }
            Screen.VodPlayer -> vodPlay?.let { play ->
                VodPlayerScreen(
                    play = play,
                    onIndex = { i -> vodPlay = play.copy(index = i) },
                    onBack = { screen = vodFrom },
                )
            } ?: LaunchedEffect(Unit) { screen = vodFrom }
            Screen.Guide -> GuideScreen(
                now = now,
                group = group,
                onGroup = setGroup,
                onWatch = { screen = Screen.Player },
                onBack = { screen = Screen.Home },
            )
            Screen.Player -> PlayerScreen(
                now = now,
                group = group,
                onBack = { screen = Screen.Home },
            )
            Screen.Settings -> SettingsScreen(
                onBack = { screen = Screen.Home },
                onChangeSource = { screen = Screen.Setup },
            )
        }
    }

    BackHandler(enabled = screen == Screen.Guide || screen == Screen.Settings || screen == Screen.Movies || screen == Screen.Series || screen == Screen.Gemist) {
        screen = Screen.Home
    }
}
