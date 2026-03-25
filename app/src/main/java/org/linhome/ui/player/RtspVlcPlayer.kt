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

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * VLC-based RTSP player wrapper class.
 * Provides a simplified API for RTSP stream playback using VLC/libVLC.
 */
class RtspVlcPlayer(private val context: Context) {
    companion object {
        private const val TAG = "RtspVlcPlayer"
    }

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isInitialized = false

    /**
     * Listener interface for player events.
     */
    interface PlayerListener {
        fun onPlaying()
        fun onPaused()
        fun onStopped()
        fun onEndReached()
        fun onBufferingUpdate(buffer: Float)
        fun onError(error: String)
    }

    private var listener: PlayerListener? = null

    init {
        // Initialize VLC
        try {
            libVLC = LibVLC(context)
            mediaPlayer = MediaPlayer(libVLC)
            
            isInitialized = true
            Log.i(TAG, "VLC initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize VLC: ${e.message}")
            listener?.onError("Failed to initialize player: ${e.message}")
        }
    }

    /**
     * Set the VLC view for video rendering using TextureView.
     * Configures the video to fill the entire view with stretch aspect ratio.
     */
    fun setupView(textureView: TextureView) {
        mediaPlayer?.vlcVout?.let { vout ->
            // 1. Detach any existing views
            vout.detachViews()
            
            // 2. Set the TextureView
            vout.setVideoView(textureView)
            
            // 3. Force window size to the current UI dimensions
            // Use post {} to ensure layout is calculated by Android
            textureView.post {
                val width = textureView.width
                val height = textureView.height
                
                if (width > 0 && height > 0) {
                    vout.setWindowSize(width, height)
                    
                    // 4. Attach views after setting WindowSize
                    vout.attachViews()

                    // 5. Apply Best Fit scale
                    mediaPlayer?.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
                    
                    // Optional: Force aspect ratio to match the view
                    // to avoid further VLC recalculations
                    mediaPlayer?.aspectRatio = null 
                }
            }
            
            Log.i(TAG, "VLC view setup complete with TextureView")
        }
    }

    /**
     * Set the player listener for callbacks.
     */
    fun setPlayerListener(listener: PlayerListener?) {
        this.listener = listener
    }

    /**
     * Load and play an RTSP stream.
     */
    fun loadStream(url: String) {
        if (!isInitialized) {
            Log.e(TAG, "VLC not initialized")
            listener?.onError("Player not initialized")
            return
        }

        try {
            // Parse the URL as a URI to ensure it's treated as a network resource
            val uri = android.net.Uri.parse(url)
            val media = Media(libVLC, uri)
            // Reduce caching for near-real-time playback
            media.addOption(":network-caching=150")
            // Avoid jitter buffer overhead for live streams
            media.addOption(":clock-jitter=0")
            mediaPlayer?.media = media
            // Release the local reference now that the player has it
            media.release()
            mediaPlayer?.play()
            Log.i(TAG, "Loading stream: $url")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load stream: ${e.message}")
            listener?.onError("Failed to load stream: ${e.message}")
        }
    }

    /**
     * Start playback.
     */
    fun play() {
        if (isInitialized) {
            mediaPlayer?.play()
            Log.i(TAG, "Play")
        }
    }

    /**
     * Pause playback.
     */
    fun pause() {
        if (isInitialized) {
            mediaPlayer?.pause()
            Log.i(TAG, "Pause")
        }
    }

    /**
     * Toggle play/pause state.
     */
    fun togglePlay() {
        if (isPlaying()) {
            pause()
        } else {
            play()
        }
    }

    /**
     * Seek to a specific position in milliseconds.
     */
    fun seekTo(positionMs: Long) {
        if (isInitialized) {
            val duration = getDuration()
            if (duration > 0) {
                val targetPosition = positionMs.coerceIn(0, duration)
                mediaPlayer?.time = targetPosition // setter takes Long
                Log.i(TAG, "Seek to $targetPosition ms")
            }
        }
    }

    /**
     * Get current playback position in milliseconds.
     */
    fun getCurrentPosition(): Long {
        return if (isInitialized) mediaPlayer?.time ?: 0L else 0L
    }

    /**
     * Get total stream duration in milliseconds.
     */
    fun getDuration(): Long {
        return if (isInitialized) mediaPlayer?.length ?: 0L else 0L
    }

    /**
     * Check if player is currently playing.
     */
    fun isPlaying(): Boolean {
        return if (isInitialized) mediaPlayer?.isPlaying ?: false else false
    }

    /**
     * Check if player is currently paused.
     * Uses player state to determine pause status.
     */
    fun isPaused(): Boolean {
        return if (isInitialized) {
            // In many versions, playerState returns an Int
            // 3 = Playing, 4 = Paused, 6 = Ended, 7 = Error
            mediaPlayer?.playerState == 4 
        } else false
    }

    /**
     * Check if player is ready to play.
     */
    fun isReady(): Boolean {
        return isInitialized && mediaPlayer?.media != null
    }

    /**
     * Release all resources.
     */
    fun release() {
        Log.i(TAG, "Releasing VLC player")
        mediaPlayer?.release()
        libVLC?.release()
        mediaPlayer = null
        libVLC = null
        listener = null
        isInitialized = false
    }
}
