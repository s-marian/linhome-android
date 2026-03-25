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

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.lifecycle.MutableLiveData
import org.linhome.compatibility.Compatibility
import org.linhome.customisation.Customisation
import org.linhome.customisation.Texts
import org.linhome.linphonecore.CoreContext
import org.linhome.linphonecore.CorePreferences
import org.linhome.linphonecore.CoreService
import org.linhome.store.DeviceStore
import org.linphone.core.Factory
import org.linphone.core.LogCollectionState
import org.linphone.mediastream.Log

class LinhomeApplication : Application() {

    companion object {
        lateinit var instance: LinhomeApplication
            private set
        lateinit var corePreferences: CorePreferences
        lateinit var coreContext: CoreContext

        var someActivityRunning: Boolean = false
        private const val CHILD_PROTECTION_PREFS = "linhome_child_protection"
        private const val CHILD_PROTECTION_MODE_KEY = "child_protection_mode"
        private lateinit var childProtectionPreferences: SharedPreferences
        val childProtectionModeState = MutableLiveData<Boolean>().apply { value = false }
        val childProtectionModeReady = MutableLiveData<Boolean>().apply { value = false }

        fun setChildProtectionMode(enabled: Boolean) {
            if (::childProtectionPreferences.isInitialized) {
                childProtectionPreferences.edit().putBoolean(CHILD_PROTECTION_MODE_KEY, enabled).apply()
            }
            corePreferences.childProtectionMode = enabled
            childProtectionModeState.value = enabled
        }

        fun refreshChildProtectionModeState() {
            val persistedValue = if (::childProtectionPreferences.isInitialized) {
                childProtectionPreferences.getBoolean(CHILD_PROTECTION_MODE_KEY, false)
            } else {
                null
            }
            val configValue = corePreferences.childProtectionMode
            val actualValue = persistedValue ?: configValue
            if (persistedValue != null && persistedValue != configValue) {
                corePreferences.childProtectionMode = persistedValue
            }
            childProtectionModeState.value = actualValue
        }

        fun isChildProtectionModeEnabled(): Boolean {
            return childProtectionModeState.value == true || (::corePreferences.isInitialized && corePreferences.childProtectionMode)
        }

        fun ensureCoreExists(
            context: Context,
            service: CoreService? = null,
            useAutoStartDescription: Boolean = false,
            force: Boolean = false,
            startService: Boolean = true) : Boolean {

            ensureChildProtectionPreferences(context)
            if (!force && ::coreContext.isInitialized && !coreContext.stopped) {
                return false
            }

            Factory.instance().setLogCollectionPath(context.filesDir.absolutePath)
            Factory.instance().enableLogCollection(LogCollectionState.Enabled)

            corePreferences = CorePreferences(context)
            corePreferences.copyAssetsFromPackage() // TODO Move in the zip - attention not to overwrite .linphone_rc

            val config = Factory.instance().createConfigWithFactory(
                corePreferences.configPath,
                corePreferences.factoryConfigPath
            )
            config.setString("storage","call_logs_db_uri",context.filesDir.absolutePath + "/linphone-log-history.db")
            corePreferences.config = config
            refreshChildProtectionModeState()
            childProtectionModeReady.value = true
            Factory.instance().setDebugMode(corePreferences.debugLogs, Texts.appName)
            Log.i("[Application] Core context created")
            coreContext = CoreContext(context, config, service, useAutoStartDescription)
            DeviceStore // will add listener to core
            coreContext.start(startService = startService)

            // work around https://bugs.linphone.org/view.php?id=7714 - for demo purpose
            if (corePreferences.config.getBool("app","first_launch", true)) {
                corePreferences.config.setBool("app","first_launch", false)
            }
            coreContext.core.setStaticPicture(context.filesDir.absolutePath+"/nowebcamCIF.jpg")
            coreContext.core.ring = context.filesDir.absolutePath+"/ringtone.wav"
            coreContext.core.ringDuringIncomingEarlyMedia = true
            coreContext.core.isNativeRingingEnabled = true
            coreContext.core.friendsDatabasePath = context.filesDir.absolutePath+"/devices.db"
            setDefaultCodecs()
            return true

        }

        fun ensureChildProtectionPreferences(context: Context) {
            if (::childProtectionPreferences.isInitialized) {
                return
            }
            childProtectionPreferences = context.getSharedPreferences(CHILD_PROTECTION_PREFS, Context.MODE_PRIVATE)
            childProtectionModeState.value = childProtectionPreferences.getBoolean(CHILD_PROTECTION_MODE_KEY, childProtectionModeState.value ?: false)
        }

        fun getPersistedChildProtectionMode(context: Context): Boolean {
            ensureChildProtectionPreferences(context)
            return childProtectionPreferences.getBoolean(CHILD_PROTECTION_MODE_KEY, childProtectionModeState.value ?: false)
        }

        fun contextExists(): Boolean {
            return ::coreContext.isInitialized
        }

        fun setDefaultCodecs() {

            coreContext.core.audioPayloadTypes.forEach {
                if (!corePreferences.availableAudioCodecs.contains(it.mimeType.lowercase()))
                    it.enable(false)
            }

            if (corePreferences.config.getBool("app","default_codec_set", false)) {
                return
            }
            corePreferences.config.setBool("app","default_codec_set", true)

            coreContext.core.videoPayloadTypes.forEach {
                it.enable(corePreferences.enabledVideoCodecsByDefault.contains(it.mimeType.lowercase()))
            }
            coreContext.core.audioPayloadTypes.forEach {
                it.enable(corePreferences.enabledAudioCodecsByDefault.contains(it.mimeType.lowercase()))
            }
            corePreferences.config.sync()
        }

    }


    override fun onCreate() {
        super.onCreate()
        instance = this
        Customisation
        Texts
        // Pre-initialize core immediately for instant startup
        // force=true ensures initialization happens even if coreContext exists
        // startService=false avoids starting foreground service on cold start
        ensureCoreExists(applicationContext, force = true, startService = false)
        Compatibility.setupAppStartupListener(applicationContext)
        DeviceStore
    }

    fun tablet(): Boolean {
        return resources.getBoolean(R.bool.tablet)
    }

    fun smartPhone(): Boolean {
        return !tablet()
    }

    fun landcape(): Boolean {
        val orientation = resources.configuration.orientation
        return orientation == Configuration.ORIENTATION_LANDSCAPE
    }

}
