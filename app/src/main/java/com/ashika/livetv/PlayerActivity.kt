package com.ashika.livetv

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView

class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var textureView: TextureView
    private lateinit var topControls: LinearLayout
    private lateinit var controlView: PlayerControlView

    private var fullscreen = false
    private var previousOrientation =
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

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

        textureView = TextureView(this)

        root.addView(textureView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        topControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            setBackgroundColor(0xCC101B30.toInt())
        }

        topControls.addView(makeButton("POPUP") {
            openPictureInPicture()
        })

        topControls.addView(makeButton("FULL SCREEN") {
            toggleFullscreen()
        })

        root.addView(topControls, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ))

        controlView = PlayerControlView(this).apply {
            setShowTimeoutMs(4000)
        }

        root.addView(controlView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))

        setContentView(root)

        player = ExoPlayer.Builder(this).build().also {
            it.setVideoTextureView(textureView)
            it.setMediaItem(MediaItem.fromUri(videoUrl))
            controlView.player = it
            it.prepare()
            it.playWhenReady = true
        }
    }

    private fun makeButton(text: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(18, 15, 18, 15)
            setBackgroundColor(0xFF1769AA.toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(6, 4, 6, 4)
            }
        }
    }

    private fun toggleFullscreen() {
        fullscreen = !fullscreen

        if (fullscreen) {
            previousOrientation = requestedOrientation
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            requestedOrientation = previousOrientation
        }

        applyFullscreen()
    }

    private fun openPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "Android 8.0 or newer required", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()

            enterPictureInPictureMode(params)
        } catch (e: Exception) {
            Toast.makeText(this, "Popup error: ${e.message}", Toast.LENGTH_LONG).show()
        }
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

        controlView.visibility =
            if (isInPictureInPictureMode) View.GONE else View.VISIBLE

        if (!isInPictureInPictureMode) applyFullscreen()
    }

    override fun onBackPressed() {
        if (fullscreen) {
            fullscreen = false
            requestedOrientation = previousOrientation
            applyFullscreen()
        } else {
            super.onBackPressed()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) player?.pause()
    }

    override fun onDestroy() {
        if (::controlView.isInitialized) {
            controlView.player = null
        }
        player?.release()
        player = null
        super.onDestroy()
    }
}
