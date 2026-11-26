package com.maluku.teyfey.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import com.maluku.teyfey.data.JsonStream.v
import java.net.URLEncoder

object Xtream {
    fun base(server: String): String {
        val s = server.trim().trimEnd('/')
        return if (s.startsWith("http://") || s.startsWith("https://")) s else "http://$s"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun api(cfg: PlaylistConfig, action: String): String =
        "${base(cfg.server)}/player_api.php?username=${enc(cfg.user)}&password=${enc(cfg.pass)}&action=$action"

    fun epgUrl(cfg: PlaylistConfig): String =
        "${base(cfg.server)}/xmltv.php?username=${enc(cfg.user)}&password=${enc(cfg.pass)}"

    /** Logs in and returns the account details. Throws when the login is wrong. */
    fun account(cfg: PlaylistConfig): AccountInfo {
        val info = Net.getText("${base(cfg.server)}/player_api.php?username=${enc(cfg.user)}&password=${enc(cfg.pass)}")
        val u = try {
            JSONObject(info).optJSONObject("user_info")
        } catch (e: Exception) {
            null
        }
        if (u == null || u.optString("auth") != "1" && u.optInt("auth", 0) != 1) {
            throw IOException("Inloggen mislukt. Controleer server, gebruikersnaam en wachtwoord.")
        }
        fun s(k: String) = u.optString(k).takeIf { it.isNotBlank() && it != "null" }
        return AccountInfo(
            status = s("status"),
            expiresAt = s("exp_date")?.toLongOrNull()?.let { it * 1000 },
            maxConnections = s("max_connections")?.toIntOrNull(),
            activeConnections = s("active_cons")?.toIntOrNull(),
            trial = s("is_trial") == "1",
            createdAt = s("created_at")?.toLongOrNull()?.let { it * 1000 },
            timezone = runCatching { JSONObject(info).optJSONObject("server_info")?.optString("timezone") }
                .getOrNull()?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    // ------------------------------------------------------------ catch-up (terugkijken)

    /** Past (and coming) programmes of one channel, straight from the provider. */
    fun catchupPrograms(cfg: PlaylistConfig, streamId: String): List<Program> {
        val txt = Net.getText(api(cfg, "get_simple_data_table") + "&stream_id=" + streamId)
        val arr = try {
            JSONObject(txt).optJSONArray("epg_listings")
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        val out = ArrayList<Program>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val start = o.optString("start_timestamp").toLongOrNull()?.times(1000) ?: continue
            val stop = o.optString("stop_timestamp").toLongOrNull()?.times(1000) ?: continue
            if (stop <= start) continue
            out.add(
                Program(
                    start, stop,
                    b64(o.optString("title")).ifBlank { "Geen titel" },
                    b64(o.optString("description")).ifBlank { null },
                )
            )
        }
        return out.sortedBy { it.start }.distinctBy { it.start }
    }

    private fun b64(s: String): String = try {
        String(java.util.Base64.getDecoder().decode(s.trim()), Charsets.UTF_8)
    } catch (e: Exception) {
        s
    }

    /** Stream address of a past programme. Start time is in the server's time zone. */
    fun catchupUrl(cfg: PlaylistConfig, streamId: String, p: Program, timezone: String?): String {
        val zone = runCatching { java.time.ZoneId.of(timezone ?: "") }.getOrElse { java.time.ZoneId.systemDefault() }
        val start = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")
            .withZone(zone).format(java.time.Instant.ofEpochMilli(p.start))
        val minutes = ((p.stop - p.start) / 60_000L).coerceAtLeast(1) + 2
        return "${base(cfg.server)}/timeshift/${cfg.user}/${cfg.pass}/$minutes/$start/$streamId.ts"
    }

    fun load(cfg: PlaylistConfig): List<Channel> {
        val cats = parseArray(Net.getText(api(cfg, "get_live_categories")))
        val catNames = HashMap<String, String>()
        for (i in 0 until cats.length()) {
            val o = cats.optJSONObject(i) ?: continue
            catNames[o.optString("category_id")] = o.optString("category_name", "Overig")
        }

        val base = base(cfg.server)
        val ext = cfg.xtreamFormat.ifBlank { "ts" }
        val seen = HashSet<String>()
        var i = 0
        val out = Net.open(api(cfg, "get_live_streams")) { input ->
            JsonStream.readObjects(input) { o ->
                i++
                val id = o.v("stream_id") ?: return@readObjects null
                if (!seen.add(id)) return@readObjects null
                val url = "$base/live/${cfg.user}/${cfg.pass}/$id.$ext"
                Channel(
                    id = "x:$id", // stable even if stream format changes
                    number = o.v("num")?.toIntOrNull() ?: i,
                    name = (o.v("name") ?: "Kanaal $id"),
                    logo = o.v("stream_icon"),
                    group = catNames[o.v("category_id") ?: ""] ?: "Overig",
                    url = url,
                    epgId = o.v("epg_channel_id"),
                    archiveDays = if (o.v("tv_archive") == "1") (o.v("tv_archive_duration")?.toIntOrNull() ?: 1) else 0,
                )
            }
        }
        return out
    }

    private fun parseArray(s: String): JSONArray = try {
        JSONArray(s)
    } catch (e: Exception) {
        JSONArray()
    }
}

data class AccountInfo(
    val status: String?,
    val expiresAt: Long?,        // null = unlimited
    val maxConnections: Int?,
    val activeConnections: Int?,
    val trial: Boolean,
    val createdAt: Long?,
    val timezone: String? = null,
) {
    fun daysLeft(now: Long = System.currentTimeMillis()): Long? =
        expiresAt?.let { ((it - now) / 86_400_000L) }

    fun toJson(): String = JSONObject()
        .put("status", status ?: "")
        .put("exp", expiresAt ?: -1)
        .put("max", maxConnections ?: -1)
        .put("act", activeConnections ?: -1)
        .put("trial", trial)
        .put("created", createdAt ?: -1)
        .put("tz", timezone ?: "")
        .toString()

    companion object {
        fun fromJson(s: String?): AccountInfo? {
            if (s.isNullOrBlank()) return null
            return try {
                val o = JSONObject(s)
                AccountInfo(
                    status = o.optString("status").ifBlank { null },
                    expiresAt = o.optLong("exp", -1).takeIf { it > 0 },
                    maxConnections = o.optInt("max", -1).takeIf { it >= 0 },
                    activeConnections = o.optInt("act", -1).takeIf { it >= 0 },
                    trial = o.optBoolean("trial"),
                    createdAt = o.optLong("created", -1).takeIf { it > 0 },
                    timezone = o.optString("tz").ifBlank { null },
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
