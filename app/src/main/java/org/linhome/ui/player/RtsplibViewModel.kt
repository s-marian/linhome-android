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

package org.linhome.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * ViewModel for managing RTSP stream player state using VLC/libVLC.
 */
class RtsplibViewModel(private val streamUrl: String, private val context: Context) : ViewModel() {

    val playing = androidx.lifecycle.MutableLiveData(false)
    val endReached = androidx.lifecycle.MutableLiveData(false)
    val position = androidx.lifecycle.MutableLiveData(0L)
    val userTracking = androidx.lifecycle.MutableLiveData(false)
    val userTrackingPosition = androidx.lifecycle.MutableLiveData(0L)
    val isPlaying = androidx.lifecycle.MutableLiveData(false)
    val error = androidx.lifecycle.MutableLiveData<String?>()

    private var rtspVlcPlayer: RtspVlcPlayer? = null

    /**
     * Factory for creating RtsplibViewModel instances.
     */
    class Factory(private val streamUrl: String, private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RtsplibViewModel(streamUrl, context) as T
        }
    }

    /**
     * Starts playing the RTSP stream.
     */
    fun startPlaying() {
        rtspVlcPlayer = RtspVlcPlayer(context)
        rtspVlcPlayer?.loadStream(streamUrl)
        playing.value = true
    }

    /**
     * Toggles play/pause state.
     */
    fun togglePlay() {
        if (playing.value!!) {
            pausePlay()
        } else {
            if (endReached.value == true) {
                playFromStart()
            } else {
                rtspVlcPlayer?.play()
                playing.value = true
            }
        }
    }

    /**
     * Pauses the stream.
     */
    fun pausePlay() {
        if (playing.value!!) {
            rtspVlcPlayer?.pause()
            playing.value = false
        }
    }

    /**
     * Resumes playing the stream.
     */
    fun resumePlay() {
        if (!playing.value!!) {
            rtspVlcPlayer?.play()
            playing.value = true
        }
    }

    /**
     * Plays from the beginning.
     */
    fun playFromStart() {
        seek(0)
    }

    /**
     * Seeks to a specific position in milliseconds.
     */
    fun seek(targetSeek: Int) {
        userTracking.value = false
        val duration = rtspVlcPlayer?.getDuration() ?: 0L
        if (targetSeek < duration.toInt()) {
            endReached.value = false
        }
        if (playing.value == true) {
            pausePlay()
        }
        rtspVlcPlayer?.seekTo(targetSeek.toLong())
        updatePosition()
        resumePlay()
    }

    /**
     * Updates the current position.
     */
    fun updatePosition() {
        position.value = rtspVlcPlayer?.getCurrentPosition() ?: 0L
    }

    /**
     * Closes the player and releases resources.
     */
    fun close() {
        rtspVlcPlayer?.release()
        rtspVlcPlayer = null
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }
}
