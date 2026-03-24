# RTSP Stream in Incoming Call Overlay - Technical Implementation Plan

## Executive Summary

This document provides a comprehensive technical analysis and implementation plan for integrating RTSP video stream display into the incoming call notification overlay system of the Linhome Android application. When an incoming call is received, the overlay will display the configured RTSP video stream instead of (or in addition to) the current snapshot display.

---

## 1. Current Implementation Analysis

### 1.1 Incoming Call Overlay System

The Linhome Android application currently implements an incoming call overlay system with the following components:

#### CallOverlayManager
**Location:** [`app/src/main/java/org/linhome/ui/call/CallOverlayManager.kt`](app/src/main/java/org/linhome/ui/call/CallOverlayManager.kt)

The `CallOverlayManager` class manages the full-screen incoming call overlay using `SYSTEM_ALERT_WINDOW` permission. Key characteristics:

- **Permission Required:** `android.permission.SYSTEM_ALERT_WINDOW`
- **Window Type:** `TYPE_APPLICATION_OVERLAY` (Android 8.0+) or `TYPE_PHONE` (older versions)
- **Layout:** Uses [`activity_call_overlay.xml`](app/src/main/res/layout/activity_call_overlay.xml)
- **Functionality:** Displays caller name, answer/decline buttons
- **Lifecycle:** Shows on `IncomingReceived`/`IncomingEarlyMedia` states, hides on call termination

#### CallIncomingActivity
**Location:** [`app/src/main/java/org/linhome/ui/call/CallIncomingActivity.kt`](app/src/main/java/org/linhome/ui/call/CallIncomingActivity.kt)

The `CallIncomingActivity` is the full-screen activity shown when overlay permission is not granted or overlay is disabled:

- Uses `TextureView` for video display (`videofullscreen`, `videocollapsed`)
- Displays incoming call video from the SIP call itself
- Has fullscreen toggle functionality
- Manages video window ID via `coreContext.core.nativeVideoWindowId`

#### NotificationsManager
**Location:** [`app/src/main/java/org/linhome/notifications/NotificationsManager.kt`](app/src/main/java/org/linhome/notifications/NotificationsManager.kt)

The notification system handles incoming call notifications when overlay is disabled:

- **Snapshot Display:** Uses `TextureView` with fake window ID for SIP video snapshot
- **Layout Options:** 
  - [`call_incoming_notification_content.xml`](app/src/main/res/layout/call_incoming_notification_content.xml) - Basic notification
  - [`call_incoming_notification_content_with_snapshot.xml`](app/src/main/res/layout/call_incoming_notification_content_with_snapshot.xml) - With snapshot image
- **Snapshot Mechanism:** Captures video frames via `call.takeVideoSnapshot()` and displays via Glide

### 1.2 RTSP Stream Viewer Implementation

The existing RTSP stream viewer provides the foundation for video playback:

#### RtsplibActivity
**Location:** [`app/src/main/java/org/linhome/ui/player/RtsplibActivity.kt`](app/src/main/java/org/linhome/ui/player/RtsplibActivity.kt)

Key implementation details:

- **Video Display:** Uses `TextureView` for RTSP stream rendering
- **Player:** Uses Linphone SDK's `Player` class
- **Surface Management:** Implements `TextureView.SurfaceTextureListener` for lifecycle management
- **Controls:** Play/pause, seek, timer display
- **Authentication:** Supports username/password in RTSP URL

#### CorePreferences RTSP Configuration
**Location:** [`app/src/main/java/org/linhome/linphonecore/CorePreferences.kt`](app/src/main/java/org/linhome/linphonecore/CorePreferences.kt:210-240)

Existing RTSP configuration storage:

```kotlin
var rtspStreamUrl: String
var rtspStreamUsername: String
var rtspStreamPassword: String
fun getRtspStreamConfiguration(): RTSPStream
```

---

## 2. Technical Feasibility Assessment

