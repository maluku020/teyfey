package com.maluku.teyfey.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.google.android.gms.cast.framework.CastContext
import com.maluku.teyfey.data.Channel
import com.maluku.teyfey.data.Net
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(UnstableApi::class)
class PlaybackManager(private val ctx: Context) {

    private val main = Handler(Looper.getMainLooper())

    val exo: ExoPlayer = buildExo()

    private val castContext: CastContext? = try {
        @Suppress("DEPRECATION")
        CastContext.getSharedInstance(ctx)
    } catch (e: Exception) {
        null // No Google Play services (some TV boxes) -> no casting
    }
    val castPlayer: CastPlayer? = castContext?.let { CastPlayer(it) }
    val castSupported get() = castPlayer != null

    private val _current = MutableStateFlow<Channel?>(null)
    val current: StateFlow<Channel?> = _current

    private val _media = MutableStateFlow<Playable?>(null)
    val media: StateFlow<Playable?> = _media

    /** Increments when a film/episode plays to the end. */
    private val _ended = MutableStateFlow(0)
    val ended: StateFlow<Int> = _ended

    private val _casting = MutableStateFlow(false)
    val casting: StateFlow<Boolean> = _casting

    private val _castDevice = MutableStateFlow<String?>(null)
    val castDevice: StateFlow<String?> = _castDevice

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering

    private var retries = 0

    init {
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                _buffering.value = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    retries = 0
                    _error.value = null
                }
                if (state == Player.STATE_ENDED) {
                    if (_media.value?.live == true) {
                        // A live stream should never "end": reconnect.
                        main.postDelayed({ reconnect() }, 500)
                    } else {
                        _ended.value++
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exo.seekToDefaultPosition()
                    exo.prepare()
                    return
                }
                if (retries < 4) {
                    retries++
                    _error.value = "Verbinding herstellen… ($retries)"
                    main.postDelayed({ reconnect() }, 1500L * retries)
                } else {
                    _error.value = "Dit kanaal speelt nu niet af (${error.errorCodeName})."
                }
            }
        })

        castPlayer?.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                _casting.value = true
                _castDevice.value = castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName
                val pos = exo.currentPosition
                exo.stop()
                _media.value?.let { load(castPlayer, it, if (it.live) 0 else pos) }
            }

            override fun onCastSessionUnavailable() {
                _casting.value = false
                _castDevice.value = null
                val pos = runCatching { castPlayer.currentPosition }.getOrDefault(0L)
                _media.value?.let { load(exo, it, if (it.live) 0 else pos) }
            }
        })
        if (castPlayer?.isCastSessionAvailable == true) {
            _casting.value = true
            _castDevice.value = castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName
        }
    }

    private fun buildExo(): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Net.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
            .setKeepPostFor302Redirects(true)

        // IPTV-friendly TS settings: start on the first keyframe that arrives -> faster zapping.
        val extractors = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )

        // Start fast (1s), but keep a healthy buffer while watching to avoid stutter.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Prefer the FFmpeg audio decoder: it turns Dolby (AC3/E-AC3) and DTS into normal PCM sound,
        // instead of passing it to a TV that may stay silent.
        val renderers = DefaultRenderersFactory(ctx)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        return ExoPlayer.Builder(ctx, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http, extractors))
            .setLoadControl(loadControl)
            .build()
            .apply {
                setWakeMode(C.WAKE_MODE_NETWORK)
                playWhenReady = true
            }
    }

    /**
     * Preferred languages for films/series. [subs] = null means subtitles off.
     * Audio "orig" keeps the film's own default track.
     */
    fun applyLanguages(audio: String, subs: String?) {
        val b = exo.trackSelectionParameters.buildUpon()
        if (audio == "orig") b.setPreferredAudioLanguages() else b.setPreferredAudioLanguages(audio, "en")
        if (subs == null) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguages(subs)
                .setSelectUndeterminedTextLanguage(true)
        }
        exo.trackSelectionParameters = b.build()
    }

    /** The player that should currently be shown / controlled. */
    val activePlayer: Player get() = if (_casting.value && castPlayer != null) castPlayer else exo

    fun play(channel: Channel) {
        val p = Playable(channel.id, channel.url, channel.name, channel.logo, live = true)
        if (_media.value?.url == p.url && activePlayer.playbackState != Player.STATE_IDLE) {
            _current.value = channel
            activePlayer.play()
            return
        }
        _current.value = channel
        start(p, 0)
    }

    /** Films / episodes: starts at [startMs]. */
    fun playVod(p: Playable, startMs: Long = 0) {
        _current.value = null
        start(p, startMs)
    }

    private fun start(p: Playable, startMs: Long) {
        _media.value = p
        _error.value = null
        retries = 0
        load(activePlayer, p, startMs)
    }

    private fun reconnect() {
        val p = _media.value ?: return
        if (_casting.value) return
        load(exo, p, if (p.live) 0 else exo.currentPosition)
    }

    private fun load(player: Player, p: Playable, startMs: Long) {
        val isCast = player === castPlayer
        val url = p.url
        val ext = url.substringAfterLast('.', "").substringBefore('?').lowercase()
        val mime = when {
            url.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            !isCast -> null // let ExoPlayer detect it
            p.live -> MimeTypes.VIDEO_MP2T
            ext == "mkv" -> MimeTypes.VIDEO_MATROSKA
            ext == "ts" -> MimeTypes.VIDEO_MP2T
            ext == "webm" -> MimeTypes.VIDEO_WEBM
            else -> MimeTypes.VIDEO_MP4
        }
        val meta = MediaMetadata.Builder()
            .setTitle(p.title)
            .setArtworkUri(p.image?.let { runCatching { Uri.parse(it) }.getOrNull() })
            .build()
        val builder = MediaItem.Builder()
            .setUri(url)
            .setMimeType(mime)
            .setMediaMetadata(meta)
        if (p.live) {
            builder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(4_000).build())
        }
        val item = builder.build()
        if (p.live || startMs <= 0) player.setMediaItem(item) else player.setMediaItem(item, startMs)
        player.prepare()
        player.playWhenReady = true
    }

    fun setMuted(muted: Boolean) {
        exo.volume = if (muted) 0f else 1f
    }

    fun pauseLocal() {
        if (!_casting.value) exo.pause()
    }

    fun resumeLocal() {
        val p = _media.value ?: return
        if (_casting.value) return
        if (!p.live) return // films stay paused when you come back
        if (exo.playbackState == Player.STATE_IDLE) load(exo, p, 0) else exo.play()
    }

    fun stop() {
        exo.stop()
        _current.value = null
        _media.value = null
    }
}

data class Playable(
    val key: String,
    val url: String,
    val title: String,
    val image: String?,
    val live: Boolean,
)
