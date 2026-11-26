package com.maluku.teyfey.data

import org.json.JSONArray
import java.io.File
import java.util.Properties

/** Where Teyfey keeps its files on Windows: %APPDATA%\Teyfey (or ~/.teyfey elsewhere). */
object AppDirs {
    val data: File by lazy {
        val appData = System.getenv("APPDATA")
        val dir = if (!appData.isNullOrBlank()) File(appData, "Teyfey") else File(System.getProperty("user.home"), ".teyfey")
        dir.mkdirs()
        dir
    }
}

/**
 * Tiny replacement for Android's SharedPreferences: a properties file per name,
 * written to disk on every apply().
 */
class Prefs private constructor(private val file: File) {
    private val props = Properties()

    init {
        if (file.exists()) runCatching { file.inputStream().use { props.load(it) } }
    }

    @Synchronized fun getString(key: String, def: String?): String? = props.getProperty(key) ?: def
    @Synchronized fun getLong(key: String, def: Long): Long = props.getProperty(key)?.toLongOrNull() ?: def
    @Synchronized fun getInt(key: String, def: Int): Int = props.getProperty(key)?.toIntOrNull() ?: def
    @Synchronized fun getStringSet(key: String, def: Set<String>?): Set<String>? {
        val raw = props.getProperty(key) ?: return def
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getString(it) }.toSet()
        }.getOrDefault(def)
    }

    fun edit() = Editor()

    inner class Editor {
        private val changes = HashMap<String, String?>()
        fun putString(key: String, v: String?) = apply { changes[key] = v }
        fun putLong(key: String, v: Long) = apply { changes[key] = v.toString() }
        fun putInt(key: String, v: Int) = apply { changes[key] = v.toString() }
        fun putStringSet(key: String, v: Set<String>) = apply { changes[key] = JSONArray(v.toList()).toString() }
        fun remove(key: String) = apply { changes[key] = null }
        fun apply() {
            synchronized(this@Prefs) {
                for ((k, v) in changes) if (v == null) props.remove(k) else props.setProperty(k, v)
                runCatching { file.outputStream().use { props.store(it, "Teyfey") } }
            }
        }
    }

    companion object {
        private val cache = HashMap<String, Prefs>()
        @Synchronized fun get(name: String): Prefs = cache.getOrPut(name) { Prefs(File(AppDirs.data, "$name.properties")) }
    }
}
