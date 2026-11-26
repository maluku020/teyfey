package com.maluku.teyfey.data

import org.json.JSONArray
import org.json.JSONObject
import com.maluku.teyfey.data.JsonStream.v
import java.net.URLEncoder

data class Category(val id: String, val name: String)

/** A poster in the Films or Series grid. */
data class VodItem(
    val id: String,
    val name: String,
    val image: String?,
    val categoryId: String,
    val rating: String?,
    val ext: String = "mp4",   // movies only
    val added: Long = 0L,
    val year: String? = null,
)

data class MovieInfo(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val duration: String?,
    val year: String?,
    val backdrop: String?,
    val poster: String?,
    val rating: String?,
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String,
    val plot: String?,
    val duration: String?,
    val image: String?,
)

data class SeriesInfo(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val year: String?,
    val backdrop: String?,
    val poster: String?,
    val rating: String?,
    val seasons: Map<Int, List<Episode>>,
)

/** Xtream Codes API for films (VOD) and series. */
object XtreamVod {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun api(cfg: PlaylistConfig, action: String, extra: String = ""): String =
        "${Xtream.base(cfg.server)}/player_api.php?username=${enc(cfg.user)}&password=${enc(cfg.pass)}&action=$action$extra"

    fun movieUrl(cfg: PlaylistConfig, item: VodItem) =
        "${Xtream.base(cfg.server)}/movie/${cfg.user}/${cfg.pass}/${item.id}.${item.ext.ifBlank { "mp4" }}"

    fun episodeUrl(cfg: PlaylistConfig, ep: Episode) =
        "${Xtream.base(cfg.server)}/series/${cfg.user}/${cfg.pass}/${ep.id}.${ep.ext.ifBlank { "mp4" }}"

    fun movieCategories(cfg: PlaylistConfig) = categories(Net.getText(api(cfg, "get_vod_categories")))
    fun seriesCategories(cfg: PlaylistConfig) = categories(Net.getText(api(cfg, "get_series_categories")))

    private fun categories(json: String): List<Category> {
        val t = json.trimStart()
        if (!t.startsWith("[")) {
            throw java.io.IOException(
                if (t.isEmpty()) "De server gaf een leeg antwoord op de categorieën."
                else "De server gaf geen categorieën terug: " + t.take(120).replace("\n", " ")
            )
        }
        val arr = array(json)
        return List(arr.length()) { i ->
            val o = arr.optJSONObject(i) ?: JSONObject()
            Category(o.str("category_id") ?: "", o.str("category_name") ?: "Overig")
        }.filter { it.id.isNotEmpty() }.distinctBy { it.id }
    }

    /** Re-uses identical strings (category ids, extensions) to save memory on 100k+ lists. */
    private class Pool {
        private val m = HashMap<String, String>()
        operator fun invoke(s: String?): String? = s?.let { m.getOrPut(it) { it } }
    }

    fun movies(cfg: PlaylistConfig): List<VodItem> {
        val seen = HashSet<String>()
        val pool = Pool()
        return Net.open(api(cfg, "get_vod_streams")) { input ->
            JsonStream.readObjects(input) { o ->
                val id = o.v("stream_id") ?: return@readObjects null
                if (!seen.add(id)) return@readObjects null // same film listed twice
                VodItem(
                    id = id,
                    name = o.v("name") ?: "Film",
                    image = o.v("stream_icon") ?: o.v("cover"),
                    categoryId = pool(o.v("category_id")) ?: "",
                    rating = pool(o.v("rating")?.takeIf { it != "0" && it != "0.0" }),
                    ext = pool(o.v("container_extension")) ?: "mp4",
                    added = o.v("added")?.toLongOrNull() ?: 0L,
                    year = o.v("year"),
                )
            }
        }
    }

    fun series(cfg: PlaylistConfig): List<VodItem> {
        val seen = HashSet<String>()
        val pool = Pool()
        return Net.open(api(cfg, "get_series")) { input ->
            JsonStream.readObjects(input) { o ->
                val id = o.v("series_id") ?: return@readObjects null
                if (!seen.add(id)) return@readObjects null
                VodItem(
                    id = id,
                    name = o.v("name") ?: "Serie",
                    image = o.v("cover"),
                    categoryId = pool(o.v("category_id")) ?: "",
                    rating = pool(o.v("rating")?.takeIf { it != "0" && it != "0.0" }),
                    added = o.v("last_modified")?.toLongOrNull() ?: 0L,
                    year = (o.v("releaseDate") ?: o.v("release_date") ?: o.v("year"))?.take(4),
                )
            }
        }
    }

