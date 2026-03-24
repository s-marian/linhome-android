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
package org.linhome.ui.call

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.widget.FrameLayout
import org.linhome.LinhomeApplication
import org.linphone.core.Player

/**
 * Custom view that displays an RTSP video stream in the incoming call overlay.
 * This view manages the TextureView and RTSP player lifecycle.
 */
class RTSPOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val textureView = TextureView(context)
    private var player: Player? = null
    private var isStreamPlaying = false

    init {
        // Add TextureView to the view hierarchy
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Setup surface texture listener for RTSP stream playback
        textureView.surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: android.graphics.SurfaceTexture,
                width: Int,
                height: Int
            ) {
                onSurfaceAvailable(surface, width, height)
            }

            override fun onSurfaceTextureSizeChanged(
                surface: android.graphics.SurfaceTexture,
                width: Int,
                height: Int
            ) {
                // Surface size changed, adjust player if needed
            }

            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                onSurfaceDestroyed()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {
                // Video frame updated callback
            }
        }
    }

    /**
     * Starts the RTSP stream playback.
     * Retrieves the configured RTSP stream URL from CorePreferences and begins playback.
     */
    fun startRTSPStream() {
        if (isStreamPlaying) {
            return
        }

        val corePreferences = LinhomeApplication.corePreferences
        val rtspStream = corePreferences.getRtspStreamConfiguration()

        // Check if RTSP stream is configured
        if (rtspStream.url.isEmpty()) {
            android.util.Log.e("RTSPOverlayView", "No RTSP stream URL configured")
            return
        }

        // Build the authenticated URL
        val streamUrl = rtspStream.buildAuthenticatedUrl()
        android.util.Log.i("RTSPOverlayView", "Starting RTSP stream: $streamUrl")

        try {
            // Create player from Linphone core
            player = LinhomeApplication.coreContext.getPlayer()
            
            if (player != null) {
                // Set the video window ID to the texture surface
                player?.setWindowId(textureView.surfaceTexture)
                
                // Open and start playing the stream
                player?.open(streamUrl)
                player?.start()
                isStreamPlaying = true
                
                android.util.Log.i("RTSPOverlayView", "RTSP stream started successfully")
            } else {
                android.util.Log.e("RTSPOverlayView", "Failed to create RTSP player")
            }
        } catch (e: Exception) {
            android.util.Log.e("RTSPOverlayView", "Error starting RTSP stream: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Stops the RTSP stream playback and releases resources.
     */
    fun stopRTSPStream() {
        if (!isStreamPlaying) {
            return
        }

        try {
            player?.close()
            player?.setWindowId(null)
            player = null
            isStreamPlaying = false
            
            android.util.Log.i("RTSPOverlayView", "RTSP stream stopped")
        } catch (e: Exception) {
            android.util.Log.e("RTSPOverlayView", "Error stopping RTSP stream: ${e.message}")
        }
    }

    /**
     * Called when the surface texture becomes available.
     * Starts the RTSP stream playback.
     */
    private fun onSurfaceAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
        android.util.Log.i("RTSPOverlayView", "Surface available: $width x $height")
        startRTSPStream()
    }

    /**
     * Called when the surface texture is destroyed.
     * Stops the RTSP stream and releases resources.
     */
    private fun onSurfaceDestroyed() {
        android.util.Log.i("RTSPOverlayView", "Surface destroyed")
        stopRTSPStream()
    }

    /**
     * Called when the view is detached from the window.
     * Ensures proper cleanup of RTSP player resources.
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRTSPStream()
    }

    /**
     * Checks if the RTSP stream is currently playing.
     */
    fun isPlaying(): Boolean = isStreamPlaying

    /**
     * Toggles the RTSP stream playback state.
     */
    fun togglePlay() {
        if (isStreamPlaying) {
            stopRTSPStream()
        } else {
            startRTSPStream()
        }
    }
}
