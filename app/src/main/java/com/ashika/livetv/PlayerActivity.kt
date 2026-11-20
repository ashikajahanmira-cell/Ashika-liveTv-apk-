package com.ashika.livetv

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var topControls: LinearLayout
    private var fullscreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoUrl = intent.getStringExtra("url")
        val channelName = intent.getStringExtra("name") ?: "Ashika Live TV"

        if (videoUrl.isNullOrBlank()) {
            finish()
            return
        }

        title = channelName

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            useController = true
        }

        root.addView(playerView)

        topControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            setBackgroundColor(0x99000000.toInt())
        }

        topControls.addView(makeButton("POPUP") {
            openPictureInPicture()
        })

        topControls.addView(makeButton("FULL SCREEN") {
            toggleFullscreen()
        })

        val controlsParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        )

        root.addView(topControls, controlsParams)
        setContentView(root)

        try {
            player = ExoPlayer.Builder(this).build().also {
                playerView.player = it
                it.setMediaItem(MediaItem.fromUri(videoUrl))
                it.prepare()
                it.playWhenReady = true
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Player error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun makeButton(text: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(18, 14, 18, 14)
            setBackgroundColor(0xFF26364F.toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(5, 0, 5, 0)
            }
        }
    }

    private fun openPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "Popup requires Android 8.0+", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()

            enterPictureInPictureMode(params)
        } catch (e: Exception) {
            Toast.makeText(this, "Popup unavailable: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleFullscreen() {
        fullscreen = !fullscreen
        applyFullscreen()
    }

    private fun applyFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (fullscreen && !isInPictureInPictureMode) {
                window.insetsController?.hide(WindowInsets.Type.systemBars())
                window.insetsController?.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                window.insetsController?.show(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                if (fullscreen && !isInPictureInPictureMode) {
                    (View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
                } else {
                    View.SYSTEM_UI_FLAG_VISIBLE
                }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreen()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        topControls.visibility =
            if (isInPictureInPictureMode) View.GONE else View.VISIBLE

        playerView.useController = !isInPictureInPictureMode

        if (!isInPictureInPictureMode) {
            applyFullscreen()
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (fullscreen) {
            fullscreen = false
            applyFullscreen()
        } else {
            super.onBackPressed()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) {
            player?.pause()
        }
    }

    override fun onDestroy() {
        if (::playerView.isInitialized) {
            playerView.player = null
        }

        player?.release()
        player = null
        super.onDestroy()
    }
}
