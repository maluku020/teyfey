package com.maluku.teyfey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import coil3.compose.AsyncImage
import com.maluku.teyfey.App
import com.maluku.teyfey.data.Episode
import com.maluku.teyfey.data.MovieInfo
import com.maluku.teyfey.data.SeriesInfo
import com.maluku.teyfey.data.VodItem

/** What the VOD player should play: one film, or a run of episodes. */
data class VodEntry(val key: String, val url: String, val title: String, val subtitle: String?)

data class VodPlay(
    val isSeries: Boolean,
    val itemId: String,
    val image: String?,
    val entries: List<VodEntry>,
    val index: Int,
)

private sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Fail(val msg: String) : Load<Nothing>
}

fun formatMs(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

// ------------------------------------------------------------------ films

@Composable
fun MovieDetailScreen(item: VodItem, onPlay: (VodPlay) -> Unit, onBack: () -> Unit) {
    val vod = App.instance.vod
    BackHandler { onBack() }
    val tick by vod.progressTick.collectAsState()
    val state by produceState<Load<MovieInfo>>(Load.Loading, item.id) {
        value = try {
            Load.Ok(vod.movieInfo(item.id))
        } catch (e: Throwable) {
            Load.Fail(e.message ?: "Info laden mislukt")
        }
    }
    @Suppress("UNCHECKED_CAST")
    val info = (state as? Load.Ok<MovieInfo>)?.value
    val key = "m:${item.id}"
    val resume = remember(tick) { vod.position(key) }

    fun play(fromStart: Boolean) {
        val entry = VodEntry(key, vod.movieUrl(item), item.name, info?.year)
        if (fromStart) vod.savePosition(key, 0, 1)
        onPlay(VodPlay(false, item.id, info?.poster ?: item.image, listOf(entry), 0))
    }

    DetailFrame(backdrop = info?.backdrop ?: info?.poster ?: item.image, onBack = onBack) {
        LazyColumn(contentPadding = PaddingValues(24.dp)) {
            item {
                DetailHeader(
                    poster = info?.poster ?: item.image,
                    title = item.name,
                    meta = listOfNotNull(
                        info?.year ?: item.year,
                        info?.duration?.takeIf { it.isNotBlank() },
                        (info?.rating ?: item.rating)?.let { "★ ${it.take(3)}" },
                        info?.genre,
                    ).joinToString("  ·  "),
                    loading = state is Load.Loading,
                ) {
                    PlayButtons(
                        resumeLabel = if (resume > 0) "Verder kijken (${formatMs(resume)})" else null,
                        onPlay = { play(false) },
                        onRestart = { play(true) },
                    )
                }
            }
            item {
                Column(Modifier.widthIn(max = 820.dp).padding(top = 20.dp)) {
                    (state as? Load.Fail)?.let { Text(it.msg, color = Z.live) }
                    info?.plot?.let { Text(it, color = Z.text, fontSize = 15.sp, lineHeight = 22.sp) }
                    info?.cast?.let { InfoLine("Cast", it) }
                    info?.director?.let { InfoLine("Regie", it) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ series

@Composable
fun SeriesDetailScreen(item: VodItem, onPlay: (VodPlay) -> Unit, onBack: () -> Unit) {
    val vod = App.instance.vod
    BackHandler { onBack() }
    val tick by vod.progressTick.collectAsState()
    val state by produceState<Load<SeriesInfo>>(Load.Loading, item.id) {
        value = try {
            Load.Ok(vod.seriesInfo(item.id))
        } catch (e: Throwable) {
            Load.Fail(e.message ?: "Info laden mislukt")
        }
    }
    @Suppress("UNCHECKED_CAST")
    val info = (state as? Load.Ok<SeriesInfo>)?.value
    val seasons = info?.seasons.orEmpty()
    val lastEpId = remember(tick) { vod.lastEpisode(item.id) }
    val lastEp = remember(info, lastEpId) { seasons.values.flatten().firstOrNull { it.id == lastEpId } }

    var season by remember(info) { mutableIntStateOf(lastEp?.season ?: seasons.keys.firstOrNull() ?: 1) }
    val episodes = seasons[season].orEmpty()

    fun playEpisode(list: List<Episode>, index: Int, fromStart: Boolean = false) {
        val entries = list.map { e ->
            VodEntry("e:${e.id}", vod.episodeUrl(e), item.name, "S${e.season} A${e.number} · ${e.title}")
        }
        if (fromStart) vod.savePosition(entries[index].key, 0, 1)
        onPlay(VodPlay(true, item.id, info?.poster ?: item.image, entries, index))
    }

    DetailFrame(backdrop = info?.backdrop ?: info?.poster ?: item.image, onBack = onBack) {
        LazyColumn(contentPadding = PaddingValues(24.dp)) {
            item {
                DetailHeader(
                    poster = info?.poster ?: item.image,
                    title = item.name,
                    meta = listOfNotNull(
                        info?.year ?: item.year,
                        seasons.size.takeIf { it > 0 }?.let { if (it == 1) "1 seizoen" else "$it seizoenen" },
                        (info?.rating ?: item.rating)?.let { "★ ${it.take(3)}" },
                        info?.genre,
                    ).joinToString("  ·  "),
                    loading = state is Load.Loading,
                ) {
                    if (seasons.isNotEmpty()) {
                        val target = lastEp ?: seasons.values.first().first()
                        val list = seasons[target.season].orEmpty()
                        val idx = list.indexOfFirst { it.id == target.id }.coerceAtLeast(0)
                        val label = if (lastEp != null) "Verder kijken · S${target.season} A${target.number}"
                        else "Afspelen · S${target.season} A${target.number}"
                        PlayButtons(resumeLabel = null, mainLabel = label, onPlay = { playEpisode(list, idx) }, onRestart = null)
                    }
                }
            }
            item {
                Column(Modifier.widthIn(max = 820.dp).padding(top = 18.dp)) {
                    (state as? Load.Fail)?.let { Text(it.msg, color = Z.live) }
                    info?.plot?.let { Text(it, color = Z.text, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                    info?.cast?.let { InfoLine("Cast", it) }
                }
            }
            if (seasons.size > 1) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                    ) {
                        items(seasons.keys.toList()) { s ->
                            val sel = s == season
                            Box(
                                Modifier
                                    .tvClickable(selected = sel, shape = RoundedCornerShape(50)) { season = s }
                                    .background(if (sel) Color.Transparent else Z.surface, RoundedCornerShape(50))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Seizoen $s", color = if (sel) Z.text else Z.textDim, fontSize = 14.sp)
                            }
                        }
                    }
                }
            } else {
                item { Spacer(Modifier.height(16.dp)) }
            }
            itemsIndexed(episodes, key = { _, e -> e.id }) { i, e ->
                EpisodeRow(
                    ep = e,
                    progress = vod.progress("e:${e.id}"),
                    watched = vod.watched("e:${e.id}"),
                    isLast = e.id == lastEpId,
                ) { playEpisode(episodes, i) }
            }
        }
    }
}

// ------------------------------------------------------------------ shared pieces

@Composable
private fun DetailFrame(backdrop: String?, onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Z.bg)) {
        if (backdrop != null) {
            AsyncImage(
                model = backdrop, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(420.dp),
            )
        }
        Box(
            Modifier.fillMaxWidth().height(422.dp).background(
                Brush.verticalGradient(listOf(Z.bg.copy(alpha = 0.55f), Z.bg.copy(alpha = 0.85f), Z.bg))
            )
        )
        Column(Modifier.fillMaxSize()) {
            IconButton(onClick = onBack, modifier = Modifier.padding(4.dp).tvIconFocus()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug", tint = Color.White)
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
private fun DetailHeader(
    poster: String?,
    title: String,
    meta: String,
    loading: Boolean,
    buttons: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.width(150.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Z.surface)
        ) {
            if (poster != null) {
                AsyncImage(poster, title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
            Spacer(Modifier.height(6.dp))
            if (meta.isNotBlank()) Text(meta, color = Z.textDim, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            if (loading) CircularProgressIndicator(Modifier.size(26.dp), color = Z.accent, strokeWidth = 3.dp)
            else buttons()
        }
    }
}

@Composable
private fun PlayButtons(
    resumeLabel: String?,
    onPlay: () -> Unit,
    onRestart: (() -> Unit)?,
    mainLabel: String? = null,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onPlay,
            colors = ButtonDefaults.buttonColors(containerColor = Z.accent),
            modifier = Modifier.focusRequester(focus).focusRing(),
        ) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(6.dp))
            Text(mainLabel ?: resumeLabel ?: "Afspelen")
        }
        if (resumeLabel != null && onRestart != null) {
            OutlinedButton(onClick = onRestart, modifier = Modifier.focusRing()) {
                Icon(Icons.Filled.Replay, null, tint = Z.text)
                Spacer(Modifier.width(6.dp))
                Text("Vanaf begin", color = Z.text)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(top = 10.dp)) {
        Text("$label  ", color = Z.textDim, fontSize = 13.sp)
        Text(value, color = Z.text, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EpisodeRow(ep: Episode, progress: Float?, watched: Boolean, isLast: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .tvClickable(selected = isLast, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Z.surface)) {
            if (ep.image != null) {
                AsyncImage(ep.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Icon(
                Icons.Filled.PlayArrow, null, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(30.dp)
                    .background(Color(0x88000000), RoundedCornerShape(50)).padding(3.dp),
            )
            if (progress != null) ProgressLine(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth(), 3.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${ep.number}. ${ep.title}", color = Z.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (watched) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.CheckCircle, "Gezien", tint = Z.accent, modifier = Modifier.size(16.dp))
                }
            }
            ep.duration?.let { Text(it, color = Z.textDim, fontSize = 12.sp) }
            ep.plot?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, color = Z.textDim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
