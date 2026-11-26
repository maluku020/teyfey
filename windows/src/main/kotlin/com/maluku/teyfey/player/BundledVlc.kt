package com.maluku.teyfey.player

import uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryDirectoryProvider
import java.io.File

/**
 * Tells vlcj where to find VLC: first the copy that ships inside Teyfey
 * (app resources /vlc), then the normal 64-bit VLC install folders.
 */
class BundledVlcProvider : DiscoveryDirectoryProvider {
    override fun priority(): Int = 1000
    override fun supported(): Boolean = true
    override fun directories(): Array<String> = VlcLocations.candidates().filter { File(it, "libvlc.dll").exists() }.toTypedArray()
}

object VlcLocations {
    fun candidates(): List<String> = buildList {
        System.getProperty("compose.application.resources.dir")?.let { add(File(it, "vlc").absolutePath) }
        System.getenv("ProgramFiles")?.let { add(File(it, "VideoLAN\\VLC").absolutePath) }
        add("C:\\Program Files\\VideoLAN\\VLC")
        System.getenv("LOCALAPPDATA")?.let { add(File(it, "Programs\\VideoLAN\\VLC").absolutePath) }
    }.distinct()

    /** Short explanation when VLC cannot be loaded. */
    fun diagnosis(): String {
        val arch = System.getProperty("os.arch")
        val found = candidates().filter { File(it, "libvlc.dll").exists() }
        val x86 = File("C:\\Program Files (x86)\\VideoLAN\\VLC\\libvlc.dll").exists()
        return when {
            found.isNotEmpty() -> "VLC gevonden in ${found.first()}, maar laden lukte niet ($arch)."
            x86 -> "Alleen de 32-bit VLC is geïnstalleerd. Installeer de 64-bit versie van videolan.org."
            else -> "VLC is niet gevonden. Installeer VLC media player (64-bit) van videolan.org."
        }
    }
}
