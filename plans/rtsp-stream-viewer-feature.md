# RTSP Stream Viewer Feature

## Overview
This document outlines the implementation plan for adding an RTSP stream viewer activity to the Linhome Android application. The feature allows users to configure and view a single RTSP video stream (H.264 codec) with URL, username, and password authentication.

## Requirements
- Display RTSP video stream with H.264 codec support
- Configure stream via URL, username, and password
- Settings screen for stream configuration
- Single stream configuration (not multiple streams)
- Full-screen video playback with controls
- **Anonymous access when username/password are empty**

## Architecture

### Component Structure

```
RTSP Stream Feature
├── Data Layer
│   ├── RTSPStream.kt - Data class for stream configuration
│   └── CorePreferences.kt - RTSP stream storage (existing file, extended)
│
├── UI Layer - Settings
│   ├── RTSPStreamViewModel.kt - Manages stream configuration state
│   ├── RTSPStreamSettingsFragment.kt - Settings UI fragment
│   └── fragment_rtsp_stream_settings.xml - Settings layout
│
├── UI Layer - Player
│   ├── RtsplibActivity.kt - Main activity for RTSP playback
│   ├── RtsplibViewModel.kt - Player state management
│   └── activity_rtsplib.xml - Player layout
│
└── Infrastructure
    ├── AndroidManifest.xml - Activity registration
    └── strings.xml - String resources
```

## Component Details

### 1. RTSPStream Data Class
**Location:** `app/src/main/java/org/linhome/entities/RTSPStream.kt`

```kotlin
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
     */
    fun buildAuthenticatedUrl(): String {
        return if (requiresAuthentication()) {
            "rtsp://${username}:${password}@${url.removePrefix("rtsp://")}"
        } else {
            url
        }
    }
}
```

### 2. CorePreferences Extension
**Location:** `app/src/main/java/org/linhome/linphonecore/CorePreferences.kt`

Add RTSP stream configuration storage:
- `rtspStreamUrl: String` - Stream URL
- `rtspStreamUsername: String` - Authentication username (optional)
- `rtspStreamPassword: String` - Authentication password (optional)

```kotlin
var rtspStreamUrl: String
    get() = config.getString("rtsp", "stream_url", "") ?: ""
    set(value) {
        config.setString("rtsp", "stream_url", value)
    }

var rtspStreamUsername: String
    get() = config.getString("rtsp", "stream_username", "") ?: ""
    set(value) {
        config.setString("rtsp", "stream_username", value)
    }

var rtspStreamPassword: String
    get() = config.getString("rtsp", "stream_password", "") ?: ""
    set(value) {
        config.setString("rtsp", "stream_password", value)
    }

/**
 * Retrieves the complete RTSP stream configuration.
 * Returns an RTSPStream with empty credentials if not configured.
 */
fun getRtspStreamConfiguration(): RTSPStream {
    return RTSPStream(
        url = rtspStreamUrl,
        username = rtspStreamUsername,
        password = rtspStreamPassword
    )
}
```

### 3. RTSPStreamViewModel
**Location:** `app/src/main/java/org/linhome/ui/settings/RTSPStreamViewModel.kt`

