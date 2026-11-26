package com.maluku.teyfey.data

import java.io.InputStream

object M3uParser {
    private val attrRegex = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    class Result(val channels: List<Channel>, val epgUrl: String?)

    fun parse(input: InputStream): Result {
        val out = ArrayList<Channel>()
        var epgUrl: String? = null
        var name: String? = null
        var attrs: Map<String, String> = emptyMap()
        var extGroup: String? = null

        input.bufferedReader().forEachLine { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U") -> {
                    val a = attrs(line)
                    epgUrl = (a["x-tvg-url"] ?: a["url-tvg"])?.split(',')?.firstOrNull()?.trim()
                }
                line.startsWith("#EXTINF") -> {
                    attrs = attrs(line)
                    val lastQuote = line.lastIndexOf('"').coerceAtLeast(0)
                    val comma = line.indexOf(',', lastQuote)
                    name = if (comma >= 0) line.substring(comma + 1).trim() else null
                    extGroup = null
                }
                line.startsWith("#EXTGRP:") -> extGroup = line.removePrefix("#EXTGRP:").trim()
                line.startsWith("#") -> Unit
                else -> {
                    val n = name
                    if (n != null || line.contains("://")) {
                        val title = n?.takeIf { it.isNotBlank() } ?: attrs["tvg-name"] ?: "Kanaal ${out.size + 1}"
                        val group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: extGroup ?: "Overig"
                        out.add(
                            Channel(
                                id = line,
                                number = attrs["tvg-chno"]?.toIntOrNull() ?: (out.size + 1),
                                name = title,
                                logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                                group = group,
                                url = line,
                                epgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                            )
                        )
                    }
                    name = null
                    attrs = emptyMap()
                    extGroup = null
                }
            }
        }
        // Make ids unique if the same url appears twice.
        val seen = HashMap<String, Int>()
        val unique = out.map { ch ->
            val c = seen.getOrDefault(ch.id, 0)
            seen[ch.id] = c + 1
            if (c == 0) ch else ch.copy(id = "${ch.id}#$c")
        }
        return Result(unique, epgUrl)
    }

    private fun attrs(line: String): Map<String, String> =
        attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
}
