package com.maluku.teyfey.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.maluku.teyfey.data.Channel
import com.maluku.teyfey.data.Net
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer

data class Playable(
    val key: String,
    val url: String,
    val title: String,
    val image: String?,
    val live: Boolean,
)

data class Track(val id: Int, val name: String)

/**
 * One VLC player for the whole app. Video frames are copied into a Compose
 * [ImageBitmap] ([frame]) so menus and info can be drawn on top of the video.
 */
class PlaybackManager {

    private val scope = CoroutineScope(Dispatchers.Default)

    /** False when VLC is not installed on this PC. */
    val vlcAvailable: Boolean = runCatching { NativeDiscovery().discover() }.getOrDefault(false)

    private val factory: MediaPlayerFactory? =
        if (vlcAvailable) runCatching {
            MediaPlayerFactory("--no-video-title-show", "--quiet", "--network-caching=1500")
        }.getOrNull() else null

    private val player: EmbeddedMediaPlayer? = factory?.mediaPlayers()?.newEmbeddedMediaPlayer()

    /** The latest video frame, drawn by VideoView. */
    var frame by mutableStateOf<ImageBitmap?>(null)
        private set

    private var info: ImageInfo? = null
    private var bytes: ByteArray = ByteArray(0)

    private val _current = MutableStateFlow<Channel?>(null)
    val current: StateFlow<Channel?> = _current

    private val _media = MutableStateFlow<Playable?>(null)
    val media: StateFlow<Playable?> = _media

    private val _ended = MutableStateFlow(0)
    val ended: StateFlow<Int> = _ended

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    /** No Chromecast on Windows: kept so the shared screens compile unchanged. */
    val casting: StateFlow<Boolean> = MutableStateFlow(false)
    val castDevice: StateFlow<String?> = MutableStateFlow(null)

    private var retries = 0
    private var startPosMs = 0L
    private var subsOff = false

    init {
        player?.videoSurface()?.set(
            CallbackVideoSurface(
                object : BufferFormatCallback {
                    override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                        info = ImageInfo.makeN32(sourceWidth, sourceHeight, ColorAlphaType.OPAQUE)
                        return RV32BufferFormat(sourceWidth, sourceHeight)
                    }

                    override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {
                        bytes = ByteArray(buffers[0].limit())
                    }
                },
                object : RenderCallback {
                    override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<out ByteBuffer>, bufferFormat: BufferFormat) {
                        val i = info ?: return
                        val buf = nativeBuffers[0]
                        if (bytes.size != buf.limit()) bytes = ByteArray(buf.limit())
                        buf.get(bytes)
                        buf.rewind()
                        val bmp = Bitmap()
                        bmp.allocPixels(i)
                        bmp.installPixels(bytes)
                        frame = bmp.asComposeImageBitmap()
                    }
                },
                true,
                VideoSurfaceAdapters.getVideoSurfaceAdapter(),
            )
        )

        player?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                _playing.value = true
                _buffering.value = false
                _error.value = null
                retries = 0
                if (subsOff) mediaPlayer.subpictures().setTrack(-1)
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _playing.value = false
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                _buffering.value = newCache < 100f
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                _playing.value = false
                if (_media.value?.live == true) reconnect(500) else _ended.value++
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _playing.value = false
                if (retries < 4) {
                    retries++
                    _error.value = "Verbinding herstellen… ($retries)"
                    reconnect(1500L * retries)
                } else {
                    _error.value = if (_media.value?.live == true) "Dit kanaal speelt nu niet af." else "Deze video speelt niet af."
                }
            }
        })
    }

    // ------------------------------------------------------------------ playback

    fun play(channel: Channel) {
        val p = Playable(channel.id, channel.url, channel.name, channel.logo, live = true)
        _current.value = channel
        if (_media.value?.url == p.url && player?.status()?.isPlaying == true) return
        start(p, 0)
    }

    fun playVod(p: Playable, startMs: Long = 0, audioLang: String = "orig", subLang: String? = "nl") {
        _current.value = null
        langOptions = buildList {
            if (audioLang != "orig") add(":audio-language=$audioLang,en")
            if (subLang != null) add(":sub-language=$subLang")
        }
        subsOff = subLang == null
        start(p, startMs)
    }

    private var langOptions: List<String> = emptyList()

    private fun start(p: Playable, startMs: Long) {
        _media.value = p
        _error.value = null
        retries = 0
        startPosMs = startMs
        load(p, startMs)
    }

    private fun load(p: Playable, startMs: Long) {
        val mp = player ?: run {
            _error.value = VlcLocations.diagnosis()
            return
        }
        frame = null
        _buffering.value = true
        val opts = ArrayList<String>()
        opts += ":http-user-agent=${Net.USER_AGENT}"
        opts += ":http-reconnect"
        opts += if (p.live) ":network-caching=3000" else ":network-caching=6000"  // bigger cushion = fewer spinners
        if (!p.live) {
            opts += langOptions
            if (startMs > 0) opts += ":start-time=${startMs / 1000}"
        }
        mp.media().play(p.url, *opts.toTypedArray())
    }

    private fun reconnect(delayMs: Long) {
        val p = _media.value ?: return
        val pos = if (p.live) 0L else (player?.status()?.time() ?: 0L)
        scope.launch {
            delay(delayMs)
            if (_media.value == p) load(p, pos)
        }
    }

    // ------------------------------------------------------------------ controls (films/series)

    fun togglePause() {
        player?.controls()?.pause()
    }

    fun position(): Long = player?.status()?.time() ?: 0L
    fun duration(): Long = player?.status()?.length() ?: 0L

    fun seekTo(ms: Long) {
        player?.controls()?.setTime(ms.coerceAtLeast(0))
    }

    fun skip(deltaMs: Long) = seekTo(position() + deltaMs)

    fun audioTracks(): List<Track> =
        player?.audio()?.trackDescriptions()?.filter { it.id() >= 0 }?.map { Track(it.id(), it.description()) }.orEmpty()

    fun audioTrack(): Int = player?.audio()?.track() ?: -1
    fun setAudioTrack(id: Int) {
        player?.audio()?.setTrack(id)
    }

    fun subtitleTracks(): List<Track> =
        player?.subpictures()?.trackDescriptions()?.map { Track(it.id(), if (it.id() < 0) "Uit" else it.description()) }.orEmpty()

    fun subtitleTrack(): Int = player?.subpictures()?.track() ?: -1
    fun setSubtitleTrack(id: Int) {
        player?.subpictures()?.setTrack(id)
    }

    fun volume(): Int = player?.audio()?.volume() ?: 100
    fun setVolume(v: Int) {
        player?.audio()?.setVolume(v.coerceIn(0, 150))
    }

    fun setMuted(muted: Boolean) {
        player?.audio()?.isMute = muted
    }

    fun stop() {
        player?.controls()?.stop()
        _current.value = null
        _media.value = null
        _playing.value = false
        frame = null
    }

    fun release() {
        runCatching { player?.controls()?.stop() }
        runCatching { player?.release() }
        runCatching { factory?.release() }
    }
}
