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
package org.linhome

import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import org.linhome.linphonecore.CorePreferences
import org.linhome.utils.ScreenOnManager

/**
 * Base activity class that provides keep-screen-on and immersive mode functionality.
 * All activities should extend this class to enable the keep-screen-on and immersive mode features.
 */
open class BaseActivity : GenericActivity(false) {
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }
    
    private fun applyImmersiveMode() {
        val corePreferences = CorePreferences(applicationContext)
        if (corePreferences.immersiveMode) {
            // Apply immersive mode flags - hide system bars without extending content under them
            val decorView = window.decorView
            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LOW_PROFILE
            )
            // Clear fullscreen flag to allow immersive mode to work
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            // Hide the tab bar when immersive mode is enabled
            val tabbar = findViewById<View>(R.id.tabbar)
            tabbar?.visibility = View.GONE
        } else {
            // Reset to normal visibility when immersive mode is disabled
            val decorView = window.decorView
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            // Show the tab bar when immersive mode is disabled
            val tabbar = findViewById<View>(R.id.tabbar)
            tabbar?.visibility = View.VISIBLE
        }
    }
}
