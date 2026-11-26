package com.maluku.teyfey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Key prefix for "everything inside a country folder". */
const val FOLDER_KEY = "folder:"

/** One entry in the sidebar: either a single group or a country folder with groups inside. */
sealed interface TreeNode {
    data class Group(val key: String, val label: String) : TreeNode
    data class Folder(val code: String, val label: String, val groups: List<Group>) : TreeNode
}

private val COUNTRY_NAMES = mapOf(
    "NL" to "Nederland", "BE" to "België", "DE" to "Duitsland", "UK" to "Verenigd Koninkrijk",
    "GB" to "Verenigd Koninkrijk", "US" to "Verenigde Staten", "USA" to "Verenigde Staten",
    "FR" to "Frankrijk", "ES" to "Spanje", "IT" to "Italië", "PT" to "Portugal", "TR" to "Turkije",
    "PL" to "Polen", "AR" to "Arabisch", "ARAB" to "Arabisch", "MA" to "Marokko", "IN" to "India",
    "ID" to "Indonesië", "CA" to "Canada", "AU" to "Australië", "NZ" to "Nieuw-Zeeland",
    "SE" to "Zweden", "NO" to "Noorwegen", "DK" to "Denemarken", "FI" to "Finland", "AT" to "Oostenrijk",
    "CH" to "Zwitserland", "IE" to "Ierland", "GR" to "Griekenland", "RO" to "Roemenië", "BG" to "Bulgarije",
    "HU" to "Hongarije", "CZ" to "Tsjechië", "SK" to "Slowakije", "RS" to "Servië", "HR" to "Kroatië",
    "BA" to "Bosnië", "AL" to "Albanië", "MK" to "Macedonië", "SI" to "Slovenië", "RU" to "Rusland",
    "UA" to "Oekraïne", "BR" to "Brazilië", "MX" to "Mexico", "LAT" to "Latijns-Amerika", "LATAM" to "Latijns-Amerika",
    "AF" to "Afrika", "AFR" to "Afrika", "PK" to "Pakistan", "BD" to "Bangladesh", "CN" to "China",
    "JP" to "Japan", "KR" to "Korea", "PH" to "Filipijnen", "TH" to "Thailand", "VN" to "Vietnam",
    "IR" to "Iran", "KU" to "Koerdistan", "KURD" to "Koerdistan", "IL" to "Israël", "EG" to "Egypte",
    "SR" to "Suriname", "CW" to "Curaçao", "EX" to "Ex-Joegoslavië", "EXYU" to "Ex-Joegoslavië",
    "ALL" to "Internationaal", "INT" to "Internationaal", "4K" to "4K", "VIP" to "VIP", "XXX" to "18+",
)

private val SPLIT = Regex("""[\s|:\-–—_•●▶►*\[\]()/]+""")

/** "NL | Sport" -> "NL", "DE: News" -> "DE", "Nederland HD" -> null. */
fun countryCode(group: String): String? {
    val first = group.trim().split(SPLIT).firstOrNull { it.isNotEmpty() } ?: return null
    if (first.length !in 2..5) return null
    if (!first.all { it.isLetter() || it.isDigit() }) return null
    val up = first.uppercase()
    // Must look like a code: written in capitals, or a code we know.
    return if (first == up || up in COUNTRY_NAMES) up else null
}

