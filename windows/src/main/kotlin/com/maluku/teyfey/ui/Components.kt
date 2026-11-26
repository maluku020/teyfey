package com.maluku.teyfey.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.onClick
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.maluku.teyfey.data.Channel
import com.maluku.teyfey.data.Program
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault())
fun timeOf(ms: Long): String = hhmm.format(Date(ms))
fun range(p: Program) = "${timeOf(p.start)} – ${timeOf(p.stop)}"


/** Clickable that also looks good with a TV remote (focus ring + highlight). */
@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvClickable(
    selected: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp),
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.04f else 1f, label = "focusScale")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .zIndex(if (focused) 1f else 0f)
        .clip(shape)
        .background(
            when {
                focused -> Z.focus.copy(alpha = 0.28f)
                selected -> Z.accentSoft
                else -> Color.Transparent
            }
        )
        .border(if (focused) 3.dp else 0.dp, if (focused) Z.focus else Color.Transparent, shape)
        .then(
            if (onLongClick != null) {
                Modifier.onClick(
                    matcher = androidx.compose.foundation.PointerMatcher.mouse(androidx.compose.ui.input.pointer.PointerButton.Secondary),
                    onClick = onLongClick,
                )
            } else Modifier
        )
        .combinedClickable(
            interactionSource = source,
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

@Composable
fun ChannelLogo(url: String?, name: String, size: Dp = 44.dp) {
    Box(
        Modifier
            .size(width = size * 1.4f, height = size)
            .clip(RoundedCornerShape(8.dp))
            .background(Z.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        } else {
            Text(
                name.take(2).uppercase(),
                color = Z.textDim,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(Z.surfaceHi)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .background(Z.accent)
        )
    }
}

@Composable
fun ChannelRow(
    channel: Channel,
    now: Long,
    program: Program?,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .tvClickable(selected = selected, onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            channel.number.toString(),
            color = if (selected) Z.accent else Z.textDim,
            fontSize = 13.sp,
            modifier = Modifier.width(40.dp),
        )
        ChannelLogo(channel.logo, channel.name, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.name,
                    color = Z.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (channel.archiveDays > 0) {
                    Spacer(Modifier.width(6.dp))
                    Icon(androidx.compose.material.icons.Icons.Filled.History, "Terugkijken", tint = Z.textDim, modifier = Modifier.size(14.dp))
                }
                if (favorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Star, null, tint = Z.fav, modifier = Modifier.size(14.dp))
                }
            }
            if (program != null) {
                Text(
                    program.title,
                    color = Z.textDim,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(program.progress(now), Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(timeOf(program.stop), color = Z.textDim, fontSize = 11.sp)
                }
            } else {
                Text("Geen programma-informatie", color = Z.textDim.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
    }
}

/** Shows the current VLC video frame, letterboxed on black. */
@Composable
fun PlayerSurface(modifier: Modifier = Modifier) {
    val frame = com.maluku.teyfey.App.instance.playback.frame
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (frame != null) {
            androidx.compose.foundation.Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun CastingCard(device: String?, channel: Channel?, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Cast, null, tint = Z.accent, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(10.dp))
        Text("Speelt op ${device ?: "Chromecast"}", color = Z.text, fontWeight = FontWeight.SemiBold)
        channel?.let { Text(it.name, color = Z.textDim, fontSize = 13.sp) }
    }
}

/** No Chromecast button on Windows. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun CastButton(modifier: Modifier = Modifier) {}

val com.maluku.teyfey.player.PlaybackManager.castSupported: Boolean get() = false

/** Logo + name, used in the headers. */
@Composable
fun Brand(height: Dp = 22.dp, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource("logo_white.png"),
            contentDescription = null,
            modifier = Modifier.height(height),
        )
        Spacer(Modifier.width(height * 0.45f))
        Text("TEYFEY", color = Z.text, fontSize = (height.value * 0.95f).sp, fontWeight = FontWeight.Black)
    }
}

enum class Section(val label: String) { LIVE("Live TV"), GEMIST("Gemist"), MOVIES("Films"), SERIES("Series") }

/** Vertical section menu for the TV / landscape sidebar. */
@Composable
fun SectionNavColumn(current: Section, onSelect: (Section) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Section.entries.forEach { s ->
            val sel = s == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 1.dp)
                    .tvClickable(selected = sel) { onSelect(s) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when (s) {
                        Section.LIVE -> Icons.Filled.LiveTv
                        Section.GEMIST -> Icons.Filled.History
                        Section.MOVIES -> Icons.Filled.Movie
                        Section.SERIES -> Icons.Filled.VideoLibrary
                    },
                    null,
                    tint = if (sel) Z.accent else Z.textDim,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    s.label,
                    color = if (sel) Z.text else Z.textDim,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                )
            }
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(Z.surfaceHi))
    }
}