### 2.1 Can TextureView Be Used in a Notification Overlay?

**Answer: No, not directly.**

**Reasons:**
1. **RemoteViews Limitation:** Notification custom views use `RemoteViews`, which only supports a limited set of views (TextView, ImageView, ImageView, ViewStub, etc.)
2. **TextureView Not Supported:** `TextureView` is not among the supported views for `RemoteViews`
3. **Security Restrictions:** Android restricts complex view hierarchies in notifications for security and performance reasons

**Alternative Approaches:**

| Approach | Feasibility | Complexity | Notes |
|----------|-------------|------------|-------|
| Full-screen Intent with Activity | ✅ High | Medium | Launches `CallIncomingActivity` with RTSP stream |
| SYSTEM_ALERT_WINDOW Overlay with RTSP | ✅ High | High | Extends existing overlay system |
| Foreground Service with Notification | ⚠️ Limited | Medium | Can show static image, not live stream |
| Widget with TextureView | ❌ Not Supported | N/A | Widgets don't support TextureView |

### 2.2 Recommended Approach: SYSTEM_ALERT_WINDOW Overlay with RTSP

**Rationale:**
1. **Consistency:** Uses the same overlay mechanism already implemented for incoming calls
2. **User Experience:** Provides full-screen, immersive experience
3. **Existing Infrastructure:** Leverages `CallOverlayManager` and `activity_call_overlay.xml`
4. **Performance:** Better resource management than notification-based approaches

### 2.3 Performance Considerations

| Factor | Impact | Mitigation |
|--------|--------|------------|
| RTSP Decoding | High CPU/GPU usage | Use hardware acceleration (Linphone SDK) |
| Network Bandwidth | Variable based on stream quality | Implement adaptive bitrate if possible |
| Memory Usage | Texture buffers for video | Proper lifecycle management |
| Battery Drain | Continuous streaming | Stop stream when call ends |

---

## 3. Implementation Approach

### 3.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    Incoming Call Event                          │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│              CallOverlayManager.showIncomingCall()              │
│  - Checks showIncomingCallOverlay preference                    │
│  - Verifies SYSTEM_ALERT_WINDOW permission                      │
│  - Creates overlay with RTSP stream support                     │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│          RTSPOverlayView (New Custom View)                      │
│  - TextureView for RTSP stream display                          │
│  - RTSP Player integration                                      │
│  - Overlay controls (answer/decline)                            │
│  - Lifecycle management                                         │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│          RtsplibViewModel (Reused)                              │
│  - Manages RTSP player state                                    │
│  - Provides LiveData for UI binding                             │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 Component Changes

#### 3.2.1 Modified Components

| Component | File | Changes |
|-----------|------|---------|
| CallOverlayManager | [`CallOverlayManager.kt`](app/src/main/java/org/linhome/ui/call/CallOverlayManager.kt:44-193) | Add RTSP stream initialization, player management |
| activity_call_overlay.xml | [`activity_call_overlay.xml`](app/src/main/res/layout/activity_call_overlay.xml:1-104) | Add TextureView for RTSP stream |
| CorePreferences | [`CorePreferences.kt`](app/src/main/java/org/linhome/linphonecore/CorePreferences.kt:157-161) | Add RTSP overlay preference flag |
| SettingsFragment | [`SettingsFragment.kt`](app/src/main/java/org/linhome/ui/settings/SettingsFragment.kt:264-284) | Add RTSP overlay toggle listener |

#### 3.2.2 New Components

| Component | File | Purpose |
|-----------|------|---------|
| RTSPOverlayView | `app/src/main/java/org/linhome/ui/call/RTSPOverlayView.kt` | Custom view combining TextureView + RTSP player |
| RTSPOverlayViewModel | `app/src/main/java/org/linhome/ui/call/RTSPOverlayViewModel.kt` | Manages RTSP player state for overlay |

### 3.3 Implementation Steps

