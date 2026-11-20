package com.ashika.livetv

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView

class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null

    private lateinit var playerView: PlayerView
    private lateinit var topControls: LinearLayout
    private lateinit var controlView: PlayerControlView
    private lateinit var statusText: TextView

    private var fullscreen = false
    private var retryCount = 0
    private var retryPending = false

    private var previousOrientation =
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private val handler = Handler(Looper.getMainLooper())

    private var retryRunnable: Runnable? = null

    private val maxRetryDelay = 15000L

    private var videoUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        videoUrl = intent.getStringExtra("url") ?: ""

        val channelName =
            intent.getStringExtra("name") ?: "Ashika Live TV"

        if (videoUrl.isBlank()) {
            Toast.makeText(
                this,
                "Invalid video URL",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        title = channelName

        createPlayerUI()
        initializePlayer()
    }

    private fun createPlayerUI() {

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // PlayerView maintains the original video aspect ratio.
        playerView = PlayerView(this).apply {

            setBackgroundColor(Color.BLACK)

            useController = false

            resizeMode =
                AspectRatioFrameLayout.RESIZE_MODE_FIT

            setShowBuffering(
                PlayerView.SHOW_BUFFERING_WHEN_PLAYING
            )

            setKeepContentOnPlayerReset(true)
        }

        root.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Top controls
        topControls = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER

            setPadding(8, 8, 8, 8)

            setBackgroundColor(0xCC170B2E.toInt())
        }

        topControls.addView(
            makeButton("POPUP") {
                openPictureInPicture()
            }
        )

        topControls.addView(
            makeButton("FULL SCREEN") {
                toggleFullscreen()
            }
        )

        root.addView(
            topControls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
        )

        // Loading / connection status
        statusText = TextView(this).apply {

            text = "Connecting..."
            textSize = 14f

            setTextColor(Color.WHITE)

            gravity = Gravity.CENTER

            setPadding(16, 10, 16, 10)

            setBackgroundColor(0xAA170B2E.toInt())
        }

        root.addView(
            statusText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        // Playback controls
        controlView = PlayerControlView(this).apply {

            setShowTimeoutMs(4000)

            setShowPreviousButton(false)
            setShowNextButton(false)
        }

        root.addView(
            controlView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        setContentView(root)
    }

    private fun initializePlayer() {

        val mediaItem = MediaItem.fromUri(videoUrl)

        player = ExoPlayer.Builder(this)
            .build()
            .also { exoPlayer ->

                playerView.player = exoPlayer

                controlView.player = exoPlayer

                exoPlayer.setMediaItem(mediaItem)

                exoPlayer.addListener(
                    object : Player.Listener {

                        override fun onPlaybackStateChanged(
                            playbackState: Int
                        ) {

                            when (playbackState) {

                                Player.STATE_BUFFERING -> {

                                    statusText.visibility =
                                        View.VISIBLE

                                    statusText.text =
                                        "Loading video..."
                                }

                                Player.STATE_READY -> {

                                    if (exoPlayer.isPlaying) {

                                        statusText.visibility =
                                            View.GONE

                                    } else if (
                                        exoPlayer.playWhenReady
                                    ) {

                                        statusText.visibility =
                                            View.VISIBLE

                                        statusText.text =
                                            "Starting video..."
                                    }
                                }

                                Player.STATE_ENDED -> {
                                    startAutoReconnect()
                                }

                                Player.STATE_IDLE -> {

                                    if (!retryPending) {

                                        statusText.visibility =
                                            View.VISIBLE

                                        statusText.text =
                                            "Waiting..."
                                    }
                                }
                            }
                        }

                        override fun onIsPlayingChanged(
                            isPlaying: Boolean
                        ) {

                            if (isPlaying) {

                                window.addFlags(
                                    WindowManager.LayoutParams
                                        .FLAG_KEEP_SCREEN_ON
                                )

                                statusText.visibility =
                                    View.GONE

                                retryCount = 0

                                cancelReconnect()

                            } else {

                                window.clearFlags(
                                    WindowManager.LayoutParams
                                        .FLAG_KEEP_SCREEN_ON
                                )
                            }
                        }

                        override fun onPlayerError(
                            error: PlaybackException
                        ) {

                            startAutoReconnect()
                        }
                    }
                )

                exoPlayer.prepare()

                exoPlayer.playWhenReady = true
            }
    }

    // Automatic reconnection
    private fun startAutoReconnect() {

        if (isFinishing || isDestroyed) return

        if (retryPending) return

        retryCount++

        val exponent = minOf(retryCount - 1, 4)

        val delay = minOf(
            1000L * (1L shl exponent),
            maxRetryDelay
        )

        retryPending = true

        statusText.visibility = View.VISIBLE

        statusText.text =
            "Reconnecting... ($retryCount)"

        retryRunnable?.let {
            handler.removeCallbacks(it)
        }

        val runnable = Runnable {

            retryPending = false
            retryRunnable = null

            if (isFinishing || isDestroyed) {
                return@Runnable
            }

            val currentPlayer = player ?: return@Runnable

            try {

                currentPlayer.stop()

                currentPlayer.prepare()

                currentPlayer.playWhenReady = true

            } catch (e: Exception) {

                startAutoReconnect()
            }
        }

        retryRunnable = runnable

        handler.postDelayed(runnable, delay)
    }

    private fun cancelReconnect() {

        retryRunnable?.let {
            handler.removeCallbacks(it)
        }

        retryRunnable = null
        retryPending = false
    }

    // Purple theme buttons
    private fun makeButton(
        text: String,
        action: () -> Unit
    ): TextView {

        return TextView(this).apply {

            this.text = text

            textSize = 13f

            setTextColor(Color.WHITE)

            gravity = Gravity.CENTER

            setPadding(18, 15, 18, 15)

            setBackgroundColor(0xFF8A2BE2.toInt())

            isClickable = true
            isFocusable = true

            setOnClickListener {
                action()
            }

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {

                setMargins(6, 4, 6, 4)
            }
        }
    }

    // Fullscreen
    private fun toggleFullscreen() {

        fullscreen = !fullscreen

        if (fullscreen) {

            previousOrientation = requestedOrientation

            requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        } else {

            requestedOrientation = previousOrientation
        }

        applyFullscreen()
    }

    // Popup / Picture in Picture
    private fun openPictureInPicture() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {

            Toast.makeText(
                this,
                "Android 8.0 or newer required",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        try {

            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()

            enterPictureInPictureMode(params)

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Popup error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // System bars
    private fun applyFullscreen() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            if (fullscreen && !isInPictureInPictureMode) {

                window.insetsController?.hide(
                    WindowInsets.Type.systemBars()
                )

                window.insetsController?.systemBarsBehavior =
                    android.view.WindowInsetsController
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            } else {

                window.insetsController?.show(
                    WindowInsets.Type.systemBars()
                )
            }

        } else {

            @Suppress("DEPRECATION")

            window.decorView.systemUiVisibility =
                if (
                    fullscreen &&
                    !isInPictureInPictureMode
                ) {

                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

                } else {

                    View.SYSTEM_UI_FLAG_VISIBLE
                }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {

        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            applyFullscreen()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {

        super.onPictureInPictureModeChanged(
            isInPictureInPictureMode,
            newConfig
        )

        topControls.visibility =
            if (isInPictureInPictureMode) {
                View.GONE
            } else {
                View.VISIBLE
            }

        controlView.visibility =
            if (isInPictureInPictureMode) {
                View.GONE
            } else {
                View.VISIBLE
            }

        if (isInPictureInPictureMode) {
            statusText.visibility = View.GONE
        }

        if (!isInPictureInPictureMode) {
            applyFullscreen()
        }
    }

    @Suppress("DEPRECATION")
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

        if (!isInPictureInPictureMode) {

            player?.pause()

            cancelReconnect()
        }
    }

    override fun onDestroy() {

        cancelReconnect()

        handler.removeCallbacksAndMessages(null)

        if (::controlView.isInitialized) {
            controlView.player = null
        }

        if (::playerView.isInitialized) {
            playerView.player = null
        }

        window.clearFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        player?.release()
        player = null

        super.onDestroy()
    }
}
