package com.maluku.teyfey

import com.maluku.teyfey.data.Repository
import com.maluku.teyfey.data.VodRepository
import com.maluku.teyfey.player.PlaybackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-wide singletons (same shape as the Android version). */
class App private constructor() {
    val repo = Repository()
    val playback = PlaybackManager()
    val vod = VodRepository(repo)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        val instance: App by lazy { App() }
    }
}