#### Step 1: Update Layout File

Modify [`activity_call_overlay.xml`](app/src/main/res/layout/activity_call_overlay.xml) to include TextureView for RTSP stream:

```xml
<androidx.constraintlayout.widget.ConstraintLayout>
    <!-- Existing callerName, deviceIcon, buttonsContainer -->
    
    <!-- New RTSP Video Display -->
    <TextureView
        android:id="@+id/rtspVideoView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_marginTop="80dp"
        app:layout_constraintTop_toBottomOf="@id/callerName"
        app:layout_constraintBottom_toTopOf="@id/buttonsContainer"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintDimensionRatio="16:9" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

#### Step 2: Create RTSPOverlayView

**Location:** `app/src/main/java/org/linhome/ui/call/RTSPOverlayView.kt`

```kotlin
class RTSPOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val textureView = TextureView(context)
    private var player: Player? = null
    private var rtspStream: RTSPStream? = null
    
    init {
        // Add TextureView to hierarchy
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        
        // Setup surface listener
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                startRTSPStream(surface)
            }
            // Other methods...
        }
    }
    
    private fun startRTSPStream(surface: SurfaceTexture) {
        // Get RTSP configuration from CorePreferences
        rtspStream = corePreferences.getRtspStreamConfiguration()
        
        // Build authenticated URL
        val streamUrl = rtspStream?.buildAuthenticatedUrl() ?: return
        
        // Create player
        player = coreContext.getPlayer()
        player?.setWindowId(surface)
        player?.play(streamUrl)
    }
    
    override fun onDetachedFromWindow() {
        player?.close()
        player = null
        super.onDetachedFromWindow()
    }
}
```

#### Step 3: Update CallOverlayManager

Modify [`CallOverlayManager.kt`](app/src/main/java/org/linhome/ui/call/CallOverlayManager.kt:54-110) to support RTSP:

```kotlin
fun showIncomingCall(call: Call) {
    if (!corePreferences.showIncomingCallOverlay) return
    if (!hasPermission()) return
    
    currentCall = call
    
    windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val params = createOverlayParams()
    
    // Use new layout with RTSP support
    val overlay = LayoutInflater.from(context).inflate(R.layout.activity_call_overlay, null)
    
    // Setup RTSP video view
    val rtspVideoView = overlay.findViewById<RTSPOverlayView>(R.id.rtspVideoView)
    rtspVideoView.startRTSPStream()
    
    // Setup caller name
    val callerName = overlay.findViewById<TextView>(R.id.callerName)
    callerName.text = call.remoteAddress.asString()
    
    // Setup buttons (existing code)
    // ...
    
    windowManager?.addView(overlay, params)
    callOverlay = overlay
}
```

#### Step 4: Add RTSP Overlay Preference

Add to [`CorePreferences.kt`](app/src/main/java/org/linhome/linphonecore/CorePreferences.kt:157-161):

```kotlin
var showIncomingCallOverlayWithRTSP: Boolean
    get() = config.getBool("app", "incoming_call_overlay_rtsp", false)
    set(value) {
        config.setBool("app", "incoming_call_overlay_rtsp", value)
    }
