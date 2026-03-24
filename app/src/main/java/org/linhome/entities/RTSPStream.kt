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

package org.linhome.entities

/**
 * Data class representing an RTSP stream configuration.
 *
 * @property url The RTSP stream URL (e.g., "rtsp://example.com/stream")
 * @property username Optional authentication username (empty for anonymous access)
 * @property password Optional authentication password (empty for anonymous access)
 */
data class RTSPStream(
    val url: String,
    val username: String = "",
    val password: String = ""
) {
    /**
     * Returns true if authentication is required (username or password is provided).
     * Returns false for anonymous access.
     */
    fun requiresAuthentication(): Boolean = username.isNotEmpty() || password.isNotEmpty()

    /**
     * Builds the RTSP URL with optional authentication credentials.
     * If no credentials are provided, returns the original URL.
     *
     * Example with authentication:
     *   Input: "rtsp://example.com/stream", username="user", password="pass"
     *   Output: "rtsp://user:pass@example.com/stream"
     *
     * Example without authentication:
     *   Input: "rtsp://example.com/stream", username="", password=""
     *   Output: "rtsp://example.com/stream"
     */
    fun buildAuthenticatedUrl(): String {
        return if (requiresAuthentication()) {
            val path = url.removePrefix("rtsp://").removePrefix("RTSP://")
            "rtsp://${username}:${password}@${path}"
        } else {
            url
        }
    }
}
