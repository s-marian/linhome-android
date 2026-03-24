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

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.linhome.GenericFragment
import org.linhome.LinhomeApplication
import org.linhome.R
import org.linhome.databinding.FragmentRtspStreamSettingsBinding
import org.linhome.ui.player.RtsplibActivity
import org.linhome.utils.DialogUtil
import org.linphone.core.tools.Log

/**
 * Fragment for configuring RTSP stream settings.
 * Allows users to set the stream URL, username, and password.
 */
class RTSPStreamSettingsFragment : GenericFragment() {

    private lateinit var binding: FragmentRtspStreamSettingsBinding
    private lateinit var viewModel: RTSPStreamViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRtspStreamSettingsBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this).get(RTSPStreamViewModel::class.java)
        
        // Set DialogUtil context for fragment
        org.linhome.utils.DialogUtil.updateContext(requireContext())
        
        binding.model = viewModel
        binding.view = this
        binding.lifecycleOwner = this
        
        setupObservers()
        setupListeners()
        
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear DialogUtil context when fragment is destroyed
        org.linhome.utils.DialogUtil.updateContext(null)
    }

    private fun setupObservers() {
        // Observe validation state
        viewModel.isValid.observe(viewLifecycleOwner, Observer { isValid ->
            binding.isValid = isValid
            updateValidationUI()
        })
        
        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner, Observer { error ->
            binding.errorMessage = error
        })
        
        // Observe save result
        viewModel.saveResult.observe(viewLifecycleOwner, Observer { result ->
            when (result) {
                is RTSPStreamViewModel.SaveResult.Success -> {
                    DialogUtil.toast(requireContext().getString(org.linhome.R.string.settings_rtsp_stream_saved))
                }
                is RTSPStreamViewModel.SaveResult.Error -> {
                    DialogUtil.error(result.message)
                }
            }
        })
        
        // Observe saving state
        viewModel.isSaving.observe(viewLifecycleOwner, Observer { isSaving ->
            binding.isSaving = isSaving
        })
    }

    private fun setupListeners() {
        // URL text change listener for real-time validation
        binding.urlEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                viewModel.validate()
            }
            false
        }
        
        // Username text change listener
        binding.usernameEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                viewModel.validate()
            }
            false
        }
        
        // Password text change listener
        binding.passwordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.validate()
            }
            false
        }
    }

    private fun updateValidationUI() {
        val isValid = viewModel.isValid.value ?: false
        val url = viewModel.streamUrl.value?.trim() ?: ""
        
        // Update URL field appearance
        binding.urlEditText.error = if (isValid) null else viewModel.errorMessage.value
        
        // Update save button state
        binding.saveButton.isEnabled = isValid
    }

    /**
     * Called when the user wants to view the RTSP stream.
     */
    fun onViewStreamClicked() {
        if (!viewModel.validate()) {
            DialogUtil.error(requireContext().getString(org.linhome.R.string.settings_rtsp_stream_url_invalid))
            return
        }
        
        val streamUrl = viewModel.buildAuthenticatedUrl()
        val intent = RtsplibActivity.createIntent(requireContext(), streamUrl)
        startActivity(intent)
    }

    /**
     * Called when the user wants to save the configuration.
     */
    fun onSaveClicked() {
        viewModel.saveConfiguration()
    }

    /**
     * Called when the user wants to cancel editing.
     */
    fun onCancelClicked() {
        parentFragmentManager.popBackStack()
    }

    companion object {
        const val TAG = "RTSPStreamSettingsFragment"
    }
}
