package com.maluku.teyfey.data

import org.json.JSONObject

data class Channel(
    val id: String,          // stable key (stream url)
    val number: Int,
    val name: String,
    val logo: String?,
    val group: String,
    val url: String,
    val epgId: String?,
    /** Days the provider keeps this channel for catch-up (0 = no catch-up). */
    val archiveDays: Int = 0,
)

data class Program(
    val start: Long,
    val stop: Long,
    val title: String,
    val desc: String?,
) {
    fun isLive(now: Long) = now in start until stop
    fun progress(now: Long): Float =
        if (stop <= start) 0f else ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}

enum class SourceType { M3U, XTREAM }

data class PlaylistConfig(
    val type: SourceType,
    val m3uUrl: String = "",
    val server: String = "",
    val user: String = "",
    val pass: String = "",
    val epgUrl: String = "",
    val xtreamFormat: String = "ts",
) {
    fun toJson(): String = JSONObject()
        .put("type", type.name)
        .put("m3uUrl", m3uUrl)
        .put("server", server)
        .put("user", user)
        .put("pass", pass)
        .put("epgUrl", epgUrl)
        .put("xtreamFormat", xtreamFormat)
        .toString()

    companion object {
        fun fromJson(s: String?): PlaylistConfig? {
            if (s.isNullOrBlank()) return null
            return try {
                val o = JSONObject(s)
                PlaylistConfig(
                    type = SourceType.valueOf(o.optString("type", "M3U")),
                    m3uUrl = o.optString("m3uUrl"),
                    server = o.optString("server"),
                    user = o.optString("user"),
                    pass = o.optString("pass"),
                    epgUrl = o.optString("epgUrl"),
                    xtreamFormat = o.optString("xtreamFormat", "ts"),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Finds the programme airing at [now] in a list sorted by start time. */
fun List<Program>.liveAt(now: Long): Program? {
    var lo = 0
    var hi = size - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val p = this[mid]
        when {
            now < p.start -> hi = mid - 1
            now >= p.stop -> lo = mid + 1
            else -> return p
        }
    }
    return null
}

fun List<Program>.nextAfter(now: Long): Program? = firstOrNull { it.start >= now }

fun normalizeName(s: String): String =
    s.lowercase()
        .replace(Regex("""\b(hd|fhd|uhd|4k|sd|hevc|h265)\b"""), "")
        .replace(Regex("""[^a-z0-9]"""), "")
