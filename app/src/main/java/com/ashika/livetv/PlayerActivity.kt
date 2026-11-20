package com.ashika.livetv

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoUrl = intent.getStringExtra("url")

        if (videoUrl.isNullOrBlank()) {
            finish()
            return
        }

        val channelName =
            intent.getStringExtra("name") ?: "Ashika Live TV"

        title = channelName

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        status = TextView(this).apply {
            text = "Connecting to channel..."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(20, 20, 20, 20)
        }

        root.addView(playerView)
        root.addView(
            status,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        setContentView(root)

        try {
            player = ExoPlayer.Builder(this).build()

            playerView.player = player

            player?.addListener(object : Player.Listener {

                override fun onPlaybackStateChanged(
                    playbackState: Int
                ) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            status.text = "Loading video..."
                            status.visibility = android.view.View.VISIBLE
                        }

                        Player.STATE_READY -> {
                            status.visibility = android.view.View.GONE
                        }

                        Player.STATE_ENDED -> {
                            status.text = "Stream ended"
                            status.visibility = android.view.View.VISIBLE
                        }
                    }
                }

                override fun onPlayerError(
                    error: PlaybackException
                ) {
                    status.text =
                        "Video could not be played.\n" +
                        error.errorCodeName

                    status.visibility = android.view.View.VISIBLE
                }
            })

            player?.setMediaItem(MediaItem.fromUri(videoUrl))
            player?.prepare()
            player?.playWhenReady = true

        } catch (e: Exception) {
            status.text = "Player error: ${e.message}"
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
