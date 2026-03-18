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
package org.linhome.utils

import android.app.Activity
import android.view.WindowManager
import org.linhome.LinhomeApplication

/**
 * Manages the keep-screen-on behavior for the application.
 * Should be called from all activities' lifecycle methods.
 */
object ScreenOnManager {
    
    /**
     * Called when an activity is created/resumed.
     * Sets FLAG_KEEP_SCREEN_ON if the setting is enabled.
     */
    fun onActivityResume(activity: Activity) {
        if (LinhomeApplication.corePreferences.keepScreenOn) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    /**
     * Called when an activity is paused/stopped.
     * Clears FLAG_KEEP_SCREEN_ON to allow screen to turn off.
     */
    fun onActivityPause(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