```

#### Step 5: Update Settings Fragment

Add RTSP overlay toggle to [`SettingsFragment.kt`](app/src/main/java/org/linhome/ui/settings/SettingsFragment.kt:264-322):

```kotlin
val incomingCallOverlayRTSPListener = object : SettingListenerStub() {
    override fun onBoolValueChanged(newValue: Boolean) {
        LinhomeApplication.corePreferences.showIncomingCallOverlayWithRTSP = newValue
        if (newValue && !LinhomeApplication.coreContext.isOverlayPermissionGranted()) {
            showOverlayPermissionDialog()
            LinhomeApplication.corePreferences.showIncomingCallOverlayWithRTSP = false
        }
    }
}
```

---

## 4. Configuration Options

### 4.1 User Preferences

Add the following settings to the UI:

| Setting | Key | Default | Description |
|---------|-----|---------|-------------|
| Enable Incoming Call Overlay | `incoming_call_overlay` | false | Show overlay instead of notification |
| Show RTSP Stream in Overlay | `incoming_call_overlay_rtsp` | false | Display RTSP stream in overlay |
| RTSP Stream URL | `rtsp_stream_url` | "" | Configured RTSP stream URL |
| RTSP Stream Username | `rtsp_stream_username` | "" | Optional authentication username |
| RTSP Stream Password | `rtsp_stream_password` | "" | Optional authentication password |

### 4.2 Display Modes

Implement three display modes for flexibility:

| Mode | Description | Configuration |
|------|-------------|---------------|
| Snapshot Only | Display static snapshot (current behavior) | `showIncomingCallOverlay=true`, `showIncomingCallOverlayWithRTSP=false` |
| RTSP Stream Only | Display live RTSP stream | `showIncomingCallOverlay=true`, `showIncomingCallOverlayWithRTSP=true` |
| RTSP Stream + Controls | Display RTSP stream with answer/decline buttons | Same as above, buttons always shown |

---

## 5. Technical Challenges and Solutions

### 5.1 Challenge: TextureView in Overlay

**Problem:** TextureView requires surface lifecycle management in overlay context.

**Solution:** 
- Implement `SurfaceTextureListener` in custom `RTSPOverlayView`
- Handle surface creation/destruction in overlay lifecycle
- Use `callOverlayManager.hideIncomingCall()` to clean up player on overlay dismissal

### 5.2 Challenge: RTSP Stream Authentication

**Problem:** RTSP streams may require authentication.

**Solution:**
- Use existing `RTSPStream.buildAuthenticatedUrl()` method
- Store credentials securely in `CorePreferences`
- Handle authentication failures gracefully with error message

### 5.3 Challenge: Resource Management

**Problem:** RTSP streaming consumes significant resources.

**Solution:**
- Stop RTSP player when overlay is hidden
- Release player resources in `onDetachedFromWindow()`
- Implement proper lifecycle management in `CallOverlayManager`

### 5.4 Challenge: Network Connectivity

**Problem:** RTSP stream may fail if network is unavailable.

**Solution:**
- Implement fallback to snapshot display on stream failure
- Show error message in overlay if stream cannot connect
- Log connection errors for debugging

---

## 6. Testing Strategy

### 6.1 Unit Tests

- Test `RTSPStream.buildAuthenticatedUrl()` with various URL formats
- Test authentication logic with/without credentials
- Test preference storage and retrieval

### 6.2 Integration Tests

- Test overlay display with RTSP stream
- Test overlay dismissal and resource cleanup
- Test fallback to snapshot on RTSP failure

### 6.3 Manual Testing Scenarios

| Scenario | Expected Result |
|----------|-----------------|
| Incoming call with RTSP overlay enabled | Overlay shows RTSP stream + answer/decline buttons |
| Incoming call with RTSP overlay disabled | Standard notification or full-screen activity |
| RTSP stream unavailable | Fallback to snapshot or error message |
| RTSP stream with authentication | Stream connects with stored credentials |
| Overlay dismissed during call | RTSP player stops, resources released |

---

## 7. Performance Optimization

### 7.1 Video Decoding

- Use hardware acceleration (Linphone SDK default)
- Limit stream resolution if device has limited resources
- Consider adaptive bitrate streaming (future enhancement)

### 7.2 Memory Management

- Release TextureView surface when overlay is hidden
- Close RTSP player immediately on call termination
- Implement object pooling for repeated overlay displays

### 7.3 Battery Optimization

- Stop RTSP stream when screen is off
- Implement stream pause/resume based on overlay visibility
- Consider lower frame rate for overlay display (15fps vs 30fps)

---

## 8. Future Enhancements

### 8.1 Multiple Stream Support

Allow users to configure different RTSP streams for different devices:

```kotlin
data class DeviceRTSPConfig(
    val deviceId: String,
    val streamUrl: String,
    val username: String = "",
    val password: String = ""
)
```

### 8.2 Picture-in-Picture Mode

Support PiP mode for RTSP stream when user switches to other apps:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    enterPictureInPictureMode(PictureInPictureParams.Builder().build())
}
```

