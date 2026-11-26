package com.maluku.teyfey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maluku.teyfey.App
import com.maluku.teyfey.data.Channel
import com.maluku.teyfey.data.liveAt

/** "Gemist": only channels with catch-up, grouped like Live TV. Picking one opens [CatchupScreen]. */
@Composable
fun GemistScreen(
    now: Long,
    onSection: (Section) -> Unit,
    onOpen: (Channel) -> Unit,
    onSettings: () -> Unit,
) {
    val repo = App.instance.repo
    val allChannels by repo.channels.collectAsState()
    val epg by repo.epg.collectAsState()
    val favs by repo.favorites.collectAsState()
    val busy by repo.busy.collectAsState()

    val all = remember(allChannels) { allChannels.filter { it.archiveDays > 0 } }
    var group by rememberSaveable { mutableStateOf(GROUP_ALL) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val fixed = remember { listOf(TreeNode.Group(GROUP_FAV, GROUP_FAV), TreeNode.Group(GROUP_ALL, GROUP_ALL)) }
    val tree = remember(all) { buildTree(all.map { it.group }.distinct().map { TreeNode.Group(it, it) }) }
    val counts = remember(all, favs) {
        all.groupingBy { it.group }.eachCount() + mapOf(GROUP_ALL to all.size, GROUP_FAV to all.count { it.id in favs })
    }
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var picker by remember { mutableStateOf(false) }
    val toggle: (String) -> Unit = { code -> expanded = if (code in expanded) expanded - code else expanded + code }
    val allLabel: (TreeNode.Folder) -> String = { f -> "Alle kanalen · ${f.label.substringAfter("  ")}" }
    val groupLabel = if (group.startsWith(FOLDER_KEY)) folderLabel(group.removePrefix(FOLDER_KEY)) else group
    val pick: (String) -> Unit = { g -> searching = false; query = ""; group = g }

    val list = remember(all, group, favs, query, searching) {
        if (searching && query.isNotBlank()) all.filter { it.name.contains(query.trim(), ignoreCase = true) }
        else filterGroup(all, group, favs)
    }

    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) runCatching { searchFocus.requestFocus() } }

    val channelList: @Composable (Modifier) -> Unit = { mod ->
        when {
            all.isEmpty() -> Box(mod.padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (busy != null) "Kanalen laden…"
                    else "Geen kanalen met terugkijken gevonden.\nJe aanbieder biedt het misschien niet aan, of vernieuw de kanalen bij Instellingen.",
                    color = Z.textDim,
                )
            }
            list.isEmpty() -> Box(mod.padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Geen kanalen gevonden.", color = Z.textDim)
            }
            else -> LazyColumn(mod, contentPadding = PaddingValues(vertical = 6.dp)) {
                items(list, key = { it.id }) { ch ->
                    ChannelRow(
                        channel = ch,
                        now = now,
                        program = epg[ch.id]?.liveAt(now),
                        selected = false,
                        favorite = ch.id in favs,
                        onClick = { onOpen(ch) },
                        onLongClick = { repo.toggleFavorite(ch.id) },
                    )
                }
            }
        }
    }

    val searchField: @Composable () -> Unit = {
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Zoek kanaal…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).focusRequester(searchFocus),
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 720.dp) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(240.dp).fillMaxHeight().background(Z.panel)) {
                    Brand(24.dp, Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp))
                    SectionNavColumn(Section.GEMIST, onSection)
                    ToolRow(Icons.Filled.Search, "Zoek kanaal") { searching = true }
                    ToolRow(Icons.Filled.Settings, "Instellingen", onSettings)
                    SidebarDivider()
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                        val sel = group.takeIf { !searching }
                        groupTree(fixed, emptySet(), sel, allLabel, { counts[it] }, toggle, pick)
                        groupTree(tree, expanded.toSet(), sel, allLabel, { counts[it] }, toggle, pick)
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (searching) "Zoeken" else "Gemist · $groupLabel",
                                color = Z.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text("Kies een kanaal om gemiste programma's terug te kijken", color = Z.textDim, fontSize = 12.sp)
                        }
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }, modifier = Modifier.tvIconFocus()) {
                            Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Zoeken", tint = Z.text)
                        }
                    }
                    searchField()
                    channelList(Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Brand(22.dp, Modifier.weight(1f))
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }, modifier = Modifier.tvIconFocus()) {
                        Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Zoeken", tint = Z.text)
                    }
                    IconButton(onClick = onSettings, modifier = Modifier.tvIconFocus()) {
                        Icon(Icons.Filled.Settings, "Instellingen", tint = Z.text)
                    }
                }
                SectionTabs(Section.GEMIST, onSection)
                searchField()
                if (!searching) GroupChip(groupLabel) { picker = true }
                channelList(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    if (picker) {
        GroupPickerDialog(
            title = "Kies een groep",
            fixed = fixed,
            tree = tree,
            expanded = expanded.toSet(),
            selected = group,
            allLabel = allLabel,
            count = { counts[it] },
            onToggle = toggle,
            onSelect = pick,
            onDismiss = { picker = false },
        )
    }
}
