package com.maluku.teyfey.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maluku.teyfey.App
import com.maluku.teyfey.data.SourceType
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onChangeSource: () -> Unit) {
    val app = App.instance
    val repo = app.repo
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by repo.config.collectAsStateWithLifecycle()
    val channels by repo.channels.collectAsStateWithLifecycle()
    val epg by repo.epg.collectAsStateWithLifecycle()
    val busy by repo.busy.collectAsStateWithLifecycle()
    val account by repo.account.collectAsStateWithLifecycle()

    fun run(label: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                Toast.makeText(ctx, "$label klaar", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "Mislukt", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.tvIconFocus()) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Terug", tint = Z.text) }
            Text("Instellingen", color = Z.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.widthIn(max = 640.dp).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val cfg = config
            SectionTitle("Bron")
            if (cfg != null) {
                Info(
                    if (cfg.type == SourceType.XTREAM) "Xtream: ${cfg.server}" else "M3U: ${cfg.m3uUrl.take(60)}",
                    if (cfg.type == SourceType.XTREAM) "Gebruiker: ${cfg.user}" else null,
                )
            }
            Info("${channels.size} kanalen", "TV-gids voor ${epg.size} kanalen")

            account?.let { acc ->
                SectionTitle("Abonnement")
                val days = acc.daysLeft()
                Info(
                    when {
                        acc.expiresAt == null -> "Geldig: onbeperkt"
                        days != null && days < 0 -> "Verlopen op ${dateOf(acc.expiresAt)}"
                        else -> "Geldig tot ${dateOf(acc.expiresAt)}"
                    },
                    when {
                        days == null -> null
                        days < 0 -> "Verleng je abonnement bij je aanbieder"
                        days == 0L -> "Loopt vandaag af"
                        else -> "Nog $days ${if (days == 1L) "dag" else "dagen"}"
                    },
                )
                Info(
                    "Status: ${acc.status ?: "onbekend"}${if (acc.trial) " (proefperiode)" else ""}",
                    acc.maxConnections?.let { m -> "Schermen tegelijk: ${acc.activeConnections ?: 0} van $m in gebruik" },
                )
                Action("Abonnement verversen", null) { run("Abonnement") { repo.refreshAccount() } }
            }
            busy?.let { Info(it, null) }

            Action("Andere login / playlist", "Zet een nieuwe gebruiker of link erop") { onChangeSource() }
            Action("Kanalen vernieuwen", null) { run("Kanalen vernieuwen") { repo.refreshPlaylist() } }
            Action("TV-gids vernieuwen", repo.epgSource()?.let { "Bron: ${it.take(50)}…" }) {
                run("TV-gids") { repo.refreshEpg() }
            }

            if (cfg != null && cfg.type == SourceType.XTREAM) {
                SectionTitle("Afspelen")
                val hls = cfg.xtreamFormat == "m3u8"
                Action(
                    "Stream-formaat: ${if (hls) "HLS (m3u8)" else "MPEG-TS"}",
                    "Tik om te wisselen. TS zapt sneller, HLS werkt beter met casten naar Chromecast.",
                ) {
                    val new = cfg.copy(xtreamFormat = if (hls) "ts" else "m3u8")
                    run("Formaat wisselen") {
                        repo.refreshPlaylist(new)
                        repo.saveConfig(new)
                        app.playback.current.value?.let { cur ->
                            repo.channels.value.firstOrNull { it.id == cur.id }?.let { app.playback.play(it) }
                        }
                    }
                }
            }

            SectionTitle("Films en series")
            var audio by remember { mutableStateOf(repo.audioLang) }
            var subs by remember { mutableStateOf(repo.subLang) }
            val audioOpts = listOf("orig" to "Origineel", "nl" to "Nederlands", "en" to "Engels", "tr" to "Turks", "de" to "Duits", "fr" to "Frans", "ar" to "Arabisch")
            val subOpts = listOf("nl" to "Nederlands", "en" to "Engels", "off" to "Uit", "tr" to "Turks", "de" to "Duits", "fr" to "Frans", "ar" to "Arabisch")
            Action(
                "Audiotaal: ${audioOpts.firstOrNull { it.first == audio }?.second ?: audio}",
                "Tik om te wisselen. Werkt als de film meerdere talen heeft.",
            ) {
                val i = audioOpts.indexOfFirst { it.first == audio }
                audio = audioOpts[(i + 1) % audioOpts.size].first
                repo.audioLang = audio
            }
            Action(
                "Ondertitels: ${subOpts.firstOrNull { it.first == subs }?.second ?: subs}",
                "Tik om te wisselen. Tijdens het kijken kun je ook kiezen met de CC-knop in de speler.",
            ) {
                val i = subOpts.indexOfFirst { it.first == subs }
                subs = subOpts[(i + 1) % subOpts.size].first
                repo.subLang = subs
            }

            SectionTitle("Beeld")
            val tvMargin by repo.tvMargin.collectAsStateWithLifecycle()
            val marginOpts = listOf(-1 to "Automatisch", 0 to "Uit", 24 to "Klein", 48 to "Normaal", 72 to "Groot")
            Action(
                "Schermrand: ${marginOpts.firstOrNull { it.first == tvMargin }?.second ?: "${tvMargin} dp"}",
                "Valt er op je tv iets buiten beeld? Kies een grotere rand. Tik om te wisselen.",
            ) {
                val i = marginOpts.indexOfFirst { it.first == tvMargin }
                repo.setTvMargin(marginOpts[(i + 1) % marginOpts.size].first)
            }

            SectionTitle("Bediening")
            Info("Kanaal kiezen", "Eén keer klikken = voorbeeld, nog een keer = volledig scherm")
            Info("Favoriet", "Houd een kanaal lang ingedrukt (of lang OK op de afstandsbediening)")
            Info("Zappen", "Op volledig scherm: omhoog/omlaag vegen of ▲▼ op de afstandsbediening")
            Info("Taal en ondertitels kiezen", "In de filmspeler: CC-knop = ondertitels, tandwiel ⚙️ = audiotaal en snelheid")
            Spacer(Modifier.height(24.dp))
            Text("Teyfey 1.0", color = Z.textDim, fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Spacer(Modifier.height(14.dp))
    Text(title.uppercase(), color = Z.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Info(title: String, sub: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(title, color = Z.text, fontSize = 15.sp)
        sub?.let { Text(it, color = Z.textDim, fontSize = 13.sp) }
    }
}

@Composable
private fun Action(title: String, sub: String?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().tvClickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(title, color = Z.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        sub?.let { Text(it, color = Z.textDim, fontSize = 13.sp) }
    }
}
