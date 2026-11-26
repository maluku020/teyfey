package com.maluku.teyfey.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

enum class VodKind { MOVIES, SERIES }

/** Films + series for Xtream sources, with a disk cache and "continue watching". */
class VodRepository(ctx: Context, private val repo: Repository) {

    private val prefs = ctx.getSharedPreferences("teyfey_vod", Context.MODE_PRIVATE)
    private val dir = ctx.filesDir
    private val mutex = Mutex()

    class Library(val categories: List<Category> = emptyList(), val items: List<VodItem> = emptyList())

    private val _movies = MutableStateFlow(Library())
    val movies: StateFlow<Library> = _movies
    private val _series = MutableStateFlow(Library())
    val series: StateFlow<Library> = _series

    private val _loading = MutableStateFlow<VodKind?>(null)
    val loading: StateFlow<VodKind?> = _loading

    /** Bumps whenever a resume position changes so lists can refresh. */
    private val _progressTick = MutableStateFlow(0)
    val progressTick: StateFlow<Int> = _progressTick

    private val movieInfoCache = HashMap<String, MovieInfo>()
    private val seriesInfoCache = HashMap<String, SeriesInfo>()

    fun supported(): Boolean = repo.config.value?.type == SourceType.XTREAM

    fun library(kind: VodKind) = if (kind == VodKind.MOVIES) movies else series

    private fun file(kind: VodKind, what: String) = File(dir, "vod_${kind.name.lowercase()}_$what.json")

    /** Load from memory/disk, or from the server when missing, stale (24h) or forced. */
    suspend fun load(kind: VodKind, force: Boolean = false) = mutex.withLock {
        val cfg = repo.config.value ?: return@withLock
        if (cfg.type != SourceType.XTREAM) return@withLock
        val flow = if (kind == VodKind.MOVIES) _movies else _series
        val timeKey = "time_${kind.name}_${cfg.server}_${cfg.user}"
        val fresh = System.currentTimeMillis() - prefs.getLong(timeKey, 0) < 24 * 3_600_000L

        withContext(Dispatchers.IO) {
            if (!force && flow.value.items.isNotEmpty() && fresh) return@withContext
            if (!force && fresh) {
                val ok = runCatching {
                    flow.value = Library(
                        XtreamVod.catsFromJson(file(kind, "cats").readText()),
                        XtreamVod.readItems(file(kind, "items")),
                    )
                }.isSuccess
                if (ok && flow.value.items.isNotEmpty()) return@withContext
            }
            _loading.value = kind
            try {
                val cats = if (kind == VodKind.MOVIES) XtreamVod.movieCategories(cfg) else XtreamVod.seriesCategories(cfg)
                val items = if (kind == VodKind.MOVIES) XtreamVod.movies(cfg) else XtreamVod.series(cfg)
                if (items.isEmpty()) {
                    throw java.io.IOException(
                        "De server gaf 0 " + (if (kind == VodKind.MOVIES) "films" else "series") + " terug (" +
                            cats.size + " categorieën)."
                    )
                }
                flow.value = Library(cats, items)
                file(kind, "cats").writeText(XtreamVod.catsToJson(cats))
                XtreamVod.writeItems(file(kind, "items"), items)
                prefs.edit().putLong(timeKey, System.currentTimeMillis()).apply()
            } finally {
                _loading.value = null
            }
        }
    }

    /** Called when the login changes. */
    fun clear() {
        _movies.value = Library()
        _series.value = Library()
        movieInfoCache.clear()
        seriesInfoCache.clear()
        VodKind.entries.forEach { k -> file(k, "cats").delete(); file(k, "items").delete() }
    }

    suspend fun movieInfo(id: String): MovieInfo {
        movieInfoCache[id]?.let { return it }
        val cfg = repo.config.value ?: error("Geen login")
        return withContext(Dispatchers.IO) { XtreamVod.movieInfo(cfg, id) }.also { movieInfoCache[id] = it }
    }

    suspend fun seriesInfo(id: String): SeriesInfo {
        seriesInfoCache[id]?.let { return it }
        val cfg = repo.config.value ?: error("Geen login")
        return withContext(Dispatchers.IO) { XtreamVod.seriesInfo(cfg, id) }.also { seriesInfoCache[id] = it }
    }

    fun movieUrl(item: VodItem) = XtreamVod.movieUrl(repo.config.value!!, item)
    fun episodeUrl(ep: Episode) = XtreamVod.episodeUrl(repo.config.value!!, ep)

    // ---------- resume positions ----------

    fun savePosition(key: String, posMs: Long, durMs: Long) {
        if (durMs <= 0) return
        val done = posMs > durMs * 0.95
        prefs.edit()
            .putLong("pos_$key", if (done) 0 else posMs)
            .putLong("dur_$key", durMs)
            .apply()
        _progressTick.value++
    }

    fun position(key: String): Long = prefs.getLong("pos_$key", 0)

    /** 0..1 watched fraction, or null when never started / finished. */
    fun progress(key: String): Float? {
        val p = prefs.getLong("pos_$key", 0)
        val d = prefs.getLong("dur_$key", 0)
        return if (p > 0 && d > 0) (p.toFloat() / d).coerceIn(0f, 1f) else null
    }

    fun watched(key: String): Boolean =
        prefs.getLong("dur_$key", 0) > 0 && prefs.getLong("pos_$key", 0) == 0L

    // ---------- recently watched (for the "Verder kijken" row) ----------

    fun markRecent(kind: VodKind, id: String) {
        val k = "recent_${kind.name}"
        val list = recentIds(kind).toMutableList()
        list.remove(id)
        list.add(0, id)
        prefs.edit().putString(k, JSONArray(list.take(40)).toString()).apply()
        _progressTick.value++
    }

    fun recentIds(kind: VodKind): List<String> = runCatching {
        val a = JSONArray(prefs.getString("recent_${kind.name}", "[]"))
        List(a.length()) { a.getString(it) }
    }.getOrDefault(emptyList())

    fun setLastEpisode(seriesId: String, episodeId: String) {
        prefs.edit().putString("lastep_$seriesId", episodeId).apply()
    }

    fun lastEpisode(seriesId: String): String? = prefs.getString("lastep_$seriesId", null)
}
