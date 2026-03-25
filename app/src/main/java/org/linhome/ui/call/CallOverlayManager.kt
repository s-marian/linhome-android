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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.TextView
import org.linhome.LinhomeApplication
import org.linhome.LinhomeApplication.Companion.corePreferences
import org.linhome.MainActivity
import org.linhome.R
import org.linhome.compatibility.Api26Compatibility
import org.linhome.linphonecore.extensions.extendedAccept
import org.linhome.store.DeviceStore
import org.linhome.ui.player.RtspVlcPlayer
import org.linhome.utils.PermissionHelper
import org.linphone.core.Call
import org.linphone.core.Reason

/**
 * Manages the full-screen incoming call overlay using SYSTEM_ALERT_WINDOW permission.
 * This overlay appears on top of all other applications to display incoming call UI.
 */
class CallOverlayManager(private val context: Context) {

    private var callOverlay: View? = null
    private var windowManager: WindowManager? = null
    private var currentCall: Call? = null
    private var rtspVlcPlayer: RtspVlcPlayer? = null

    /**
      * Shows the incoming call overlay on top of all other applications.
      * This requires SYSTEM_ALERT_WINDOW permission on Android 6.0+.
      * Also checks for battery optimization settings on Samsung devices.
      */
    fun showIncomingCall(call: Call) {
        if (!corePreferences.showIncomingCallOverlay) {
            return
        }

        // Check overlay permission
        if (!PermissionHelper.hasOverlayPermission(context)) {
            // Permission not granted - launch settings for user to grant
            PermissionHelper.launchOverlayPermissionSettings(context)
            return
        }

        // Check battery optimization (important for Samsung devices)
        if (!PermissionHelper.isBatteryOptimizationDisabled(context)) {
            // Battery optimization may cause the overlay to not appear on lock screen
            // Launch settings to allow user to disable optimization
            PermissionHelper.launchBatteryOptimizationSettings(context)
        }

        // Only show overlay for incoming calls
        if (call.state != Call.State.IncomingReceived && call.state != Call.State.IncomingEarlyMedia) {
            return
        }

        currentCall = call

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = createOverlayParams()

        // Use the new XML layout for the overlay
        val overlay = LayoutInflater.from(context).inflate(R.layout.activity_call_overlay, null)
        
        // Set caller name
        val callerName = overlay.findViewById<TextView>(R.id.callerName)
        callerName.text = call.remoteAddress.asString()
        
        // Find the device associated with this call
        val device = DeviceStore.findDeviceByAddress(call.remoteAddress.asString())
        
        // Check if RTSP stream overlay is enabled
        val useRTSPOverlay = corePreferences.showIncomingCallOverlayWithRTSP
        val rtspVideoView = overlay.findViewById<TextureView>(R.id.rtspVideoView)
        val deviceIcon = overlay.findViewById<TextView>(R.id.deviceIcon)
        
        if (useRTSPOverlay) {
            // Show RTSP video view and hide device icon
            rtspVideoView.visibility = View.VISIBLE
            deviceIcon.visibility = View.GONE
        } else {
            // Hide RTSP video view, show device icon (default behavior)
            rtspVideoView.visibility = View.GONE
            deviceIcon.visibility = View.VISIBLE
        }
        
        // Set up decline button
        val declineButton = overlay.findViewById<TextView>(R.id.declineButton)
        declineButton.setOnClickListener {
            try {
                call.decline(Reason.Declined)
                hideIncomingCall()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Set up answer button
        val answerButton = overlay.findViewById<TextView>(R.id.answerButton)
        answerButton.setOnClickListener {
            try {
                call.extendedAccept()
                hideIncomingCall()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            windowManager?.addView(overlay, params)
            callOverlay = overlay
            
            // Setup RTSP stream playback AFTER the overlay is added to the window
            // This ensures the TextureView surface is available
            if (useRTSPOverlay) {
                setupRTSPStream(rtspVideoView, device)
                
                // Force layout to ensure TextureView is measured and laid out
                rtspVideoView.post {
                    // Small delay to ensure surface is available
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (rtspVideoView.isAvailable) {
                            android.util.Log.i("CallOverlayManager", "TextureView is available after post")
                        } else {
                            android.util.Log.w("CallOverlayManager", "TextureView is NOT available after post")
                        }
                    }, 100)
                }
            }
        } catch (e: Exception) {
            // If overlay creation fails, fall back to notification
            e.printStackTrace()
        }
    }

    /**
      * Sets up the RTSP stream playback for the given TextureView using VLC.
      * Uses the device's RTSP stream if available.
      */
     private fun setupRTSPStream(textureView: TextureView, device: org.linhome.entities.Device?) {
         // Get RTSP stream from device
         val rtspStream = device?.let { dev ->
             if (dev.rtspStreamUrl.isNotEmpty()) {
                 org.linhome.entities.RTSPStream(
                     url = dev.rtspStreamUrl,
                     username = dev.rtspStreamUsername,
                     password = dev.rtspStreamPassword
                 )
             } else {
                 null
             }
         }

         if (rtspStream == null || rtspStream.url.isEmpty()) {
             android.util.Log.e("CallOverlayManager", "No RTSP stream URL configured for device")
             // Fall back to showing device icon
             callOverlay?.findViewById<TextureView>(R.id.rtspVideoView)?.visibility = View.GONE
             callOverlay?.findViewById<TextView>(R.id.deviceIcon)?.visibility = View.VISIBLE
             return
         }

        val streamUrl = rtspStream.buildAuthenticatedUrl()
        android.util.Log.i("CallOverlayManager", "Starting RTSP stream: $streamUrl (from ${if (device != null) "device" else "global config"})")

        try {
            // Create VLC player
            rtspVlcPlayer = RtspVlcPlayer(context)
            
            // Setup the video view with TextureView
            rtspVlcPlayer?.setupView(textureView)
            
            // Load and play the stream
            rtspVlcPlayer?.loadStream(streamUrl)
            
            android.util.Log.i("CallOverlayManager", "RTSP stream started successfully with VLC")
        } catch (e: Exception) {
            android.util.Log.e("CallOverlayManager", "Error starting RTSP stream: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Stops the RTSP stream playback and releases resources.
     */
    private fun stopRTSPStream() {
        try {
            rtspVlcPlayer?.release()
            rtspVlcPlayer = null
            android.util.Log.i("CallOverlayManager", "RTSP stream stopped")
        } catch (e: Exception) {
            android.util.Log.e("CallOverlayManager", "Error stopping RTSP stream: ${e.message}")
        }
    }

    /**
     * Hides the incoming call overlay.
     */
    fun hideIncomingCall() {
        // Stop RTSP stream if playing
        stopRTSPStream()
        
        // Remove overlay from window
        callOverlay?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                android.util.Log.e("CallOverlayManager", "Error removing overlay: ${e.message}")
            }
            callOverlay = null
        }
        
        currentCall = null
    }

    /**
      * Creates the overlay window parameters.
      * Uses TYPE_APPLICATION_OVERLAY for Android 8.0+ for better Samsung device compatibility.
      */
    private fun createOverlayParams(): LayoutParams {
        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // TYPE_APPLICATION_OVERLAY is the recommended type for Android 8.0+
                // It works better on Samsung devices for lock screen overlays
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                Api26Compatibility.getOverlayType()
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        return params
    }

    /**
      * Handles touch events on the overlay.
      */
    private fun handleOverlayTouch(call: Call) {
        // Handle touch events if needed
    }

    /**
      * Creates an intent to open the overlay settings page.
      * This opens the special "Draw over other apps" settings screen where users
      * can grant the SYSTEM_ALERT_WINDOW permission.
      */
    fun getOverlaySettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = android.net.Uri.parse("package:" + context.packageName)
            }
        } else {
            Intent(context, MainActivity::class.java)
        }
    }
}
