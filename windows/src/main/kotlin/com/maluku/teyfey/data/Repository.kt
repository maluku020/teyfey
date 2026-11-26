package com.maluku.teyfey.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

class Repository {

    private val prefs = Prefs.get("teyfey")
    private val channelsFile = File(AppDirs.data, "channels.json")
    private val epgFile = File(AppDirs.data, "epg.bin")
    private val mutex = Mutex()

    private val _config = MutableStateFlow(PlaylistConfig.fromJson(prefs.getString("config", null)))
    val config: StateFlow<PlaylistConfig?> = _config

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels

    private val _epg = MutableStateFlow<Map<String, List<Program>>>(emptyMap())
    val epg: StateFlow<Map<String, List<Program>>> = _epg

    private val _favorites = MutableStateFlow(prefs.getStringSet("favorites", emptySet())!!.toSet())
    val favorites: StateFlow<Set<String>> = _favorites

    private val _account = MutableStateFlow(AccountInfo.fromJson(prefs.getString("account", null)))
    val account: StateFlow<AccountInfo?> = _account

    /** Refreshes subscription details (Xtream only). */
    suspend fun refreshAccount() {
        val cfg = _config.value ?: return
        if (cfg.type != SourceType.XTREAM) return
        withContext(Dispatchers.IO) {
            val acc = Xtream.account(cfg)
            _account.value = acc
            prefs.edit().putString("account", acc.toJson()).apply()
        }
    }

    /** Human readable status line ("EPG laden…"), null when idle. */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy

    var lastChannelId: String?
        get() = prefs.getString("lastChannel", null)
        set(v) { prefs.edit().putString("lastChannel", v).apply() }

    var lastGroup: String?
        get() = prefs.getString("lastGroup", null)
        set(v) { prefs.edit().putString("lastGroup", v).apply() }

    private var m3uEpgUrl: String?
        get() = prefs.getString("m3uEpgUrl", null)
        set(v) { prefs.edit().putString("m3uEpgUrl", v).apply() }

    /** "nl", "en", … or "orig" */
    var audioLang: String
        get() = prefs.getString("audioLang", "orig") ?: "orig"
        set(v) { prefs.edit().putString("audioLang", v).apply() }

    /** "nl", "en", … or "off" */
    var subLang: String
        get() = prefs.getString("subLang", "nl") ?: "nl"
        set(v) { prefs.edit().putString("subLang", v).apply() }

    /** Edge space on TVs (overscan). -1 = automatic (48 dp on a TV, 0 on a phone). */
    private val _tvMargin = MutableStateFlow(prefs.getInt("tvMargin", -1))
    val tvMargin: StateFlow<Int> = _tvMargin
    fun setTvMargin(v: Int) {
        prefs.edit().putInt("tvMargin", v).apply()
        _tvMargin.value = v
    }

    /** Sound of the small preview on Live TV (full screen always has sound). */
    private val _previewMuted = MutableStateFlow(prefs.getInt("previewMuted", 0) == 1)
    val previewMuted: StateFlow<Boolean> = _previewMuted
    fun setPreviewMuted(m: Boolean) {
        prefs.edit().putInt("previewMuted", if (m) 1 else 0).apply()
        _previewMuted.value = m
    }

    fun toggleFavorite(id: String) {
        val s = _favorites.value.toMutableSet()
        if (!s.add(id)) s.remove(id)
        _favorites.value = s
        prefs.edit().putStringSet("favorites", s).apply()
    }

    /** Saves a new source. A changed source forces a fresh TV guide (done by [start]). */
    fun saveConfig(cfg: PlaylistConfig) {
        if (cfg.type != SourceType.XTREAM) {
            _account.value = null
            prefs.edit().remove("account").apply()
        }
        if (cfg != _config.value) {
            _epg.value = emptyMap()
            epgFile.delete()
            prefs.edit().putLong("epgTime", 0).apply()
        }
        prefs.edit().putString("config", cfg.toJson()).apply()
        _config.value = cfg
    }

    /** Loads cached channels + EPG from disk, then refreshes what is stale. */
    suspend fun start() {
        withContext(Dispatchers.IO) {
            if (_channels.value.isEmpty()) {
                runCatching { _channels.value = readChannels() }
                runCatching { _epg.value = readEpg() }
            }
        }
        val cfg = _config.value ?: return
        val age = System.currentTimeMillis() - prefs.getLong("playlistTime", 0)
        val oldFormat = prefs.getInt("chFormat", 1) < 2
        if (_channels.value.isEmpty() || age > 24 * 3_600_000L || oldFormat) {
            runCatching { refreshPlaylist(cfg) }
        } else {
            runCatching { refreshAccount() }
        }
        val epgAge = System.currentTimeMillis() - prefs.getLong("epgTime", 0)
        if (_epg.value.isEmpty() || epgAge > 8 * 3_600_000L) {
            runCatching { refreshEpg() }
        }
    }