/** Horizontal section tabs for phones. */
@Composable
fun SectionTabs(current: Section, onSelect: (Section) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        Section.entries.forEach { s ->
            val sel = s == current
            Column(
                Modifier
                    .weight(1f)
                    .tvClickable(selected = false) { onSelect(s) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    s.label,
                    color = if (sel) Z.text else Z.textDim,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.size(width = 28.dp, height = 3.dp)
                        .background(if (sel) Z.accent else Color.Transparent, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

/** Small banner shown when the subscription ends within 14 days. */
@Composable
fun SubscriptionWarning(modifier: Modifier = Modifier) {
    val acc by com.maluku.teyfey.App.instance.repo.account
        .collectAsState()
    val days = acc?.daysLeft() ?: return
    if (days > 14) return
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(if (days <= 3) Z.live.copy(alpha = 0.25f) else Z.fav.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            if (days < 0) "Abbo gestopt op ${dateOf(acc!!.expiresAt!!)}"
            else "Abbo stopt ${dateOf(acc!!.expiresAt!!)} — nog $days ${if (days == 1L) "dag" else "dagen"}",
            color = Z.text,
            fontSize = 13.sp,
        )
    }
}

fun dateOf(ms: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale("nl", "NL")).format(Date(ms))


/** Windows is never a TV. */
@Composable
fun isTv(): Boolean = false

/** Space kept free at the screen edges so TVs that crop the picture (overscan) don't cut anything off. */
@Composable
fun tvSafePadding(): androidx.compose.foundation.layout.PaddingValues {
    val margin by com.maluku.teyfey.App.instance.repo.tvMargin.collectAsState()
    val tv = isTv()
    val h = when {
        margin >= 0 -> margin
        tv -> 48
        else -> 0
    }
    return androidx.compose.foundation.layout.PaddingValues(horizontal = h.dp, vertical = (h * 0.56f).dp)
}


/** Extra menu row in the TV sidebar (search, guide, settings…), reachable with ▲▼. */
@Composable
fun ToolRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .tvClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Z.textDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Z.textDim, fontSize = 14.sp)
    }
}

@Composable
fun SidebarDivider() {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(Z.surfaceHi))
}


/** White ring around a Material button while the TV remote is on it. */
@Composable
fun Modifier.focusRing(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(50)): Modifier {
    var focused by remember { androidx.compose.runtime.mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.hasFocus }
        .border(if (focused) 4.dp else 0.dp, if (focused) Z.focus else Color.Transparent, shape)
}


/** Yellow circle around small icon buttons (search, settings…) while the remote is on them. */
@Composable
fun Modifier.tvIconFocus(): Modifier {
    var focused by remember { androidx.compose.runtime.mutableStateOf(false) }
    val shape = androidx.compose.foundation.shape.CircleShape
    return this
        .onFocusChanged { focused = it.hasFocus }
        .background(if (focused) Z.focus.copy(alpha = 0.35f) else Color.Transparent, shape)
        .border(if (focused) 3.dp else 0.dp, if (focused) Z.focus else Color.Transparent, shape)
}