### 8.3 Stream Quality Adjustment

Allow users to select stream quality based on network conditions:

| Quality | Resolution | Bitrate |
|---------|------------|---------|
| Low | 320x240 | 256 kbps |
| Medium | 640x480 | 512 kbps |
| High | 1280x720 | 1536 kbps |

---

## 9. Implementation Timeline

### Phase 1: Core Implementation (Week 1)
- [ ] Update `activity_call_overlay.xml` with TextureView
- [ ] Create `RTSPOverlayView` custom view
- [ ] Modify `CallOverlayManager` to support RTSP
- [ ] Add RTSP overlay preference to `CorePreferences`

### Phase 2: Settings Integration (Week 1)
- [ ] Add RTSP overlay toggle to `SettingsFragment`
- [ ] Update settings UI with RTSP configuration
- [ ] Implement permission handling for RTSP overlay

### Phase 3: Testing and Refinement (Week 2)
- [ ] Unit tests for RTSP stream handling
- [ ] Integration tests for overlay display
- [ ] Performance optimization
- [ ] Error handling and fallback mechanisms

### Phase 4: Documentation and Release (Week 2)
- [ ] Update user documentation
- [ ] Create feature demonstration
- [ ] Prepare release notes
- [ ] Code review and merge

---

## 10. Conclusion

Integrating RTSP stream display into the incoming call overlay is technically feasible and aligns with the existing architecture of the Linhome Android application. The implementation leverages the existing `CallOverlayManager` system and extends it with RTSP stream capabilities through a custom `RTSPOverlayView` component.

Key advantages of this approach:
- **Consistency:** Uses the same overlay mechanism as the existing system
- **User Experience:** Provides immersive full-screen video display
- **Maintainability:** Reuses existing RTSP player infrastructure
- **Flexibility:** Supports multiple display modes (snapshot, RTSP, hybrid)

The implementation requires modifications to four main components and introduces two new components, with an estimated development timeline of 2 weeks for complete implementation and testing.

---

## Appendix A: File References

### Modified Files
- [`app/src/main/java/org/linhome/ui/call/CallOverlayManager.kt`](app/src/main/java/org/linhome/ui/call/CallOverlayManager.kt)
- [`app/src/main/res/layout/activity_call_overlay.xml`](app/src/main/res/layout/activity_call_overlay.xml)
- [`app/src/main/java/org/linhome/linphonecore/CorePreferences.kt`](app/src/main/java/org/linhome/linphonecore/CorePreferences.kt)
- [`app/src/main/java/org/linhome/ui/settings/SettingsFragment.kt`](app/src/main/java/org/linhome/ui/settings/SettingsFragment.kt)

### New Files
- `app/src/main/java/org/linhome/ui/call/RTSPOverlayView.kt`
- `app/src/main/java/org/linhome/ui/call/RTSPOverlayViewModel.kt` (optional)

### Existing Supporting Files
- [`app/src/main/java/org/linhome/ui/player/RtsplibActivity.kt`](app/src/main/java/org/linhome/ui/player/RtsplibActivity.kt)
- [`app/src/main/java/org/linhome/ui/player/RtsplibViewModel.kt`](app/src/main/java/org/linhome/ui/player/RtsplibViewModel.kt)
- [`app/src/main/java/org/linhome/entities/RTSPStream.kt`](app/src/main/java/org/linhome/entities/RTSPStream.kt)

---

*Document Version: 1.0*
*Created: 2026-03-19*
*Author: Architect Mode Analysis*
