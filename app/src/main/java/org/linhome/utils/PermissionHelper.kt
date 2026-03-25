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

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import org.linhome.compatibility.Api23Compatibility

/**
 * Utility class for handling permission requests and settings launches.
 * Provides methods to check and launch settings for overlay permissions and battery optimization.
 */
object PermissionHelper {

    /**
     * Checks if the app has the SYSTEM_ALERT_WINDOW permission (draw over other apps).
     * This is required for the call overlay to appear on lock screen.
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Launches the SYSTEM_ALERT_WINDOW permission settings screen.
     * Users must manually grant this permission in system settings.
     */
    fun launchOverlayPermissionSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Checks if the app is excluded from battery optimization.
     * This is important for Samsung devices to prevent the app from being killed in background.
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    /**
     * Launches the battery optimization settings screen.
     * Users can choose to disable battery optimization for the app.
     */
    fun launchBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Launches the app info settings screen where users can manage all app permissions.
     */
    fun launchAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Checks if the app has the USE_FULL_SCREEN_INTENT permission.
     * This is declared in AndroidManifest and doesn't require runtime permission.
     */
    fun hasFullScreenIntentPermission(context: Context): Boolean {
        return try {
            context.packageManager.checkPermission(
                android.Manifest.permission.USE_FULL_SCREEN_INTENT,
                context.packageName
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if all required permissions for call overlay are granted.
     * Returns a map of permission name to granted status.
     */
    fun checkAllOverlayPermissions(context: Context): Map<String, Boolean> {
        return mapOf(
            "overlay_permission" to hasOverlayPermission(context),
            "battery_optimization" to isBatteryOptimizationDisabled(context),
            "full_screen_intent" to hasFullScreenIntentPermission(context)
        )
    }

    /**
     * Creates an intent that launches all relevant settings screens.
     * This is a fallback for devices where individual settings cannot be launched.
     */
    fun launchAllSettings(context: Context): Intent {
        return Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
