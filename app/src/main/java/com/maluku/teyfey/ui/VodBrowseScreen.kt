package com.maluku.teyfey.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.maluku.teyfey.App
import com.maluku.teyfey.data.Category
import com.maluku.teyfey.data.VodItem
import com.maluku.teyfey.data.VodKind
import kotlinx.coroutines.launch

private const val CAT_RECENT = "__recent"
private const val CAT_NEW = "__new"
private const val CAT_ALL = "__all"

@Composable
fun VodBrowseScreen(
    kind: VodKind,
    onSection: (Section) -> Unit,
    onOpen: (VodItem) -> Unit,
    onSettings: () -> Unit,
) {
    val vod = App.instance.vod
    val scope = rememberCoroutineScope()
    val lib by vod.library(kind).collectAsStateWithLifecycle()
    val loading by vod.loading.collectAsStateWithLifecycle()
    val tick by vod.progressTick.collectAsStateWithLifecycle()
    val section = if (kind == VodKind.MOVIES) Section.MOVIES else Section.SERIES

    var error by remember(kind) { mutableStateOf<String?>(null) }
    var cat by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    var searching by rememberSaveable(kind) { mutableStateOf(false) }
    var query by rememberSaveable(kind) { mutableStateOf("") }

    fun reload(force: Boolean) {
        scope.launch {
            error = null
            try {
                vod.load(kind, force)
            } catch (e: Throwable) {
                error = if (e is OutOfMemoryError) "Deze lijst is te groot voor dit apparaat." else e.message ?: "Laden mislukt"
            }
        }
    }
    LaunchedEffect(kind) { if (vod.supported()) reload(false) }

    val recent = remember(lib, tick) {
        val byId = lib.items.associateBy { it.id }
        vod.recentIds(kind).mapNotNull { byId[it] }
    }
    val cats: List<Category> = remember(lib, recent.isNotEmpty()) {
        buildList {
            if (recent.isNotEmpty()) add(Category(CAT_RECENT, "▶ Verder kijken"))
            add(Category(CAT_NEW, "Nieuw toegevoegd"))
            add(Category(CAT_ALL, if (kind == VodKind.MOVIES) "Alle films" else "Alle series"))
            addAll(lib.categories)
        }
    }
    val selected = cat ?: if (recent.isNotEmpty()) CAT_RECENT else CAT_NEW
    val fixed = remember(cats) { cats.take(cats.size - lib.categories.size).map { TreeNode.Group(it.id, it.name) } }
    val tree = remember(lib) { buildTree(lib.categories.map { TreeNode.Group(it.id, it.name) }) }
    var expanded by rememberSaveable(kind) { mutableStateOf(listOfNotNull(folderOf(tree, selected))) }
    var picker by remember { mutableStateOf(false) }
    val toggle: (String) -> Unit = { code -> expanded = if (code in expanded) expanded - code else expanded + code }
    val allLabel: (TreeNode.Folder) -> String = { f ->
        (if (kind == VodKind.MOVIES) "Alle films · " else "Alle series · ") + f.label.substringAfter("  ")
    }
    val catCounts = remember(lib) { lib.items.groupingBy { it.categoryId }.eachCount() }
    val items = remember(lib, selected, query, searching, recent) {
        when {
            searching && query.isNotBlank() -> lib.items.filter { it.name.contains(query.trim(), ignoreCase = true) }
            selected == CAT_RECENT -> recent
            selected == CAT_NEW -> lib.items.sortedByDescending { it.added }.take(300)
            selected == CAT_ALL -> lib.items
            selected.startsWith(FOLDER_KEY) -> {
                val code = selected.removePrefix(FOLDER_KEY)
                val ids = lib.categories.filter { countryCode(it.name) == code }.map { it.id }.toHashSet()
                lib.items.filter { it.categoryId in ids }
            }
            else -> lib.items.filter { it.categoryId == selected }
        }
    }
    val title = when {
        searching -> "Zoeken"
        selected.startsWith(FOLDER_KEY) -> folderLabel(selected.removePrefix(FOLDER_KEY))
        else -> cats.firstOrNull { it.id == selected }?.name ?: ""
    }

    val gridState = rememberLazyGridState()
    LaunchedEffect(selected, query) { gridState.scrollToItem(0) }

    val grid: @Composable (Modifier) -> Unit = { mod ->
        when {
            !vod.supported() -> Message(mod, "Films en series werken met een Xtream-login.\nZet die bij Instellingen → Andere login.")
            error != null -> Message(mod, "Laden mislukt:\n" + error!!) { reload(true) }
            lib.items.isEmpty() && loading == kind -> Box(mod, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Z.accent)
                    Spacer(Modifier.height(12.dp))
                    Text(if (kind == VodKind.MOVIES) "Films laden…" else "Series laden…", color = Z.textDim)
                }
            }
            items.isEmpty() && lib.items.isEmpty() -> Message(mod, "Nog geen lijst geladen.") { reload(true) }
            items.isEmpty() -> Message(mod, "Niets gevonden.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(124.dp),
                state = gridState,
                modifier = mod,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    val progress = if (kind == VodKind.MOVIES) vod.progress("m:${item.id}") else null
                    PosterCard(item, progress) { onOpen(item) }
                }
            }
        }
    }

    val actions: @Composable () -> Unit = {
        if (App.instance.playback.castSupported) CastButton()
        IconButton(onClick = { searching = !searching; if (!searching) query = "" }, modifier = Modifier.tvIconFocus()) {
            Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Zoeken", tint = Z.text)
        }
        IconButton(onClick = { reload(true) }, modifier = Modifier.tvIconFocus()) { Icon(Icons.Filled.Refresh, "Vernieuwen", tint = Z.text) }
        IconButton(onClick = onSettings, modifier = Modifier.tvIconFocus()) { Icon(Icons.Filled.Settings, "Instellingen", tint = Z.text) }
    }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) runCatching { searchFocus.requestFocus() } }
    val searchField: @Composable () -> Unit = {
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(if (kind == VodKind.MOVIES) "Zoek film…" else "Zoek serie…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).focusRequester(searchFocus),
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        if (maxWidth >= 720.dp) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(240.dp).fillMaxHeight().background(Z.panel)) {
                    Brand(24.dp, Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp))
                    SectionNavColumn(section, onSection)
                    ToolRow(Icons.Filled.Search, if (kind == VodKind.MOVIES) "Zoek film" else "Zoek serie") { searching = true }
                    ToolRow(Icons.Filled.Refresh, "Lijst vernieuwen") { reload(true) }
                    SidebarDivider()
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                        val pick: (String) -> Unit = { id -> searching = false; query = ""; cat = id }
                        val sel = selected.takeIf { !searching }
                        groupTree(fixed, emptySet(), sel, allLabel, { catCounts[it] }, toggle, pick)
                        groupTree(tree, expanded.toSet(), sel, allLabel, { catCounts[it] }, toggle, pick)
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title, color = Z.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (loading == kind) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        actions()
                    }
                    searchField()
                    grid(Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Brand(22.dp, Modifier.weight(1f))
                    actions()
                }
                SectionTabs(section, onSection)
                searchField()
                if (!searching) GroupChip(title) { picker = true }
                grid(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
    if (picker) {
        GroupPickerDialog(
            title = if (kind == VodKind.MOVIES) "Film-categorieën" else "Serie-categorieën",
            fixed = fixed,
            tree = tree,
            expanded = expanded.toSet(),
            selected = selected,
            allLabel = allLabel,
            count = { catCounts[it] },
            onToggle = toggle,
            onSelect = { searching = false; query = ""; cat = it },
            onDismiss = { picker = false },
        )
    }
}

@Composable
private fun Message(modifier: Modifier, text: String, onRetry: (() -> Unit)? = null) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = Z.textDim, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (onRetry != null) {
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.Button(
                    onClick = onRetry,
                    modifier = Modifier.focusRing(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Z.accent),
                ) { Text("Opnieuw proberen") }
            }
        }
    }
}

@Composable
private fun CategoryItem(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .tvClickable(selected = selected, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(Modifier.size(width = 3.dp, height = 16.dp).background(Z.accent, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            name,
            color = if (selected) Z.text else Z.textDim,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PosterCard(item: VodItem, progress: Float?, onClick: () -> Unit) {
    Column(Modifier.tvClickable(onClick = onClick).padding(4.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Z.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.name.take(2).uppercase(), color = Z.textDim, fontWeight = FontWeight.Bold)
            if (item.image != null) {
                AsyncImage(
                    model = item.image,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            item.rating?.let { r ->
                Text(
                    "★ ${r.take(3)}",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                        .background(Color(0xB3000000), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (progress != null) {
                ProgressLine(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth(), 4.dp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.name, color = Z.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
        )
        item.year?.let { Text(it, color = Z.textDim, fontSize = 11.sp) }
    }
}
