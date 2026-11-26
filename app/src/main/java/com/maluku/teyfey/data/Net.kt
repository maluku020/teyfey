package com.maluku.teyfey.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

object Net {
    const val USER_AGENT = "Teyfey/1.0 (Linux; Android) ExoPlayer"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    /** Opens a URL and hands the (transparently un-gzipped) body to [block]. */
    fun <T> open(url: String, block: (InputStream) -> T): T {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Server gaf fout ${resp.code}")
            val body = resp.body ?: throw IOException("Leeg antwoord van server")
            val raw = BufferedInputStream(body.byteStream(), 64 * 1024)
            raw.mark(4)
            val b1 = raw.read()
            val b2 = raw.read()
            raw.reset()
            val stream: InputStream =
                if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(raw), 64 * 1024) else raw
            return block(stream)
        }
    }

    fun getText(url: String): String = open(url) { it.bufferedReader().readText() }
}
