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

import android.content.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.linhome.BR
import org.linhome.GenericFragment
import org.linhome.LinhomeApplication
import org.linhome.R
import org.linhome.customisation.Texts
import org.linhome.databinding.FragmentSettingsBinding
import org.linhome.utils.DialogUtil
import org.linphone.core.Core
import org.linphone.core.PayloadType
import org.linphone.core.tools.Log
import org.linhome.ui.settings.RTSPStreamSettingsFragment


class SettingsFragment : GenericFragment() {

    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var binding: FragmentSettingsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        settingsViewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)
        
        // Set DialogUtil context for fragment
        org.linhome.utils.DialogUtil.updateContext(requireContext())
        
        initCodecsList(
            LinhomeApplication.coreContext.core.audioPayloadTypes.filter { LinhomeApplication.corePreferences.availableAudioCodecs.contains(it.mimeType.lowercase()) }.toTypedArray(),
            settingsViewModel.audioCodecs,
            true
        )
        initCodecsList(
            LinhomeApplication.coreContext.core.videoPayloadTypes,
            settingsViewModel.videCodecs
        )
        binding.model = settingsViewModel
        binding.view = this
        binding.lifecycleOwner = this
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear DialogUtil context when fragment is destroyed
        org.linhome.utils.DialogUtil.updateContext(null)
    }

    private fun initCodecsList(
        payloads: Array<PayloadType>,
        target: ArrayList<ViewDataBinding>,
        showRate: Boolean = false
    ) {
        for (payload in payloads) {
            val binding = DataBindingUtil.inflate<ViewDataBinding>(
                LayoutInflater.from(requireContext()),
                R.layout.settings_widget_switch,
                null,
                false
            )
            binding.setVariable(BR.title, payload.mimeType)
            if (showRate)
                binding.setVariable(BR.subtitle, "${payload.clockRate} Hz")
            binding.setVariable(BR.checked, payload.enabled())
            binding.setVariable(BR.listener, object : SettingListenerStub() {
                override fun onBoolValueChanged(newValue: Boolean) {
                    payload.enable(newValue)
                }
            })
            binding.lifecycleOwner = this
            target.add(binding)
        }
    }


    val sendLogsListener = object : SettingListenerStub() {
        override fun onClicked() {
            showProgress()
            binding.sendLogs.root.isEnabled = false
            settingsViewModel.logUploadResult.observe(viewLifecycleOwner, Observer { result ->
                when (result.first) {
                    Core.LogCollectionUploadState.InProgress -> {
                    }
                    Core.LogCollectionUploadState.NotDelivered -> {
                        hideProgress()
                        binding.sendLogs.root.isEnabled = true
                        DialogUtil.error("log_upload_failed")
                    }
                    Core.LogCollectionUploadState.Delivered -> {
                        hideProgress()
                        binding.sendLogs.root.isEnabled = true
                        val clipboard =
                            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Logs url", result.second)
                        clipboard.setPrimaryClip(clip)
                        DialogUtil.toast("log_upload_success")
                        shareUploadedLogsUrl(result.second)
                    }
                }
            })
            LinhomeApplication.coreContext.core.uploadLogCollection()
        }
    }

    val clearLogsListener = object : SettingListenerStub() {
        override fun onClicked() {
            LinhomeApplication.coreContext.core.resetLogCollection()
            DialogUtil.toast("log_clear_success")
        }
    }

    private fun shareUploadedLogsUrl(info: String) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(
            Intent.EXTRA_EMAIL,
            arrayOf(Texts.get("support_email_android"))
        )
        intent.putExtra(Intent.EXTRA_SUBJECT, "${Texts.appName} Logs")
        intent.putExtra(Intent.EXTRA_TEXT, info)
        intent.type = "application/zip"

        try {
            startActivity(Intent.createChooser(intent, "Send mail..."))
        } catch (ex: ActivityNotFoundException) {
            Log.e(ex)
        }
    }

    private val pickRingtoneLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        org.linphone.core.tools.Log.i("[RingtoneDebug] onActivityResult: uri=$uri")
        uri?.let { handleSelectedRingtone(it) }
    }
    
    val ringtonePathListener = object : SettingListenerStub() {
        override fun onClicked() {
            org.linphone.core.tools.Log.i("[RingtoneDebug] ringtonePathListener onClicked called")
            try {
                val context = requireContext()
                org.linphone.core.tools.Log.i("[RingtoneDebug] Showing AlertDialog")
                
                androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle(org.linhome.R.string.settings_ringtone_select)
                    .setMessage(org.linhome.R.string.settings_ringtone_select_message)
                    .setPositiveButton(org.linhome.R.string.select) { dialog, _ ->
                        org.linphone.core.tools.Log.i("[RingtoneDebug] User clicked select")
                        pickRingtoneLauncher.launch(arrayOf(
                            "audio/*",
                            "audio/mp3",
                            "audio/mpeg",
                            "audio/wav",
                            "audio/wave",
                            "audio/ogg",
                            "audio/m4a",
                            "audio/aac",
                            "audio/amr"
                        ))
                        dialog.dismiss()
                    }
                    .setNegativeButton(org.linhome.R.string.cancel) { dialog, _ ->
                        org.linphone.core.tools.Log.i("[RingtoneDebug] User clicked cancel")
                        dialog.dismiss()
                    }
                    .setOnCancelListener { dialog ->
                        org.linphone.core.tools.Log.i("[RingtoneDebug] Dialog cancelled")
                        dialog.dismiss()
                    }
                    .show()
                
                org.linphone.core.tools.Log.i("[RingtoneDebug] AlertDialog shown")
            } catch (e: Exception) {
                org.linphone.core.tools.Log.e("[RingtoneDebug] Error showing ringtone picker: $e")
                e.printStackTrace()
            }
        }
    }
    
    private fun handleSelectedRingtone(uri: android.net.Uri) {
        val context = requireContext()
        val filePath = getFilePathFromUri(context, uri)
        
        if (filePath != null && java.io.File(filePath).exists()) {
            settingsViewModel.setRingtonePath(filePath)
            org.linhome.utils.DialogUtil.toast("ringtone_selected")
        } else {
            copyUriToFile(context, uri)
        }
    }
    
    private fun getFilePathFromUri(context: android.content.Context, uri: android.net.Uri): String? {
        if (uri.scheme == android.content.ContentResolver.SCHEME_FILE) {
            return uri.path
        }
        
        if (uri.scheme == android.content.ContentResolver.SCHEME_CONTENT) {
            val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                if (cursor.moveToFirst()) {
                    return cursor.getString(columnIndex)
                }
            }
        }
        
        return null
    }
    
    private fun copyUriToFile(context: android.content.Context, uri: android.net.Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = "ringtone_${System.currentTimeMillis()}.mp3"
                val outputFile = java.io.File(context.filesDir, "share/sounds/linhome/$fileName")
                outputFile.parentFile?.mkdirs()
                
                outputFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                
                org.linphone.core.tools.Log.i("[RingtonePicker] Copied ringtone to: ${outputFile.absolutePath}")
                settingsViewModel.setRingtonePath(outputFile.absolutePath)
                org.linhome.utils.DialogUtil.toast("ringtone_selected")
            }
        } catch (e: Exception) {
            org.linphone.core.tools.Log.e("[RingtonePicker] Error copying ringtone file: ${e.message}")
            org.linhome.utils.DialogUtil.error("ringtone_copy_failed")
        }
    }

    val incomingCallOverlayListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            // Always save the value first
            LinhomeApplication.corePreferences.showIncomingCallOverlay = newValue
            if (newValue) {
                // Check if overlay permission is granted
                if (!LinhomeApplication.coreContext.isOverlayPermissionGranted()) {
                    // Permission not granted, show dialog and revert the toggle
                    showOverlayPermissionDialog()
                    // Revert the setting
                    LinhomeApplication.corePreferences.showIncomingCallOverlay = false
                }
            }
        }

        override fun onClicked() {
            if (!LinhomeApplication.coreContext.isOverlayPermissionGranted()) {
                showOverlayPermissionDialog()
            }
        }
    }

    val incomingCallOverlayRTSPListener = object : SettingListenerStub() {
        override fun onBoolValueChanged(newValue: Boolean) {
            // Always save the value first
            LinhomeApplication.corePreferences.showIncomingCallOverlayWithRTSP = newValue
            if (newValue) {
                // Check if overlay permission is granted
                if (!LinhomeApplication.coreContext.isOverlayPermissionGranted()) {
                    // Permission not granted, show dialog and revert the toggle
                    showOverlayPermissionDialog()
                    // Revert the setting
                    LinhomeApplication.corePreferences.showIncomingCallOverlayWithRTSP = false
                }
            }
        }

        override fun onClicked() {
            if (!LinhomeApplication.coreContext.isOverlayPermissionGranted()) {
                showOverlayPermissionDialog()
            }
        }
    }

    private fun showOverlayPermissionDialog() {
        val context = requireContext()
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.overlay_permission_required)
            .setMessage(R.string.overlay_permission_message)
            .setPositiveButton(R.string.grant_permission) { dialog, _ ->
                // Launch the overlay permission settings
                val intent = LinhomeApplication.coreContext.getOverlaySettingsIntent()
                startActivity(intent)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    var rtspStreamListener: SettingListenerStub = object : SettingListenerStub() {
        override fun onClicked() {
            org.linphone.core.tools.Log.i("[RTSP] rtspStreamListener onClicked called")
            try {
                // Launch RTSP stream settings fragment
                org.linphone.core.tools.Log.i("[RTSP] Creating RTSPStreamSettingsFragment")
                val rtspFragment = RTSPStreamSettingsFragment()
                org.linphone.core.tools.Log.i("[RTSP] Fragment created, starting transaction")
                // Use childFragmentManager for FragmentContainerView
                childFragmentManager.beginTransaction()
                    .replace(R.id.container, rtspFragment, RTSPStreamSettingsFragment.TAG)
                    .addToBackStack(null)
                    .commit()
                org.linphone.core.tools.Log.i("[RTSP] Transaction committed successfully")
            } catch (e: Exception) {
                org.linphone.core.tools.Log.e("[RTSP] Error launching RTSP settings: ${e.message}")
                e.printStackTrace()
            }
        }
    }


}