fun flagOf(code: String): String {
    val c = when (code) { "UK" -> "GB"; "USA" -> "US"; else -> code }
    if (c.length != 2 || !c.all { it in 'A'..'Z' }) return ""
    return String(Character.toChars(0x1F1E6 + (c[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (c[1] - 'A')))
}

fun folderLabel(code: String): String {
    val name = COUNTRY_NAMES[code] ?: code
    val flag = if (code == "ALL" || code == "INT") "🌍" else flagOf(code)
    return if (flag.isNotEmpty()) "$flag  $name" else name
}

/**
 * Turns flat group names into a tree. Groups that share a country code with at least
 * one other group become a folder; everything else stays a single row (order kept).
 */
fun buildTree(groups: List<TreeNode.Group>): List<TreeNode> {
    val byCode = groups.groupBy { countryCode(it.label) }
    val out = ArrayList<TreeNode>()
    val done = HashSet<String>()
    for (g in groups) {
        val code = countryCode(g.label)
        val members = code?.let { byCode[it] }
        if (code != null && members != null && members.size >= 2) {
            if (done.add(code)) out.add(TreeNode.Folder(code, folderLabel(code), members))
        } else {
            out.add(g)
        }
    }
    return out
}

/** Which folder holds this selected key (to auto-expand it). */
fun folderOf(tree: List<TreeNode>, key: String): String? {
    if (key.startsWith(FOLDER_KEY)) return key.removePrefix(FOLDER_KEY)
    return tree.filterIsInstance<TreeNode.Folder>().firstOrNull { f -> f.groups.any { it.key == key } }?.code
}

/** Adds the tree rows to a LazyColumn. */
fun LazyListScope.groupTree(
    tree: List<TreeNode>,
    expanded: Set<String>,
    selected: String?,
    allLabel: (TreeNode.Folder) -> String,
    count: (String) -> Int? = { null },
    onToggle: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    for (node in tree) {
        when (node) {
            is TreeNode.Group -> item(key = "g_" + node.key) {
                TreeRow(node.label, node.key == selected, depth = 0, count = count(node.key)) { onSelect(node.key) }
            }
            is TreeNode.Folder -> {
                val open = node.code in expanded
                item(key = "f_" + node.code) {
                    TreeRow(
                        label = node.label,
                        selected = false,
                        depth = 0,
                        count = node.groups.size,
                        arrow = if (open) 1 else 0,
                        bold = true,
                    ) { onToggle(node.code) }
                }
                if (open) {
                    val allKey = FOLDER_KEY + node.code
                    item(key = "fa_" + node.code) {
                        TreeRow(allLabel(node), allKey == selected, depth = 1) { onSelect(allKey) }
                    }
                    items(node.groups, key = { "fg_" + node.code + "_" + it.key }) { g ->
                        TreeRow(g.label, g.key == selected, depth = 1, count = count(g.key)) { onSelect(g.key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TreeRow(
    label: String,
    selected: Boolean,
    depth: Int,
    count: Int? = null,
    arrow: Int = -1, // -1 none, 0 closed, 1 open
    bold: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp + (depth * 14).dp, end = 8.dp, top = 1.dp, bottom = 1.dp)
            .tvClickable(selected = selected, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (arrow) {
            0 -> Icon(Icons.Filled.KeyboardArrowRight, null, tint = Z.textDim, modifier = Modifier.size(18.dp))
            1 -> Icon(Icons.Filled.KeyboardArrowDown, null, tint = Z.accent, modifier = Modifier.size(18.dp))
            else -> if (selected) {
                Box(Modifier.size(width = 3.dp, height = 16.dp).background(Z.accent, RoundedCornerShape(2.dp)))
            }
        }
        if (arrow >= 0 || selected) Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (selected || bold) Z.text else Z.textDim,
            fontWeight = if (selected || bold) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        count?.let { Text(it.toString(), color = Z.textDim, fontSize = 12.sp) }
    }
}

/** Phone version: the same tree in a pop-up. */
@Composable
fun GroupPickerDialog(
    title: String,
    fixed: List<TreeNode.Group>,
    tree: List<TreeNode>,
    expanded: Set<String>,
    selected: String?,
    allLabel: (TreeNode.Folder) -> String,
    count: (String) -> Int? = { null },
    onToggle: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                groupTree(fixed, emptySet(), selected, allLabel, count, onToggle) { onSelect(it); onDismiss() }
                groupTree(tree, expanded, selected, allLabel, count, onToggle) { onSelect(it); onDismiss() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Sluiten") } },
        containerColor = Z.panel,
    )
}

/** Chip on phones that opens [GroupPickerDialog]. */
@Composable
fun GroupChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .tvClickable(shape = RoundedCornerShape(50), onClick = onClick)
            .background(Z.surface, RoundedCornerShape(50))
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Z.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.KeyboardArrowDown, null, tint = Z.textDim, modifier = Modifier.size(18.dp))
    }
}
