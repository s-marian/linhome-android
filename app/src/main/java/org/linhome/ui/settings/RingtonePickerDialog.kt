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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentResolver
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.linhome.R
import org.linhome.compatibility.Compatibility
import org.linhome.utils.DialogUtil
import org.linphone.core.tools.Log
import java.io.File

/**
 * Dialog fragment for selecting a ringtone file from device storage.
 * Uses Android's Storage Access Framework on API 29+ and Intent.ACTION_GET_CONTENT for older versions.
 */
class RingtonePickerDialog : DialogFragment() {

    companion object {
        const val TAG = "RingtonePickerDialog"
        const val REQUEST_PICK_RINGTONE = 1001
        const val REQUEST_PERMISSION_READ_STORAGE = 1002

        private val AUDIO_MIME_TYPES = arrayOf(
            "audio/*",
            "audio/mp3",
            "audio/mpeg",
            "audio/wav",
            "audio/wave",
            "audio/ogg",
            "audio/m4a",
            "audio/aac",
            "audio/amr"
        )
    }

    private val settingsViewModel: SettingsViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("[RingtonePicker] onCreate called")
        setStyle(STYLE_NORMAL, R.style.Theme_MaterialComponents_Dialog_Alert)
    }

    override fun onStart() {
        super.onStart()
        Log.i("[RingtonePicker] onStart called, dialog = $dialog")
        dialog?.window?.setLayout(
            resources.displayMetrics.widthPixels * 90 / 100,
            Activity.RESULT_CANCELED
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        Log.i("[RingtonePicker] onCreateDialog called")
        val context = requireContext()
        
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_ringtone_select)
            .setMessage(R.string.settings_ringtone_select_message)
            .setPositiveButton(R.string.select) { _, _ ->
                checkAndPickRingtone()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dismiss()
            }
            .setOnCancelListener { dialog ->
                dismiss()
            }
            .create()
        Log.i("[RingtonePicker] Dialog created: $dialog")
        return dialog
    }

    private fun checkAndPickRingtone() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+): Use Storage Access Framework
            pickRingtoneWithSAF()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 (API 29): Can use SAF or ACTION_GET_CONTENT
            pickRingtoneWithSAF()
        } else {
            // Android 6.0-9.0 (API 23-28): Need READ_EXTERNAL_STORAGE permission
            if (Compatibility.hasPermission(requireContext(), android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
                pickRingtoneWithIntent()
            } else {
                requestPermissionAndPick()
            }
        }
    }

    private fun pickRingtoneWithSAF() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_MIME_TYPES, AUDIO_MIME_TYPES)
        }
        startActivityForResult(intent, REQUEST_PICK_RINGTONE)
    }

    private fun pickRingtoneWithIntent() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_MIME_TYPES, AUDIO_MIME_TYPES)
        }
        startActivityForResult(Intent.createChooser(intent, getString(R.string.settings_ringtone_select)), REQUEST_PICK_RINGTONE)
    }

    fun showRingtonePicker(fragmentManager: androidx.fragment.app.FragmentManager) {
        show(fragmentManager, TAG)
    }

    private fun requestPermissionAndPick() {
        if (Compatibility.hasPermission(requireContext(), android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
            pickRingtoneWithIntent()
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_PERMISSION_READ_STORAGE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSION_READ_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    pickRingtoneWithIntent()
                } else {
                    DialogUtil.error("permission_denied")
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_PICK_RINGTONE -> {
                    data?.data?.let { uri ->
                        handleSelectedRingtone(uri)
                    }
                }
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            Log.i("[RingtonePicker] User cancelled ringtone selection")
        }
    }

    private fun handleSelectedRingtone(uri: Uri) {
        val context = requireContext()
        
        // Try to get the file path from URI
        val filePath = getFilePathFromUri(context, uri)
        
        if (filePath != null && File(filePath).exists()) {
            settingsViewModel.setRingtonePath(filePath)
            DialogUtil.toast("ringtone_selected")
            dismiss()
        } else {
            // For SAF URIs, we need to copy the file to app's private storage
            copyUriToFile(context, uri)
        }
    }

    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        // Try to get path from content URI
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path
        }
        
        // For MediaStore URIs, try to get the real path
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                if (cursor.moveToFirst()) {
                    return cursor.getString(columnIndex)
                }
            }
        }
        
        return null
    }

    private fun copyUriToFile(context: Context, uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // Create a file in app's private storage
                val fileName = "ringtone_${System.currentTimeMillis()}.mp3"
                val outputFile = File(context.filesDir, "share/sounds/linhome/$fileName")
                outputFile.parentFile?.mkdirs()
                
                outputFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                
                Log.i("[RingtonePicker] Copied ringtone to: ${outputFile.absolutePath}")
                settingsViewModel.setRingtonePath(outputFile.absolutePath)
                DialogUtil.toast("ringtone_selected")
                dismiss()
            }
        } catch (e: Exception) {
            Log.e("[RingtonePicker] Error copying ringtone file: ${e.message}")
            DialogUtil.error("ringtone_copy_failed")
        }
    }
}
