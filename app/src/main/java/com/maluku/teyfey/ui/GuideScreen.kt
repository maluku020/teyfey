package com.maluku.teyfey.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maluku.teyfey.App
import com.maluku.teyfey.data.Channel
import com.maluku.teyfey.data.Program
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DP_PER_MIN = 4.dp
private val ROW_H = 62.dp
private val CH_COL = 190.dp
private const val HALF_HOUR = 30 * 60_000L

@Composable
fun GuideScreen(
    now: Long,
    group: String,
    onGroup: (String) -> Unit,
    onWatch: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit = {},
) {
    val app = App.instance
    val repo = app.repo
    val pb = app.playback
    val all by repo.channels.collectAsStateWithLifecycle()
    val epg by repo.epg.collectAsStateWithLifecycle()
    val favs by repo.favorites.collectAsStateWithLifecycle()
    val busy by repo.busy.collectAsStateWithLifecycle()

    val fixedGroups = remember { listOf(TreeNode.Group(GROUP_FAV, GROUP_FAV), TreeNode.Group(GROUP_ALL, GROUP_ALL)) }
    val tree = remember(all) { buildTree(all.map { it.group }.distinct().map { TreeNode.Group(it, it) }) }
    val counts = remember(all, favs) {
        all.groupingBy { it.group }.eachCount() + mapOf(GROUP_ALL to all.size, GROUP_FAV to favs.size)
    }
    var expanded by rememberSaveable { mutableStateOf(listOfNotNull(folderOf(tree, group))) }
    var picker by remember { mutableStateOf(false) }
    val groupLabel = if (group.startsWith(FOLDER_KEY)) folderLabel(group.removePrefix(FOLDER_KEY)) else group
    val list = remember(all, group, favs) { filterGroup(all, group, favs) }

    // Time window: from half an hour before now, 26 hours ahead.
    val windowStart = remember { (System.currentTimeMillis() - HALF_HOUR) / HALF_HOUR * HALF_HOUR }
    val windowEnd = windowStart + 26 * 3_600_000L
    val totalWidth = DP_PER_MIN * ((windowEnd - windowStart) / 60_000f)

    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        // start with "now" near the left edge
        val px = with(density) { (DP_PER_MIN * ((System.currentTimeMillis() - windowStart - 15 * 60_000L) / 60_000f)).toPx() }
        hScroll.scrollTo(px.toInt().coerceAtLeast(0))
    }

    var details by remember { mutableStateOf<Pair<Channel, Program>?>(null) }

    val watch: (Channel) -> Unit = { ch ->
        pb.play(ch)
        repo.lastChannelId = ch.id
        onWatch()
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.tvIconFocus()) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug", tint = Z.text) }
            Text("TV-gids", color = Z.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            Text(
                SimpleDateFormat("EEEE d MMMM", Locale("nl", "NL")).format(Date(now)),
                color = Z.textDim, fontSize = 14.sp, modifier = Modifier.weight(1f),
            )
            busy?.let { Text(it, color = Z.textDim, fontSize = 12.sp) }
            IconButton(onClick = onSettings, modifier = Modifier.tvIconFocus()) {
                Icon(Icons.Filled.Settings, "Instellingen", tint = Z.text)
            }
        }

        GroupChip(groupLabel) { picker = true }
        Spacer(Modifier.height(4.dp))

        // Time header
        Row(Modifier.fillMaxWidth().height(28.dp).background(Z.panel)) {
            Spacer(Modifier.width(CH_COL))
            Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll, enabled = false)) {
                Box(Modifier.width(totalWidth).fillMaxHeight()) {
                    var t = windowStart
                    while (t < windowEnd) {
                        val x = DP_PER_MIN * ((t - windowStart) / 60_000f)
                        Text(
                            timeOf(t), color = Z.textDim, fontSize = 12.sp,
                            modifier = Modifier.offset(x = x + 6.dp).align(Alignment.CenterStart),
                        )
                        t += HALF_HOUR
                    }
                }
            }
        }

        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Geen kanalen in deze groep.", color = Z.textDim)
            }
            return@Column
        }
        if (epg.isEmpty()) {
            Text(
                if (busy != null) "TV-gids wordt geladen…" else "Geen TV-gids gevonden. Voeg een EPG-link toe bij Instellingen.",
                color = Z.textDim, fontSize = 13.sp, modifier = Modifier.padding(12.dp),
            )
        }

        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { ch ->
                    GuideRow(
                        channel = ch,
                        programs = epg[ch.id].orEmpty(),
                        now = now,
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        totalWidth = totalWidth,
                        hScroll = hScroll,
                        onChannel = { watch(ch) },
                        onProgram = { p -> if (p.isLive(now)) watch(ch) else details = ch to p },
                    )
                }
            }
            // "now" line
            val nowX = DP_PER_MIN * ((now - windowStart) / 60_000f)
            val nowPx = with(density) { (CH_COL + nowX).toPx() }
            Box(
                Modifier
                    .offset { IntOffset((nowPx - hScroll.value).toInt(), 0) }
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(if (nowPx - hScroll.value > with(density) { CH_COL.toPx() }) Z.live else Color.Transparent)
            )
        }
    }

    if (picker) {
        GroupPickerDialog(
            title = "Kies een groep",
            fixed = fixedGroups,
            tree = tree,
            expanded = expanded.toSet(),
            selected = group,
            allLabel = { f -> "Alle kanalen · ${f.label.substringAfter("  ")}" },
            count = { counts[it] },
            onToggle = { code -> expanded = if (code in expanded) expanded - code else expanded + code },
            onSelect = { onGroup(it) },
            onDismiss = { picker = false },
        )
    }

    details?.let { (ch, p) ->
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text(p.title) },
            text = {
                Column {
                    Text("${ch.name}  ·  ${range(p)}", color = Z.textDim, fontSize = 13.sp)
                    if (!p.desc.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(p.desc, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { details = null; watch(ch) }) { Text("Kijk nu ${ch.name}") }
            },
            dismissButton = { TextButton(onClick = { details = null }) { Text("Sluiten") } },
            containerColor = Z.surface,
        )
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    programs: List<Program>,
    now: Long,
    windowStart: Long,
    windowEnd: Long,
    totalWidth: androidx.compose.ui.unit.Dp,
    hScroll: ScrollState,
    onChannel: () -> Unit,
    onProgram: (Program) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(ROW_H).padding(vertical = 2.dp)) {
        Row(
            Modifier.width(CH_COL).fillMaxHeight().background(Z.panel)
                .tvClickable(onClick = onChannel)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(channel.number.toString(), color = Z.textDim, fontSize = 11.sp, modifier = Modifier.width(30.dp))
            ChannelLogo(channel.logo, channel.name, 30.dp)
            Spacer(Modifier.width(8.dp))
            Text(channel.name, color = Z.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
            Box(Modifier.width(totalWidth).fillMaxHeight()) {
                val visible = programs.filter { it.stop > windowStart && it.start < windowEnd }
                if (visible.isEmpty()) {
                    Text(
                        "Geen programma-informatie", color = Z.textDim.copy(alpha = 0.5f), fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterStart).offset {
                            IntOffset(hScroll.value + 24, 0)
                        },
                    )
                }
                for (p in visible) {
                    val s = maxOf(p.start, windowStart)
                    val e = minOf(p.stop, windowEnd)
                    val x = DP_PER_MIN * ((s - windowStart) / 60_000f)
                    val w = DP_PER_MIN * ((e - s) / 60_000f)
                    val live = p.isLive(now)
                    val past = p.stop <= now
                    Box(
                        Modifier
                            .offset(x = x)
                            .width(w)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                            .background(if (live) Z.surfaceHi else Z.surface, RoundedCornerShape(6.dp))
                            .tvClickable(shape = RoundedCornerShape(6.dp)) { onProgram(p) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Text(
                                p.title,
                                color = if (past) Z.textDim else Z.text,
                                fontSize = 13.sp,
                                fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(range(p), color = Z.textDim, fontSize = 11.sp, maxLines = 1)
                        }
                        if (live) {
                            ProgressLine(
                                p.progress(now),
                                Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                                2.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}
