package com.maluku.teyfey.data

import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import java.io.InputStream
import java.util.Calendar
import java.util.TimeZone

/**
 * Streaming XMLTV parser. Keeps only programmes for channels we actually have,
 * inside a window around "now", so even huge guides stay light on memory.
 * Returns programmes keyed by [Channel.id].
 */
object EpgParser {

    fun parse(input: InputStream, channels: List<Channel>, now: Long = System.currentTimeMillis()): Map<String, List<Program>> {
        val from = now - 3 * 3_600_000L
        val to = now + 36 * 3_600_000L

        // tvg-id -> channel ids, normalized name -> channel ids
        val byEpgId = HashMap<String, MutableList<String>>()
        val byName = HashMap<String, MutableList<String>>()
        for (c in channels) {
            c.epgId?.let { byEpgId.getOrPut(it.lowercase()) { ArrayList() }.add(c.id) }
            byName.getOrPut(normalizeName(c.name)) { ArrayList() }.add(c.id)
        }
        // xmltv channel id -> our channel ids (resolved while reading <channel>)
        val resolved = HashMap<String, List<String>>()
        val result = HashMap<String, MutableList<Program>>()

        fun targetsFor(xmlId: String): List<String> =
            resolved.getOrPut(xmlId) {
                byEpgId[xmlId.lowercase()] ?: byName[normalizeName(xmlId)] ?: emptyList()
            }

        val factory = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        }
        val r = factory.createXMLStreamReader(input)

        var channelId: String? = null
        var inProgramme = false
        var pChannel = ""
        var pStart = 0L
        var pStop = 0L
        var pTitle: String? = null
        var pDesc: String? = null

        try {
            while (r.hasNext()) {
                when (r.next()) {
                    XMLStreamConstants.START_ELEMENT -> when (r.localName) {
                        "channel" -> channelId = r.getAttributeValue(null, "id")
                        "display-name" -> {
                            val id = channelId
                            if (id != null) {
                                val text = r.elementText
                                if (byEpgId[id.lowercase()] == null) {
                                    val hits = byName[normalizeName(text)]
                                    if (hits != null) {
                                        val existing = resolved[id].orEmpty()
                                        resolved[id] = (existing + hits).distinct()
                                    }
                                }
                            }
                        }
                        "programme" -> {
                            inProgramme = true
                            pChannel = r.getAttributeValue(null, "channel") ?: ""
                            pStart = parseTime(r.getAttributeValue(null, "start"))
                            pStop = parseTime(r.getAttributeValue(null, "stop"))
                            pTitle = null
                            pDesc = null
                        }
                        "title" -> if (inProgramme && pTitle == null) pTitle = r.elementText
                        "desc" -> if (inProgramme && pDesc == null) pDesc = r.elementText
                    }
                    XMLStreamConstants.END_ELEMENT -> when (r.localName) {
                        "channel" -> channelId = null
                        "programme" -> {
                            inProgramme = false
                            if (pStop > from && pStart < to && pStop > pStart) {
                                val targets = targetsFor(pChannel)
                                if (targets.isNotEmpty()) {
                                    val p = Program(pStart, pStop, pTitle?.trim().orEmpty().ifBlank { "Geen titel" }, pDesc?.trim())
                                    for (t in targets) result.getOrPut(t) { ArrayList() }.add(p)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Broken/truncated guide: keep whatever we parsed so far.
            if (result.isEmpty()) throw e
        }

        return result.mapValues { (_, list) ->
            list.sortBy { it.start }
            // drop overlaps/duplicates
            val clean = ArrayList<Program>(list.size)
            for (p in list) {
                val last = clean.lastOrNull()
                if (last == null || p.start >= last.stop) clean.add(p)
            }
            clean
        }
    }

    /** XMLTV time: "20261115180000 +0100" (timezone optional, default UTC). */
    fun parseTime(s: String?): Long {
        if (s == null || s.length < 12) return 0L
        return try {
            val y = s.substring(0, 4).toInt()
            val mo = s.substring(4, 6).toInt()
            val d = s.substring(6, 8).toInt()
            val h = s.substring(8, 10).toInt()
            val mi = s.substring(10, 12).toInt()
            val se = if (s.length >= 14 && s[12].isDigit()) s.substring(12, 14).toInt() else 0
            val tz = s.substring(minOf(s.length, 14)).trim()
            var offsetMin = 0
            if (tz.length >= 5 && (tz[0] == '+' || tz[0] == '-')) {
                val sign = if (tz[0] == '-') -1 else 1
                offsetMin = sign * (tz.substring(1, 3).toInt() * 60 + tz.substring(3, 5).toInt())
            }
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.clear()
            cal.set(y, mo - 1, d, h, mi, se)
            cal.timeInMillis - offsetMin * 60_000L
        } catch (e: Exception) {
            0L
        }
    }
}
