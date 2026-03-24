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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.linhome.LinhomeApplication
import org.linhome.entities.RTSPStream

/**
 * ViewModel for managing RTSP stream configuration state.
 */
class RTSPStreamViewModel : ViewModel() {
    
    private val _streamUrl = MutableLiveData<String>()
    val streamUrl: LiveData<String> = _streamUrl
    
    private val _username = MutableLiveData<String>()
    val username: LiveData<String> = _username
    
    private val _password = MutableLiveData<String>()
    val password: LiveData<String> = _password
    
    private val _isValid = MutableLiveData<Boolean>(false)
    val isValid: LiveData<Boolean> = _isValid
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _isSaving = MutableLiveData<Boolean>(false)
    val isSaving: LiveData<Boolean> = _isSaving
    
    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> = _saveResult
    
    init {
        loadConfiguration()
    }
    
    /**
     * Loads the current RTSP stream configuration from CorePreferences.
     */
    fun loadConfiguration() {
        val config = LinhomeApplication.corePreferences.getRtspStreamConfiguration()
        _streamUrl.value = config.url
        _username.value = config.username
        _password.value = config.password
        validate()
    }
    
    /**
     * Sets the stream URL.
     */
    fun setStreamUrl(url: String) {
        _streamUrl.value = url
        validate()
    }
    
    /**
     * Sets the username.
     */
    fun setUsername(username: String) {
        _username.value = username
        validate()
    }
    
    /**
     * Sets the password.
     */
    fun setPassword(password: String) {
        _password.value = password
        validate()
    }
    
    /**
     * Validates the current configuration.
     * Returns true if the URL is valid (starts with rtsp://).
     */
    fun validate(): Boolean {
        val url = _streamUrl.value?.trim() ?: ""
        
        if (url.isEmpty()) {
            _isValid.value = false
            _errorMessage.value = "Stream URL is required"
            return false
        }
        
        if (!url.startsWith("rtsp://", ignoreCase = true)) {
            _isValid.value = false
            _errorMessage.value = "URL must start with rtsp://"
            return false
        }
        
        _isValid.value = true
        _errorMessage.value = null
        return true
    }
    
    /**
     * Saves the current configuration to CorePreferences.
     */
    fun saveConfiguration() {
        if (!validate()) {
            return
        }
        
        _isSaving.value = true
        
        try {
            LinhomeApplication.corePreferences.rtspStreamUrl = _streamUrl.value ?: ""
            LinhomeApplication.corePreferences.rtspStreamUsername = _username.value ?: ""
            LinhomeApplication.corePreferences.rtspStreamPassword = _password.value ?: ""
            
            _saveResult.value = SaveResult.Success
        } catch (e: Exception) {
            _saveResult.value = SaveResult.Error(e.message ?: "Unknown error")
        } finally {
            _isSaving.value = false
        }
    }
    
    /**
     * Clears the configuration (resets to empty values).
     */
    fun clearConfiguration() {
        _streamUrl.value = ""
        _username.value = ""
        _password.value = ""
        _isValid.value = false
        _errorMessage.value = null
    }
    
    /**
     * Creates an RTSPStream object from the current configuration.
     */
    fun getRtspStream(): RTSPStream {
        return RTSPStream(
            url = _streamUrl.value ?: "",
            username = _username.value ?: "",
            password = _password.value ?: ""
        )
    }
    
    /**
     * Checks if the current configuration requires authentication.
     */
    fun requiresAuthentication(): Boolean {
        val stream = getRtspStream()
        return stream.requiresAuthentication()
    }
    
    /**
     * Builds the authenticated URL from the current configuration.
     */
    fun buildAuthenticatedUrl(): String {
        val stream = getRtspStream()
        return stream.buildAuthenticatedUrl()
    }
    
    /**
     * Result of save operation.
     */
    sealed class SaveResult {
        object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }
}
