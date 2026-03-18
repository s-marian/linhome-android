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
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.LinearLayout
import android.widget.TextView
import org.linhome.LinhomeApplication.Companion.corePreferences
import org.linhome.R
import org.linhome.compatibility.Compatibility
import org.linhome.linphonecore.extensions.extendedAccept
import org.linphone.core.Call
import org.linphone.core.Reason
import org.linphone.mediastream.Version

/**
 * Manages the full-screen incoming call overlay using SYSTEM_ALERT_WINDOW permission.
 * This overlay appears on top of all other applications to display incoming call UI.
 */
class CallOverlayManager(private val context: Context) {

    private var callOverlay: View? = null
    private var windowManager: WindowManager? = null
    private var currentCall: Call? = null

    /**
     * Shows the incoming call overlay on top of all other applications.
     * This requires SYSTEM_ALERT_WINDOW permission on Android 6.0+.
     */
    fun showIncomingCall(call: Call) {
        if (!corePreferences.showIncomingCallOverlay) {
            return
        }

        if (!hasPermission()) {
            return
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
        } catch (e: Exception) {
            // If overlay creation fails, fall back to notification
            e.printStackTrace()
        }
    }

    /**
     * Hides the incoming call overlay.
     */
    fun hideIncomingCall() {
        if (callOverlay != null && windowManager != null) {
            try {
                windowManager?.removeView(callOverlay)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            callOverlay = null
        }
        currentCall = null
    }

    /**
     * Checks if the app has SYSTEM_ALERT_WINDOW permission.
     */
    fun hasPermission(): Boolean {
        return Compatibility.canDrawOverlay(context)
    }

    /**
     * Creates the WindowManager.LayoutParams for the overlay.
     * Uses appropriate window type and flags for full-screen overlay.
     */
    private fun createOverlayParams(): LayoutParams {
        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
            if (Version.sdkAboveOrEqual(Version.API26_O_80)) {
                LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                LayoutParams.TYPE_PHONE
            },
            LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or LayoutParams.FLAG_TURN_SCREEN_ON
                    or LayoutParams.FLAG_DISMISS_KEYGUARD
                    or LayoutParams.FLAG_KEEP_SCREEN_ON
                    or LayoutParams.FLAG_NOT_FOCUSABLE
                    or LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

        return params
    }

    /**
     * Handles touch events on the overlay.
     * Currently just answers the call on touch.
     */
    private fun handleOverlayTouch(call: Call) {
        // Answer the call when user touches the overlay
        try {
            call.extendedAccept()
            hideIncomingCall()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Generates an intent to open the SYSTEM_ALERT_WINDOW settings.
     */
    fun getOverlaySettingsIntent(): Intent {
        return Intent(
            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Checks if the overlay is currently visible.
     */
    fun isOverlayVisible(): Boolean {
        return callOverlay != null
    }
}
