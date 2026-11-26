package com.maluku.teyfey.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maluku.teyfey.App
import com.maluku.teyfey.data.Channel
import com.maluku.teyfey.data.Program
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Terugkijken: past programmes of one channel, grouped per day. */
@Composable
fun CatchupScreen(channel: Channel, onPlay: (VodPlay) -> Unit, onBack: () -> Unit) {
    val repo = App.instance.repo
    val tick by App.instance.vod.progressTick.collectAsState()
    BackHandler { onBack() }

    val state by produceState<Pair<List<Program>?, String?>>(null to null, channel.id) {
        value = try {
            repo.catchup(channel) to null
        } catch (e: Throwable) {
            emptyList<Program>() to (e.message ?: "Laden mislukt")
        }
    }
    val programs = state.first
    val error = state.second

    val dayFmt = remember { SimpleDateFormat("EEE d MMM", Locale("nl", "NL")) }
    fun dayKey(ms: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
    val today = remember { dayKey(System.currentTimeMillis()) }
    fun dayLabel(key: Long): String = when ((today - key) / 86_400_000L) {
        0L -> "Vandaag"
        1L -> "Gisteren"
        else -> dayFmt.format(Date(key)).replaceFirstChar { it.uppercase() }
    }

    val days = remember(programs) { programs.orEmpty().groupBy { dayKey(it.start) }.toSortedMap(compareByDescending { it }) }
    var selectedDay by remember(days) { mutableStateOf(days.keys.firstOrNull()) }
    val list = days[selectedDay].orEmpty()

    fun play(p: Program) {
        val key = "cu:${channel.id}:${p.start}"
        val entry = VodEntry(
            key = key,
            url = repo.catchupUrl(channel, p),
            title = p.title,
            subtitle = "${channel.name} · ${dayLabel(dayKey(p.start))} ${range(p)}",
        )
        onPlay(VodPlay(isSeries = false, itemId = key, image = channel.logo, entries = listOf(entry), index = 0))
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.tvIconFocus()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug", tint = Z.text)
            }
            ChannelLogo(channel.logo, channel.name, 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Terugkijken · ${channel.name}", color = Z.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Je aanbieder bewaart dit kanaal ${channel.archiveDays} ${if (channel.archiveDays == 1) "dag" else "dagen"}",
                    color = Z.textDim, fontSize = 13.sp,
                )
            }
        }

        when {
            programs == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Z.accent)
            }
            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Laden mislukt:\n$error", color = Z.textDim)
            }
            programs.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Geen gemiste programma's gevonden voor dit kanaal.", color = Z.textDim)
            }
            else -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    items(days.keys.toList()) { d ->
                        val sel = d == selectedDay
                        Box(
                            Modifier
                                .tvClickable(selected = sel, shape = RoundedCornerShape(50)) { selectedDay = d }
                                .background(if (sel) Color.Transparent else Z.surface, RoundedCornerShape(50))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(dayLabel(d), color = if (sel) Z.text else Z.textDim, fontSize = 14.sp)
                        }
                    }
                }
                LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    items(list, key = { it.start }) { p ->
                        val progress = remember(tick) { App.instance.vod.progress("cu:${channel.id}:${p.start}") }
                        Row(
                            Modifier
                                .widthIn(max = 980.dp)
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .tvClickable { play(p) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(range(p), color = Z.textDim, fontSize = 13.sp, modifier = Modifier.width(110.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.title, color = Z.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                p.desc?.let { Text(it, color = Z.textDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                if (progress != null) {
                                    Spacer(Modifier.height(4.dp))
                                    ProgressLine(progress, Modifier.fillMaxWidth(0.5f))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Icon(Icons.Filled.PlayArrow, "Afspelen", tint = Z.textDim, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

/** "⟲ Terugkijken" button for a channel that has catch-up. */
@Composable
fun CatchupButton(channel: Channel?, onClick: (Channel) -> Unit, modifier: Modifier = Modifier) {
    if (channel == null || channel.archiveDays <= 0) return
    Row(
        modifier
            .tvClickable(shape = RoundedCornerShape(50)) { onClick(channel) }
            .background(Z.surfaceHi, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.History, null, tint = Z.focus, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Terugkijken",
            color = Z.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
        )
    }
}


/** "☆ Favoriet" button for the selected channel (works with a TV remote: just press OK). */
@Composable
fun FavoriteButton(channel: Channel?, modifier: Modifier = Modifier) {
    if (channel == null) return
    val repo = App.instance.repo
    val favs by repo.favorites.collectAsState()
    val fav = channel.id in favs
    Row(
        modifier
            .tvClickable(shape = RoundedCornerShape(50)) {
                repo.toggleFavorite(channel.id)
                Toaster.show(if (fav) "Uit favorieten" else "Toegevoegd aan favorieten")
            }
            .background(Z.surfaceHi, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
            null, tint = Z.fav, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(if (fav) "Favoriet" else "Favoriet maken", color = Z.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}