    /** Downloads the playlist. Throws with a readable message on failure. */
    suspend fun refreshPlaylist(cfg: PlaylistConfig = _config.value!!) = mutex.withLock {
        withContext(Dispatchers.IO) {
            _busy.value = "Kanalen laden…"
            try {
                val list = when (cfg.type) {
                    SourceType.M3U -> {
                        val r = Net.open(cfg.m3uUrl.trim()) { M3uParser.parse(it) }
                        m3uEpgUrl = r.epgUrl
                        r.channels
                    }
                    SourceType.XTREAM -> {
                        val acc = Xtream.account(cfg) // also checks the login
                        _account.value = acc
                        prefs.edit().putString("account", acc.toJson()).apply()
                        Xtream.load(cfg)
                    }
                }
                if (list.isEmpty()) throw IllegalStateException("Geen kanalen gevonden in deze playlist.")
                _channels.value = list
                writeChannels(list)
                prefs.edit().putLong("playlistTime", System.currentTimeMillis()).putInt("chFormat", 2).apply()
            } finally {
                _busy.value = null
            }
        }
    }

    /** Programmes you can still watch back on [channel] (newest first). */
    suspend fun catchup(channel: Channel): List<Program> {
        val cfg = _config.value ?: return emptyList()
        if (cfg.type != SourceType.XTREAM || channel.archiveDays <= 0) return emptyList()
        val sid = channel.id.removePrefix("x:")
        val now = System.currentTimeMillis()
        val from = now - channel.archiveDays * 86_400_000L
        return withContext(Dispatchers.IO) { Xtream.catchupPrograms(cfg, sid) }
            .filter { it.stop <= now && it.start >= from }
            .sortedByDescending { it.start }
    }

    fun catchupUrl(channel: Channel, p: Program): String =
        Xtream.catchupUrl(_config.value!!, channel.id.removePrefix("x:"), p, _account.value?.timezone)

    fun epgSource(): String? {
        val cfg = _config.value ?: return null
        if (cfg.epgUrl.isNotBlank()) return cfg.epgUrl.trim()
        return when (cfg.type) {
            SourceType.XTREAM -> Xtream.epgUrl(cfg)
            SourceType.M3U -> m3uEpgUrl
        }
    }

    suspend fun refreshEpg() = mutex.withLock {
        val url = epgSource() ?: return@withLock
        val channels = _channels.value
        if (channels.isEmpty()) return@withLock
        withContext(Dispatchers.IO) {
            _busy.value = "TV-gids laden…"
            try {
                val map = Net.open(url) { EpgParser.parse(it, channels) }
                _epg.value = map
                writeEpg(map)
                prefs.edit().putLong("epgTime", System.currentTimeMillis()).apply()
            } finally {
                _busy.value = null
            }
        }
    }

    // ---------- cache ----------

    private fun writeChannels(list: List<Channel>) {
        val arr = JSONArray()
        for (c in list) {
            arr.put(
                JSONObject()
                    .put("id", c.id).put("n", c.number).put("name", c.name)
                    .put("logo", c.logo ?: "").put("g", c.group).put("url", c.url)
                    .put("epg", c.epgId ?: "")
                    .put("ad", c.archiveDays)
            )
        }
        channelsFile.writeText(arr.toString())
    }

    private fun readChannels(): List<Channel> {
        if (!channelsFile.exists()) return emptyList()
        val arr = JSONArray(channelsFile.readText())
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Channel(
                id = o.getString("id"),
                number = o.optInt("n", i + 1),
                name = o.optString("name"),
                logo = o.optString("logo").ifBlank { null },
                group = o.optString("g", "Overig"),
                url = o.getString("url"),
                epgId = o.optString("epg").ifBlank { null },
                archiveDays = o.optInt("ad", 0),
            )
        }
    }

    private fun writeEpg(map: Map<String, List<Program>>) {
        DataOutputStream(epgFile.outputStream().buffered()).use { out ->
            out.writeInt(map.size)
            for ((id, list) in map) {
                out.writeUTF(id)
                out.writeInt(list.size)
                for (p in list) {
                    out.writeLong(p.start)
                    out.writeLong(p.stop)
                    out.writeUTF(p.title.take(500))
                    out.writeUTF((p.desc ?: "").take(2000))
                }
            }
        }
    }

    private fun readEpg(): Map<String, List<Program>> {
        if (!epgFile.exists()) return emptyMap()
        val now = System.currentTimeMillis()
        DataInputStream(epgFile.inputStream().buffered()).use { inp ->
            val n = inp.readInt()
            val map = HashMap<String, List<Program>>(n)
            repeat(n) {
                val id = inp.readUTF()
                val count = inp.readInt()
                val list = ArrayList<Program>(count)
                repeat(count) {
                    val p = Program(inp.readLong(), inp.readLong(), inp.readUTF(), inp.readUTF().ifBlank { null })
                    if (p.stop > now - 3 * 3_600_000L) list.add(p)
                }
                if (list.isNotEmpty()) map[id] = list
            }
            return map
        }
    }
}
