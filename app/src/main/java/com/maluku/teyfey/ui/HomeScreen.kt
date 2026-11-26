package com.maluku.teyfey.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maluku.teyfey.App
import com.maluku.teyfey.data.Channel
import com.maluku.teyfey.data.liveAt
import com.maluku.teyfey.data.nextAfter

@Composable
fun HomeScreen(
    now: Long,
    group: String,
    onGroup: (String) -> Unit,
    onFullscreen: () -> Unit,
    onGuide: () -> Unit,
    onSettings: () -> Unit,
    onSection: (Section) -> Unit,
    onCatchup: (Channel) -> Unit = {},
) {
    val app = App.instance
    val repo = app.repo
    val pb = app.playback
    val ctx = LocalContext.current

    val all by repo.channels.collectAsStateWithLifecycle()
    val epg by repo.epg.collectAsStateWithLifecycle()
    val favs by repo.favorites.collectAsStateWithLifecycle()
    val busy by repo.busy.collectAsStateWithLifecycle()
    val current by pb.current.collectAsStateWithLifecycle()

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val fixedGroups = remember { listOf(TreeNode.Group(GROUP_FAV, GROUP_FAV), TreeNode.Group(GROUP_ALL, GROUP_ALL)) }
    val tree = remember(all) { buildTree(all.map { it.group }.distinct().map { TreeNode.Group(it, it) }) }
    val counts = remember(all, favs) {
        all.groupingBy { it.group }.eachCount() + mapOf(GROUP_ALL to all.size, GROUP_FAV to favs.size)
    }
    var expanded by rememberSaveable { mutableStateOf(listOfNotNull(folderOf(tree, group))) }
    var picker by remember { mutableStateOf(false) }
    val toggle: (String) -> Unit = { code -> expanded = if (code in expanded) expanded - code else expanded + code }
    val allLabel: (TreeNode.Folder) -> String = { f -> "Alle kanalen · ${f.label.substringAfter("  ")}" }
    val groupLabel = when {
        group.startsWith(FOLDER_KEY) -> folderLabel(group.removePrefix(FOLDER_KEY))
        else -> group
    }
    val list = remember(all, group, favs, query, searching) {
        if (searching && query.isNotBlank()) all.filter { it.name.contains(query.trim(), ignoreCase = true) }
        else filterGroup(all, group, favs)
    }

    // Start the last watched channel in the preview.
    LaunchedEffect(all.isNotEmpty()) {
        if (current == null && all.isNotEmpty()) {
            val last = all.firstOrNull { it.id == repo.lastChannelId } ?: list.firstOrNull()
            last?.let { pb.play(it) }
        }
    }

    val select: (Channel) -> Unit = { ch ->
        if (current?.id == ch.id) onFullscreen() else {
            pb.play(ch)
            repo.lastChannelId = ch.id
        }
    }
    val toggleFav: (Channel) -> Unit = { ch ->
        val was = ch.id in favs
        repo.toggleFavorite(ch.id)
        Toast.makeText(ctx, if (was) "Uit favorieten" else "Toegevoegd aan favorieten", Toast.LENGTH_SHORT).show()
    }

    val listState = rememberLazyListState()
    LaunchedEffect(group, query) { if (list.isNotEmpty()) listState.scrollToItem(0) }

    val channelList: @Composable (Modifier) -> Unit = { mod ->
        if (all.isEmpty()) {
            Box(mod, contentAlignment = Alignment.Center) {
                Text(busy ?: "Nog geen kanalen. Ga naar Instellingen om te vernieuwen.", color = Z.textDim)
            }
        } else if (list.isEmpty()) {
            Box(mod.padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (group == GROUP_FAV) "Nog geen favorieten.\nHoud een kanaal lang ingedrukt om het toe te voegen."
                    else "Geen kanalen gevonden.",
                    color = Z.textDim,
                )
            }
        } else {
            LazyColumn(mod, state = listState, contentPadding = PaddingValues(vertical = 6.dp)) {
                items(list, key = { it.id }) { ch ->
                    val progs = epg[ch.id]
                    ChannelRow(
                        channel = ch,
                        now = now,
                        program = progs?.liveAt(now),
                        selected = current?.id == ch.id,
                        favorite = ch.id in favs,
                        onClick = { select(ch) },
                        onLongClick = { toggleFav(ch) },
                    )
                }
            }
        }
    }

    val actions: @Composable () -> Unit = {
        if (pb.castSupported) CastButton()
        IconButton(onClick = { searching = !searching; if (!searching) query = "" }, modifier = Modifier.tvIconFocus()) {
            Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Zoeken", tint = Z.text)
        }
        IconButton(onClick = onGuide, modifier = Modifier.tvIconFocus()) { Icon(Icons.Filled.DateRange, "TV-gids", tint = Z.text) }
        IconButton(onClick = onSettings, modifier = Modifier.tvIconFocus()) { Icon(Icons.Filled.Settings, "Instellingen", tint = Z.text) }
        MuteButton()
    }

    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(searching) { if (searching) runCatching { searchFocus.requestFocus() } }
    val searchField: @Composable () -> Unit = {
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Zoek kanaal…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    .focusRequester(searchFocus),
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        val wide = maxWidth >= 720.dp
        val panelWidth = maxWidth * 0.36f
        Column(Modifier.fillMaxSize()) {
            if (busy != null) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Z.accent)
            if (!wide) SubscriptionWarning()

            if (wide) {
                // ---------- TV / landscape: groups | channels | preview ----------
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(240.dp).fillMaxHeight().background(Z.panel)) {
                        Brand(24.dp, Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp))
                        SectionNavColumn(Section.LIVE, onSection)
                        ToolRow(Icons.Filled.Search, "Zoek kanaal") { searching = true }
                        ToolRow(Icons.Filled.DateRange, "TV-gids", onGuide)
                        SidebarDivider()
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                            val pick: (String) -> Unit = { g -> searching = false; query = ""; onGroup(g) }
                            groupTree(fixedGroups, emptySet(), group.takeIf { !searching }, allLabel, { counts[it] }, toggle, pick)
                            groupTree(tree, expanded.toSet(), group.takeIf { !searching }, allLabel, { counts[it] }, toggle, pick)
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (searching) "Zoeken" else groupLabel,
                                color = Z.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            actions()
                        }
                        searchField()
                        channelList(Modifier.weight(1f).fillMaxWidth())
                    }
                    Column(
                        Modifier.fillMaxHeight().width(panelWidth)
                            .background(Z.panel).padding(14.dp)
                    ) {
                        Preview(Modifier.fillMaxWidth(), onFullscreen)
                        Spacer(Modifier.height(14.dp))
                        NowInfo(current, current?.let { epg[it.id] }, now, detailed = true)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FavoriteButton(current)
                            CatchupButton(current, onCatchup)
                        }
                        Spacer(Modifier.weight(1f))
                        SubscriptionInfo(Modifier.align(Alignment.End))
                    }
                }
            } else {
                // ---------- phone portrait ----------
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Brand(22.dp, Modifier.weight(1f))
                    actions()
                }
                SectionTabs(Section.LIVE, onSection)
                Preview(Modifier.fillMaxWidth(), onFullscreen)
                NowInfo(current, current?.let { epg[it.id] }, now, detailed = false, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                Row(Modifier.padding(start = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FavoriteButton(current)
                    CatchupButton(current, onCatchup)
                }
                searchField()
                if (!searching) GroupChip(groupLabel) { picker = true }
                channelList(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
    if (picker) {
        GroupPickerDialog(
            title = "Kies een groep",
            fixed = fixedGroups,
            tree = tree,
            expanded = expanded.toSet(),
            selected = group,
            allLabel = allLabel,
            count = { counts[it] },
            onToggle = toggle,
            onSelect = { searching = false; query = ""; onGroup(it) },
            onDismiss = { picker = false },
        )
    }
}

@Composable
private fun GroupItem(name: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .tvClickable(selected = selected, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(Modifier.size(width = 3.dp, height = 18.dp).background(Z.accent, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            name,
            color = if (selected) Z.text else Z.textDim,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        count?.let { Text(it.toString(), color = Z.textDim, fontSize = 12.sp) }
    }
}

@Composable
private fun Preview(modifier: Modifier, onFullscreen: () -> Unit) {
    val pb = App.instance.playback
    val casting by pb.casting.collectAsStateWithLifecycle()
    val device by pb.castDevice.collectAsStateWithLifecycle()
    val current by pb.current.collectAsStateWithLifecycle()
    val error by pb.error.collectAsStateWithLifecycle()

    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .tvClickable(onClick = onFullscreen)
            .background(Color.Black)
    ) {
        if (casting) CastingCard(device, current, Modifier.fillMaxSize())
        else PlayerSurface(pb.exo, Modifier.fillMaxSize())

        error?.let {
            Text(
                it, color = Z.text, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center).background(Color(0xAA000000), RoundedCornerShape(6.dp)).padding(8.dp),
            )
        }
        Icon(
            Icons.Filled.Fullscreen, "Volledig scherm", tint = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                .background(Color(0x66000000), RoundedCornerShape(6.dp)).padding(4.dp).size(20.dp),
        )
        // Sound on/off for the preview
        val repo = App.instance.repo
        val muted by repo.previewMuted.collectAsState()
        LaunchedEffect(muted) { pb.setMuted(muted) }
    }
}

/** Sound on/off for the preview: icon next to the gear (reachable with the TV remote). */
@Composable
fun MuteButton(modifier: Modifier = Modifier) {
    val repo = App.instance.repo
    val muted by repo.previewMuted.collectAsState()
    IconButton(onClick = { repo.setPreviewMuted(!muted) }, modifier = modifier.tvIconFocus()) {
        Icon(
            if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            if (muted) "Geluid aan" else "Geluid uit",
            tint = if (muted) Z.fav else Z.text,
        )
    }
}

@Composable
fun NowInfo(
    channel: Channel?,
    programs: List<com.maluku.teyfey.data.Program>?,
    now: Long,
    detailed: Boolean,
    modifier: Modifier = Modifier,
) {
    if (channel == null) return
    val live = programs?.liveAt(now)
    val next = programs?.nextAfter(now)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(Z.live, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "${channel.number}  ${channel.name}", color = Z.textDim, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            live?.title ?: channel.name,
            color = Z.text,
            fontSize = if (detailed) 20.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (live != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(range(live), color = Z.textDim, fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
                ProgressLine(live.progress(now), Modifier.weight(1f))
            }
            if (detailed && !live.desc.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(live.desc, color = Z.textDim, fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
        }
        if (next != null) {
            Spacer(Modifier.height(if (detailed) 14.dp else 4.dp))
            Text(
                "Straks ${timeOf(next.start)}  ·  ${next.title}",
                color = Z.textDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