    fun movieInfo(cfg: PlaylistConfig, id: String): MovieInfo {
        val root = JSONObject(Net.getText(api(cfg, "get_vod_info", "&vod_id=$id")))
        val o = root.optJSONObject("info") ?: JSONObject()
        return MovieInfo(
            plot = o.str("plot") ?: o.str("description"),
            genre = o.str("genre"),
            cast = o.str("cast") ?: o.str("actors"),
            director = o.str("director"),
            duration = o.str("duration"),
            year = (o.str("releasedate") ?: o.str("release_date") ?: o.str("year"))?.take(4),
            backdrop = o.firstString("backdrop_path"),
            poster = o.str("movie_image") ?: o.str("cover_big"),
            rating = o.str("rating")?.takeIf { it != "0" && it != "0.0" },
        )
    }

    fun seriesInfo(cfg: PlaylistConfig, id: String): SeriesInfo {
        val root = JSONObject(Net.getText(api(cfg, "get_series_info", "&series_id=$id")))
        val o = root.optJSONObject("info") ?: JSONObject()
        val seasons = sortedMapOf<Int, MutableList<Episode>>()

        fun addEpisodes(seasonKey: Int?, arr: JSONArray) {
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val epId = e.str("id") ?: continue
                val season = e.str("season")?.toIntOrNull() ?: seasonKey ?: 1
                val info = e.optJSONObject("info")
                seasons.getOrPut(season) { ArrayList() }.add(
                    Episode(
                        id = epId,
                        season = season,
                        number = e.str("episode_num")?.toIntOrNull() ?: (i + 1),
                        title = e.str("title") ?: "Aflevering ${i + 1}",
                        ext = e.str("container_extension") ?: "mp4",
                        plot = info?.str("plot"),
                        duration = info?.str("duration"),
                        image = info?.str("movie_image"),
                    )
                )
            }
        }

        when (val eps = root.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { k -> eps.optJSONArray(k)?.let { addEpisodes(k.toIntOrNull(), it) } }
            is JSONArray -> for (i in 0 until eps.length()) eps.optJSONArray(i)?.let { addEpisodes(null, it) }
        }
        seasons.values.forEach { list -> list.sortBy { it.number } }

        return SeriesInfo(
            plot = o.str("plot"),
            genre = o.str("genre"),
            cast = o.str("cast"),
            year = (o.str("releaseDate") ?: o.str("release_date"))?.take(4),
            backdrop = o.firstString("backdrop_path"),
            poster = o.str("cover"),
            rating = o.str("rating")?.takeIf { it != "0" && it != "0.0" },
            seasons = seasons,
        )
    }

    // ---------- JSON helpers (Xtream panels are inconsistent with types) ----------

    private fun array(s: String): JSONArray = try {
        JSONArray(s)
    } catch (e: Exception) {
        JSONArray()
    }

    private fun JSONObject.str(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = opt(key)
        if (v is JSONArray || v is JSONObject) return null
        return v.toString().trim().takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun JSONObject.firstString(key: String): String? = when (val v = opt(key)) {
        is JSONArray -> (0 until v.length()).map { v.optString(it) }.firstOrNull { it.isNotBlank() }
        is String -> v.takeIf { it.isNotBlank() }
        else -> null
    }

    // ---------- cache (de)serialisation ----------

    fun writeItems(file: java.io.File, items: List<VodItem>) {
        android.util.JsonWriter(file.bufferedWriter()).use { w ->
            w.beginArray()
            for (v in items) {
                w.beginObject()
                w.name("i").value(v.id).name("n").value(v.name).name("p").value(v.image ?: "")
                    .name("c").value(v.categoryId).name("r").value(v.rating ?: "").name("e").value(v.ext)
                    .name("a").value(v.added.toString()).name("y").value(v.year ?: "")
                w.endObject()
            }
            w.endArray()
        }
    }

    fun readItems(file: java.io.File): List<VodItem> = file.inputStream().buffered().use { input ->
        val pool = Pool()
        JsonStream.readObjects(input) { o ->
            val id = o.v("i") ?: return@readObjects null
            VodItem(
                id = id, name = o["n"].orEmpty(), image = o.v("p"), categoryId = pool(o["c"]).orEmpty(),
                rating = pool(o.v("r")), ext = pool(o.v("e")) ?: "mp4", added = o.v("a")?.toLongOrNull() ?: 0L, year = o.v("y"),
            )
        }
    }

    fun catsToJson(c: List<Category>): String =
        JSONArray().also { a -> c.forEach { a.put(JSONObject().put("i", it.id).put("n", it.name)) } }.toString()

    fun catsFromJson(s: String): List<Category> {
        val a = JSONArray(s)
        return List(a.length()) { Category(a.getJSONObject(it).getString("i"), a.getJSONObject(it).optString("n")) }
    }
}