Responsibilities:
- Manage stream configuration state
- Validate URL format (must start with rtsp://)
- Handle save/cancel operations
- Provide LiveData for UI binding
- Support anonymous access (empty username/password)

```kotlin
class RTSPStreamViewModel : ViewModel() {
    val streamUrl = MutableLiveData("")
    val username = MutableLiveData("")
    val password = MutableLiveData("")
    val isValid = MutableLiveData(false)
    val errorMessage = MutableLiveData<String?>()
    
    // Validation: URL must start with rtsp://
    fun validateUrl(url: String): Boolean {
        return url.startsWith("rtsp://", ignoreCase = true)
    }
    
    // Save configuration
    fun saveConfiguration() {
        if (!validateUrl(streamUrl.value!!)) {
            errorMessage.value = "URL must start with rtsp://"
            return
        }
        // Save to CorePreferences
        // ...
    }
}
```

### 4. RTSPStreamSettingsFragment
**Location:** `app/src/main/java/org/linhome/ui/settings/RTSPStreamSettingsFragment.kt`

UI Components:
- EditText for RTSP URL (with hint: "rtsp://example.com/stream")
- EditText for username (optional)
- EditText for password (password type, optional)
- Save and Cancel buttons
- Validation feedback
- **Note: Username and password fields are optional for anonymous access**

**Layout:** `app/src/main/res/layout/fragment_rtsp_stream_settings.xml`

### 5. RtsplibActivity
**Location:** `app/src/main/java/org/linhome/ui/player/RtsplibActivity.kt`

Responsibilities:
- Load RTSP stream configuration from CorePreferences
- Check if authentication is required using `RTSPStream.requiresAuthentication()`
- Build authenticated URL if credentials are provided
- Initialize video player (using Linphone SDK Player or ExoPlayer)
- Display video in TextureView
- Handle play/pause controls
- Manage lifecycle (onPause, onDestroy)

```kotlin
class RtsplibActivity : GenericActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load configuration
        val rtspStream = corePref.getRtspStreamConfiguration()
        
        // Build URL with or without authentication
        val streamUrl = rtspStream.buildAuthenticatedUrl()
        
        // Initialize player with streamUrl
        // ...
    }
}
```

**Layout:** `app/src/main/res/layout/activity_rtsplib.xml`

### 6. RtsplibViewModel
**Location:** `app/src/main/java/org/linhome/ui/player/RtsplibViewModel.kt`

Responsibilities:
- Player state management (playing, paused, error)
- Video position tracking
- Error handling and reporting
- Handle authentication errors

## Implementation Steps

### Phase 1: Data Layer
1. Create RTSPStream data class with authentication helper methods
2. Add RTSP preferences to CorePreferences.kt

### Phase 2: Settings UI
3. Add string resources for RTSP settings
4. Create RTSPStreamViewModel
5. Create RTSPStreamSettingsFragment and layout
6. Add RTSP settings section to fragment_settings.xml
7. Update SettingsViewModel if needed

### Phase 3: Player Activity
8. Create RtsplibActivity
9. Create RtsplibViewModel
10. Create activity_rtsplib.xml layout
11. Register activity in AndroidManifest.xml

### Phase 4: Integration
12. Add INTERNET permission if not present
13. Test RTSP stream playback (authenticated and anonymous)
14. Verify settings persistence

## UI Flow

```mermaid
graph TD
    A[Settings Screen] --> B[RTSP Stream Settings Section]
    B --> C[RTSP Stream Settings Fragment]
    C --> D[Configure URL]
    D --> E[Configure Username Optional]
    E --> F[Configure Password Optional]
    F --> G[Save Configuration]
    G --> H[CorePreferences Storage]
    
    I[Launch RTSP Player] --> J[RtsplibActivity]
    J --> K[Load Configuration from CorePreferences]
    K --> L{Authentication Required?}
    L -->|Yes| M[Build Authenticated URL]
    L -->|No| N[Use Anonymous URL]
    M --> O[Initialize Video Player]
    N --> O
    O --> P[Display Stream in TextureView]
    P --> Q[Play/Pause Controls]
```

## Technical Considerations

### Video Player Implementation
The implementation can use either:
1. **Linphone SDK Player** - Already used in PlayerActivity, supports H.264
2. **ExoPlayer** - More flexible, better RTSP support

**Recommendation:** Use Linphone SDK Player for consistency with existing codebase, but verify RTSP support.

### RTSP URL Format
Expected format: `rtsp://[username:password@]host:port/path`
- Username and password can be embedded in URL or provided separately
- Separate fields allow for easier password management
- **Empty username/password results in anonymous access**

### Authentication Logic
```kotlin
// Anonymous access (no credentials)
rtsp://example.com/stream

// Authenticated access
rtsp://username:password@example.com/stream
```

### Permissions Required
- `android.permission.INTERNET` - For network access (check if already present)
- `android.permission.FOREGROUND_SERVICE_CAMERA` - For background video playback (if needed)

### Error Handling
- Invalid URL format
- Connection timeout
- Authentication failure (401/403 errors)
- Unsupported codec
- Network connectivity issues
- **Anonymous access failures**

## Testing Checklist
- [ ] Settings screen displays correctly
- [ ] URL validation works
- [ ] Configuration saves to CorePreferences
- [ ] Configuration loads on activity start
- [ ] Video stream plays correctly (authenticated)
- [ ] Video stream plays correctly (anonymous)
- [ ] H.264 codec is supported
- [ ] Username/password authentication works
- [ ] Anonymous access works (empty credentials)
- [ ] Play/pause controls function
- [ ] Activity lifecycle handled properly
- [ ] Error messages display correctly
- [ ] Authentication error handling works

## Future Enhancements
- Support for multiple RTSP streams
- Stream presets/quick access
- Picture-in-picture mode
- Recording capability
- Stream quality adjustment
- Network quality indicator
- Stream profile management (authenticated vs anonymous)
