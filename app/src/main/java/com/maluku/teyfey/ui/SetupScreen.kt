package com.maluku.teyfey.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maluku.teyfey.App
import com.maluku.teyfey.data.PlaylistConfig
import com.maluku.teyfey.data.SourceType
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(existing: PlaylistConfig?, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(if (existing?.type == SourceType.M3U) 1 else 0) }
    var server by rememberSaveable { mutableStateOf(existing?.server.orEmpty()) }
    var user by rememberSaveable { mutableStateOf(existing?.user.orEmpty()) }
    var pass by rememberSaveable { mutableStateOf(existing?.pass.orEmpty()) }
    var m3u by rememberSaveable { mutableStateOf(existing?.m3uUrl.orEmpty()) }
    var epg by rememberSaveable { mutableStateOf(existing?.epgUrl.orEmpty()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (onCancel != null) BackHandler(enabled = !loading) { onCancel() }

    fun submit() {
        val cfg = if (tab == 0) {
            PlaylistConfig(
                type = SourceType.XTREAM,
                server = server.trim(),
                user = user.trim(),
                pass = pass.trim(),
                epgUrl = epg.trim(),
                xtreamFormat = existing?.xtreamFormat ?: "ts",
            )
        } else {
            PlaylistConfig(type = SourceType.M3U, m3uUrl = m3u.trim(), epgUrl = epg.trim())
        }
        val missing = if (tab == 0) cfg.server.isBlank() || cfg.user.isBlank() || cfg.pass.isBlank() else cfg.m3uUrl.isBlank()
        if (missing) {
            error = "Vul alle velden in."
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                repo.refreshPlaylist(cfg)
                repo.saveConfig(cfg) // AppRoot then loads the TV guide in the background
                onDone()
            } catch (e: Exception) {
                error = e.message ?: "Laden mislukt. Controleer je gegevens en internet."
            } finally {
                loading = false
            }
        }
    }

    Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Brand(34.dp)
            Text("Log in bij je IPTV-abonnement", color = Z.textDim, fontSize = 15.sp)

            TabRow(selectedTabIndex = tab, containerColor = Z.panel) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Xtream login") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("M3U-link") })
            }

            if (tab == 0) {
                Field("Server (bv. http://line.voorbeeld.com)", server, KeyboardType.Uri) { server = it }
                Field("Gebruikersnaam", user) { user = it }
                Field("Wachtwoord", pass, KeyboardType.Password, password = true) { pass = it }
            } else {
                Field("Playlist-URL (.m3u)", m3u, KeyboardType.Uri) { m3u = it }
            }
            Field("TV-gids URL (optioneel, XMLTV)", epg, KeyboardType.Uri) { epg = it }

            error?.let { Text(it, color = Z.live, fontSize = 14.sp) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { submit() }, enabled = !loading, modifier = Modifier.focusRing()) {
                    Text(if (loading) "Bezig met laden…" else "Inloggen")
                }
                if (loading) {
                    Spacer(Modifier.size(12.dp))
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                }
                if (onCancel != null && !loading) {
                    Spacer(Modifier.size(8.dp))
                    TextButton(onClick = onCancel) { Text("Annuleren") }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Teyfey bevat zelf geen zenders. Je kijkt alleen wat je eigen abonnement aanbiedt.",
                color = Z.textDim,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    type: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}
