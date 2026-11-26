package com.maluku.teyfey

import android.app.Application
import com.maluku.teyfey.data.Repository
import com.maluku.teyfey.data.VodRepository
import com.maluku.teyfey.player.PlaybackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    lateinit var repo: Repository
        private set
    lateinit var playback: PlaybackManager
        private set
    lateinit var vod: VodRepository
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = Repository(this)
        playback = PlaybackManager(this)
        vod = VodRepository(this, repo)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
