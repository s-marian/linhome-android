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

package org.linhome.ui.settings

import androidx.databinding.ViewDataBinding
import androidx.lifecycle.MutableLiveData
import org.linhome.LinhomeApplication
import org.linhome.customisation.Texts
import org.linhome.linphonecore.CorePreferences
import org.linhome.utils.databindings.ViewModelWithTools
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.MediaEncryption

class SettingsViewModel : ViewModelWithTools() {

    val audioCodecs = ArrayList<ViewDataBinding>()
    val videCodecs = ArrayList<ViewDataBinding>()

    val enableIpv6 = MutableLiveData(core.isIpv6Enabled())
    val latestSnapshotShown = MutableLiveData(corePref.showLatestSnapshot)
    var backgroundModeEnabled = MutableLiveData(corePref.keepServiceAlive)
    val incomingCallOverlay = MutableLiveData(corePref.showIncomingCallOverlay)
    var incomingCallOverlayRTSP = MutableLiveData(corePref.showIncomingCallOverlayWithRTSP)
    val keepScreenOn = MutableLiveData(corePref.keepScreenOn)
    val callButtonsAlwaysVisible = MutableLiveData(corePref.callButtonsAlwaysVisible)
    val immersiveMode = MutableLiveData(corePref.immersiveMode)
    val childProtectionMode = LinhomeApplication.childProtectionModeState
    
    // Ringtone
    val ringtonePath = MutableLiveData(corePref.ringtonePath)


    // Logs
    val enableDebugLogs = MutableLiveData(corePref.debugLogs)
    var logUploadResult = MutableLiveData<Pair<Core.LogCollectionUploadState, String>>()

    private val uploadCoreListener = object : CoreListenerStub() {
        override fun onLogCollectionUploadStateChanged(
            core: Core,
            state: Core.LogCollectionUploadState,
            url: String
        ) {
            logUploadResult.postValue(Pair(state, url))
        }
    }

    // Media Encryption
    private val encryptionValues = arrayListOf<Int>()

    val encryptionListener = object : SettingListenerStub() {
        override fun onListValueChanged(position: Int) {
            core.mediaEncryption = MediaEncryption.fromInt(encryptionValues[position])
            encryptionIndex.value = position
        }
    }
    val encryptionIndex = MutableLiveData<Int>()
    val encryptionLabels = MutableLiveData<ArrayList<String>>()

    init {
        core.addListener(uploadCoreListener)
        initEncryptionList()
    }

    override fun onCleared() {
        core.removeListener(uploadCoreListener)
        super.onCleared()
    }

    val enableIpv6Listener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            core.isIpv6Enabled = newValue
        }
    }

    val showLatestSnapshot = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            corePref.showLatestSnapshot = newValue
        }
    }

    val enableDebugLogsListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            corePref.debugLogs = newValue
        }
    }

    val enableBackgroundMode = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            corePref.keepServiceAlive = newValue
            if (newValue) {
                LinhomeApplication.coreContext.notificationsManager.startForeground()
            } else {
                LinhomeApplication.coreContext.notificationsManager.stopForegroundNotificationIfPossible()
            }
        }
    }

    val incomingCallOverlayListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            corePref.showIncomingCallOverlay = newValue
        }
    }

    val incomingCallOverlayRTSPListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            corePref.showIncomingCallOverlayWithRTSP = newValue
        }
    }

    val keepScreenOnListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            corePref.keepScreenOn = newValue
        }
    }

    val callButtonsAlwaysVisibleListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            corePref.callButtonsAlwaysVisible = newValue
        }
    }

    val immersiveModeListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            corePref.immersiveMode = newValue
        }
    }

    val childProtectionModeListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            LinhomeApplication.setChildProtectionMode(newValue)
        }
    }

    val ringtonePathListener = object : SettingListenerStub() {
        override fun onClicked() {
            // This listener is not used - the actual dialog showing is handled by SettingsFragment
        }
    }

    fun setRingtonePath(path: String) {
        corePref.ringtonePath = path
        ringtonePath.value = path
        // Apply the new ringtone to the core
        LinhomeApplication.coreContext.core.ring = path
    }

    fun showRingtonePicker(fragmentManager: androidx.fragment.app.FragmentManager) {
        org.linphone.core.tools.Log.i("[RingtoneDebug] showRingtonePicker called with fm: $fragmentManager")
        try {
            val ringtonePicker = RingtonePickerDialog()
            org.linphone.core.tools.Log.i("[RingtoneDebug] RingtonePickerDialog created: $ringtonePicker")
            ringtonePicker.show(fragmentManager, RingtonePickerDialog.TAG)
            org.linphone.core.tools.Log.i("[RingtoneDebug] Dialog show called")
        } catch (e: Exception) {
            org.linphone.core.tools.Log.e("[RingtoneDebug] Exception in showRingtonePicker: $e")
            e.printStackTrace()
        }
    }

    private fun initEncryptionList() {
        val labels = arrayListOf<String>()

        labels.add(Texts.get("none"))
        encryptionValues.add(MediaEncryption.None.toInt())

        if (core.mediaEncryptionSupported(MediaEncryption.SRTP)) {
            labels.add("SRTP")
            encryptionValues.add(MediaEncryption.SRTP.toInt())
        }

        encryptionLabels.value = labels
        encryptionIndex.value = encryptionValues.indexOf(core.mediaEncryption.toInt())
    }

}
