package com.maluku.teyfey.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.maluku.teyfey.App

// AppCompatActivity (a FragmentActivity) is required by the Cast button dialog.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            TeyfeyTheme { AppRoot() }
        }
    }

    override fun onStart() {
        super.onStart()
        App.instance.playback.resumeLocal()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) App.instance.playback.pauseLocal()
    }
}
