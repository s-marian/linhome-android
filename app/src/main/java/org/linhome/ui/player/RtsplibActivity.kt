/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
 *
 * This file is part of linhome-android
 * (see https://www.linhome.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.linhome.ui.player

import android.os.Bundle
import android.os.SystemClock
import android.view.SurfaceView
import android.view.View
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.SeekBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.linhome.GenericActivity
import org.linhome.R
import org.linhome.databinding.ActivityRtsplibBinding
import org.linphone.core.tools.Log

/**
 * Activity for displaying an RTSP video stream using VLC/libVLC.
 * Supports H.264 codec streams with optional authentication.
 */
class RtsplibActivity : GenericActivity() {

    lateinit var binding: ActivityRtsplibBinding
    lateinit var playerViewModel: RtsplibViewModel
    var playingOnPause = false
    private var rtspVlcPlayer: RtspVlcPlayer? = null

    companion object {
        /**
         * Creates an intent to launch the RTSP player activity.
         *
         * @param context The context to create the intent from
         * @param streamUrl The RTSP stream URL (with or without authentication)
         * @return The intent to launch the activity
         */
        fun createIntent(context: android.content.Context, streamUrl: String): android.content.Intent {
            return android.content.Intent(context, RtsplibActivity::class.java).apply {
                putExtra("streamUrl", streamUrl)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide navigation and status bars for immersive mode
        val decorView: View = window.decorView
        val uiOptions: Int =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN
        decorView.systemUiVisibility = uiOptions

        binding = DataBindingUtil.setContentView(this, R.layout.activity_rtsplib) as ActivityRtsplibBinding
        binding.lifecycleOwner = this

        // Get stream URL from intent
        val streamUrl = intent.getStringExtra("streamUrl")
        if (streamUrl.isNullOrEmpty()) {
            Log.e("[RTSP] No stream URL provided")
            finish()
            return
        }

        Log.i("[RTSP] Loading stream: $streamUrl")

        // Create VLC player
        rtspVlcPlayer = RtspVlcPlayer(this)
        rtspVlcPlayer?.setPlayerListener(object : RtspVlcPlayer.PlayerListener {
            override fun onPlaying() {
                runOnUiThread {
                    playerViewModel.playing.value = true
                }
            }

            override fun onPaused() {
                runOnUiThread {
                    playerViewModel.playing.value = false
                }
            }

            override fun onStopped() {
                runOnUiThread {
                    playerViewModel.playing.value = false
                }
            }

            override fun onEndReached() {
                runOnUiThread {
                    playerViewModel.endReached.value = true
                    playerViewModel.playing.value = false
                }
            }

            override fun onBufferingUpdate(buffer: Float) {
                Log.i("[RTSP] Buffering: ${buffer * 100}%")
            }

            override fun onError(error: String) {
                runOnUiThread {
                    playerViewModel.error.value = error
                }
            }
        })

        // Create and setup ViewModel
        viewModelStore.clear()
        playerViewModel = ViewModelProvider(
            this,
            RtsplibViewModel.Factory(streamUrl, this)
        )[RtsplibViewModel::class.java]

        binding.model = playerViewModel

        // Setup VLC video display
        val surfaceView = findViewById<SurfaceView>(R.id.video)
        rtspVlcPlayer?.setupView(surfaceView)

        // Setup controls
        findViewById<ImageView>(R.id.play).setOnClickListener {
            togglePlay()
        }

        findViewById<ImageView>(R.id.cancel_button).setOnClickListener {
            findViewById<ImageView>(R.id.cancel_button).alpha = 0.3f
            finish()
        }

        // Observe player state
        playerViewModel.playing.observe(this, Observer { playing ->
            if (playing) {
                findViewById<Chronometer>(R.id.timer).start()
            } else {
                findViewById<Chronometer>(R.id.timer).stop()
            }
        })

        playerViewModel.playing.observe(this) { p ->
            findViewById<Chronometer>(R.id.timer).base = SystemClock.elapsedRealtime() - playerViewModel.position.value!!
        }

        playerViewModel.userTrackingPosition.observe(this) { p ->
            if (playerViewModel.userTracking.value == true) {
                findViewById<Chronometer>(R.id.timer).text =
                    String.format("%02d:%02d", (p / 1000) / 60, (p / 1000) % 60)
            }
        }

        findViewById<Chronometer>(R.id.timer).setOnChronometerTickListener {
            playerViewModel.updatePosition()
        }

        findViewById<SeekBar>(R.id.seek).setOnTouchListener { view, motionEvent -> false }

        // Start playing the stream
        playerViewModel.startPlaying()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.clear()
        outState.putInt("position", playerViewModel.position.value?.toInt() ?: 0)
        outState.putBoolean("playing", playingOnPause)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        playingOnPause = playerViewModel.playing.value!!
        playerViewModel?.pausePlay()
        super.onPause()
    }

    override fun onDestroy() {
        playerViewModel?.close()
        rtspVlcPlayer?.release()
        super.onDestroy()
    }

    fun togglePlay() {
        if (playerViewModel.playing.value == true) {
            findViewById<Chronometer>(R.id.timer).stop()
        } else {
            findViewById<Chronometer>(R.id.timer).base = SystemClock.elapsedRealtime() - playerViewModel.position.value!!
            findViewById<Chronometer>(R.id.timer).start()
        }
        playerViewModel.togglePlay()
    }
}
