package com.maluku.teyfey.data

import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Reads a huge JSON array of flat objects one object at a time, so lists with
 * tens of thousands of films never have to fit in memory as one big string.
 */
object JsonStream {
    fun <T> readObjects(input: InputStream, map: (Map<String, String>) -> T?): List<T> {
        val out = ArrayList<T>()
        val reader = JsonReader(InputStreamReader(input, Charsets.UTF_8))
        reader.isLenient = true
        try {
            val first = reader.peek()
            if (first != JsonToken.BEGIN_ARRAY) {
                throw java.io.IOException("Onverwacht antwoord van de server ($first in plaats van een lijst).")
            }
            reader.beginArray()
            while (reader.hasNext()) {
                if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                    reader.skipValue()
                    continue
                }
                val obj = HashMap<String, String>(24)
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    when (reader.peek()) {
                        JsonToken.STRING, JsonToken.NUMBER -> obj[name] = reader.nextString()
                        JsonToken.BOOLEAN -> obj[name] = reader.nextBoolean().toString()
                        JsonToken.NULL -> reader.nextNull()
                        else -> reader.skipValue() // nested arrays/objects are not needed in lists
                    }
                }
                reader.endObject()
                map(obj)?.let { out.add(it) }
            }
            reader.endArray()
        } catch (e: Exception) {
            if (out.isEmpty()) throw e // keep what we got from a truncated answer
        }
        return out
    }

    /** Value helper: blank / "null" -> null. */
    fun Map<String, String>.v(key: String): String? =
        this[key]?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
}
