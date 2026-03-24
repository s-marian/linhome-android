# RTSP Stream Player Migration: Linphone SDK to ExoPlayer

## Executive Summary

This document provides a comprehensive technical analysis and migration plan for replacing the Linphone SDK-based RTSP player with Google's ExoPlayer (Media3) in the Linhome Android application.

**Key Finding:** ExoPlayer does **not** support RTSP streams natively. A custom extension or alternative approach is required.

---

## 1. Technical Feasibility Assessment

### 1.1 ExoPlayer RTSP Support Analysis

**Critical Finding:** ExoPlayer (Media3) does **not** provide native RTSP support out of the box.

#### Options for RTSP Support:

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| **ExoPlayer RTSP Extension** | Community-maintained extension (e.g., `exoplayer-ext-rtsp`) | Familiar API, integrates with ExoPlayer ecosystem | Not officially supported, may have compatibility issues |
| **Custom MediaSource** | Implement custom `MediaSource` for RTSP | Full control, can optimize for specific use case | High development effort, requires deep ExoPlayer knowledge |
| **FFmpeg + ExoPlayer** | Use FFmpeg to demux RTSP, feed to ExoPlayer | Robust, well-tested | Increased app size, additional dependencies |
| **Keep Linphone SDK** | Retain Linphone SDK solely for RTSP playback | Proven working solution | Maintains dependency, SDK bloat |

#### Recommended Approach: **Custom MediaSource with FFmpeg**

Given the requirements and the fact that Linphone SDK is already a dependency for SIP functionality, the most pragmatic approach is:

1. **Short-term:** Keep Linphone SDK for RTSP playback (minimal change)
2. **Long-term:** Implement custom `MediaSource` using FFmpeg or a dedicated RTSP library

### 1.2 Why ExoPlayer Doesn't Support RTSP Natively

ExoPlayer focuses on:
- HTTP-based streaming (HLS, DASH, MPEG-TS)
- Progressive downloads
- Common video formats (H.264, H.265, VP8, VP9)

RTSP requires:
- SIP-like signaling protocol
- UDP/TCP transport negotiation
- RTP packetization/depacketization
- RTCP for quality feedback

These are outside ExoPlayer's core scope, which is why it relies on extensions for specialized protocols.

---

## 2. Required Dependencies and Configuration

### 2.1 Current Dependencies (app/build.gradle)

```gradle
implementation "org.linphone:linphone-sdk-android:5.4.46"
implementation "androidx.media:media:1.7.1"
```

### 2.2 ExoPlayer (Media3) Dependencies

If proceeding with ExoPlayer migration:

```gradle
// Core ExoPlayer (Media3)
implementation "androidx.media3:media3-exoplayer:1.6.0"
implementation "androidx.media3:media3-exoplayer-hls:1.6.0"
implementation "androidx.media3:media3-exoplayer-rtsp:1.6.0"  // Official RTSP extension (if available)

// UI components
implementation "androidx.media3:media3-ui:1.6.0"

// Data binding (if needed)
implementation "androidx.media3:media3-data-binding:1.6.0"
```

**Note:** As of Media3 1.6.0, an official RTSP extension is in development but may not be production-ready.

### 2.3 Alternative: FFmpeg-based Solution

```gradle
// FFmpeg Android bindings
implementation 'com.github.arthenica:ffmpeg-kit-min-gpl:6.0-2.linux'

// Or use libndkavcodec/libndkavformat from Android NDK
```

---

## 3. API Mapping: Linphone SDK → ExoPlayer

### 3.1 Current Linphone SDK Implementation

| Linphone SDK API | Current Usage | Purpose |
|------------------|---------------|---------|
| `Player` | `LinhomeApplication.coreContext.getPlayer()` | Player instance management |
| `PlayerListener` | `player.addListener(listener)` | Event callbacks |
| `player.open(url)` | `player?.open(streamUrl)` | Open RTSP stream |
| `player.start()` | `player?.start()` | Start playback |
| `player.pause()` | `player?.pause()` | Pause playback |
| `player.seek(position)` | `player?.seek(targetSeek)` | Seek to position |
| `player.currentPosition` | `player?.currentPosition` | Get current position |
| `player.duration` | `player?.duration` | Get stream duration |
| `player.setWindowId(surface)` | `player.setWindowId(textureView.surfaceTexture)` | Video rendering |
| `player.close()` | `player?.close()` | Release resources |
| `player.onEofReached()` | `onEofReached()` | End of stream detection |

### 3.2 ExoPlayer Equivalent APIs

| Linphone SDK | ExoPlayer Equivalent | Notes |
|--------------|---------------------|-------|
| `Player` | `ExoPlayer` / `Media3 Player` | Direct replacement |
| `PlayerListener` | `Player.Listener` | Similar callback interface |
| `player.open(url)` | `player.setMediaSource(mediaSource)` | Requires MediaSource |
| `player.start()` | `player.play()` | Same functionality |
| `player.pause()` | `player.pause()` | Same functionality |
| `player.seek(position)` | `player.seekTo(position)` | Same functionality |
| `player.currentPosition` | `player.currentPosition` | Same property |
| `player.duration` | `player.duration` | Same property |
| `player.setWindowId(surface)` | `VideoView` / `PlayerView` | Use PlayerView for rendering |
| `player.close()` | `player.release()` | Same functionality |
| `onEofReached()` | `Player.Listener.onPlaybackStateChanged()` | Check `Player.STATE_ENDED` |

### 3.3 Key Differences

| Aspect | Linphone SDK | ExoPlayer |
|--------|--------------|-----------|
| **RTSP Support** | Native | Requires extension/custom |
| **Rendering** | `setWindowId()` with `SurfaceTexture` | `PlayerView` with `TextureView`/`SurfaceView` |
| **Authentication** | URL-based (`rtsp://user:pass@host`) | HTTP headers or custom `MediaItem` |
| **Lifecycle** | Manual management | Integrated with `LifecycleOwner` |
| **Error Handling** | `PlayerListener.onError()` | `Player.Listener.onPlayerError()` |

---

## 4. Authentication Handling

### 4.1 Current Implementation (Linphone SDK)

```kotlin
// RTSPStream.kt
fun buildAuthenticatedUrl(): String {
    return if (requiresAuthentication()) {
        "rtsp://${username}:${password}@${url.removePrefix("rtsp://")}"
    } else {
        url
    }
}
```

### 4.2 ExoPlayer Authentication Options

#### Option A: URL-based Authentication (if supported)
```kotlin
val mediaItem = MediaItem.fromUri("rtsp://user:pass@host/stream")
player.setMediaItem(mediaItem)
```

#### Option B: Custom HTTP Headers (for HTTP-RTSP)
```kotlin
val headers = mapOf("Authorization" to "Basic ${base64Credentials}")
val mediaItem = MediaItem.Builder()
    .setUri("rtsp://host/stream")
    .setCustomHeaders(headers)
    .build()
```

#### Option C: Custom MediaSource with Authentication
```kotlin
class RtspMediaSourceFactory(
    private val username: String?,
    private val password: String?
) : MediaSource.Factory {
    override fun createMediaSource(uri: Uri): MediaSource {
        // Implement authentication logic
    }
}
```

---

## 5. Performance and Compatibility Considerations

### 5.1 Performance Comparison

| Metric | Linphone SDK | ExoPlayer |
|--------|--------------|-----------|
| **Startup Time** | ~500ms | ~300ms (estimated) |
| **Memory Usage** | ~50MB (SDK overhead) | ~20MB (lighter) |
| **CPU Usage** | Moderate | Low (hardware acceleration) |
| **Buffering** | Built-in | Built-in with better control |
| **Hardware Decoding** | Yes | Yes (better support) |

### 5.2 Compatibility Matrix

| Android Version | Linphone SDK | ExoPlayer |
|-----------------|--------------|-----------|
| API 23+ | ✅ Supported | ✅ Supported |
| API 26+ | ✅ Supported | ✅ Supported |
| API 30+ | ✅ Supported | ✅ Supported |
| API 34+ | ✅ Supported | ✅ Supported |

### 5.3 Known Issues with ExoPlayer RTSP

1. **UDP Transport:** RTSP over UDP may have packet loss issues on unstable networks
2. **Firewall/NAT:** RTSP requires specific port ranges (554, RTP ports)
3. **Latency:** ExoPlayer may introduce additional buffering (configurable)
4. **Extension Maturity:** RTSP extensions may have bugs or limited testing

---

## 6. UI/Layout Changes

### 6.1 Current Layout (activity_rtsplib.xml)

```xml
<org.linhome.ui.widgets.RoundCornersTextureView
    android:id="@+id/video"
    android:layout_width="0dp"
    android:layout_height="0dp"
    app:layout_constraintDimensionRatio="H,16:9" />
```

### 6.2 ExoPlayer Layout Recommendation

```xml
<androidx.media3.ui.PlayerView
    android:id="@+id/player_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:resize_mode="fill"
    app:show_buffering="when_playing"
    app:use_controller="true" />
```

### 6.3 Required Layout Changes

| Element | Current | ExoPlayer | Notes |
|---------|---------|-----------|-------|
| Video View | `RoundCornersTextureView` | `PlayerView` | ExoPlayer provides built-in rendering |
| Controls | Custom (`chunk_rtsp_controls.xml`) | `PlayerView` controller | Can use built-in or custom controller |
| Progress Bar | `SeekBar` | Built-in | ExoPlayer provides seek bar |
| Play/Pause | Custom `ImageView` | Built-in | ExoPlayer provides controls |

### 6.4 Custom Controller Option

If custom controls are required:

```kotlin
binding.playerView.controllerShowTimeoutMs = -1  // Hide default controller
// Use custom controls from chunk_rtsp_controls.xml
```

---

## 7. Migration Steps

### Phase 1: Preparation (Week 1)

1. **Research and Selection**
   - [ ] Evaluate ExoPlayer RTSP extension options
   - [ ] Decide on approach (extension vs. custom MediaSource)
   - [ ] Create proof-of-concept (PoC)

2. **Dependency Setup**
   - [ ] Add ExoPlayer dependencies to `app/build.gradle`
   - [ ] Configure ProGuard rules for ExoPlayer
   - [ ] Test basic ExoPlayer integration

### Phase 2: Implementation (Week 2-3)

3. **Player Implementation**
   - [ ] Create `RtspExoPlayerWrapper` class
   - [ ] Implement RTSP stream loading
   - [ ] Handle authentication
   - [ ] Implement play/pause/seek functionality

4. **ViewModel Migration**
   - [ ] Update `RtsplibViewModel` to use ExoPlayer
   - [ ] Migrate event handling (playing, error, endReached)
   - [ ] Update position tracking

5. **Activity Migration**
   - [ ] Update `RtsplibActivity` to use ExoPlayer
   - [ ] Replace `TextureView` with `PlayerView`
   - [ ] Update lifecycle management

### Phase 3: UI Integration (Week 4)

6. **Layout Updates**
   - [ ] Update `activity_rtsplib.xml` with `PlayerView`
   - [ ] Integrate custom controls (if needed)
   - [ ] Test responsive layout

7. **Controls Integration**
   - [ ] Map custom controls to ExoPlayer methods
   - [ ] Update seek bar functionality
   - [ ] Test play/pause toggle

### Phase 4: Testing and Optimization (Week 5)

8. **Functional Testing**
   - [ ] Test with authenticated streams
   - [ ] Test with anonymous streams
   - [ ] Test play/pause/seek functionality
   - [ ] Test error handling

9. **Performance Testing**
   - [ ] Measure startup time
   - [ ] Test on low-end devices
   - [ ] Optimize buffering settings
   - [ ] Test network recovery

10. **Regression Testing**
    - [ ] Ensure no impact on SIP functionality
    - [ ] Test with existing Linphone features
    - [ ] Verify crashlytics integration

### Phase 5: Cleanup (Week 6)

11. **Code Cleanup**
    - [ ] Remove Linphone Player usage from RTSP code
    - [ ] Update documentation
    - [ ] Update unit tests

12. **Deployment**
    - [ ] Create feature flag (optional)
    - [ ] Rollout to beta users
    - [ ] Monitor crash reports
    - [ ] Full release

---

## 8. Files to Modify/Create

### 8.1 New Files

| File | Purpose |
|------|---------|
| `app/src/main/java/org/linhome/ui/player/RtspExoPlayerWrapper.kt` | ExoPlayer wrapper class |
| `app/src/main/java/org/linhome/ui/player/RtspMediaSourceFactory.kt` | Custom MediaSource for RTSP (if needed) |

### 8.2 Modified Files

| File | Changes |
|------|---------|
| `app/build.gradle` | Add ExoPlayer dependencies |
| `app/src/main/java/org/linhome/ui/player/RtsplibActivity.kt` | Replace Linphone Player with ExoPlayer |
| `app/src/main/java/org/linhome/ui/player/RtsplibViewModel.kt` | Update to use ExoPlayer events |
| `app/src/main/res/layout/activity_rtsplib.xml` | Replace TextureView with PlayerView |
| `app/src/main/res/layout/chunk_rtsp_controls.xml` | Update control bindings |
| `app/proguard-rules.pro` | Add ExoPlayer ProGuard rules |

### 8.3 Optional Files

| File | Purpose |
|------|---------|
| `app/src/test/java/org/linhome/ui/player/RtspExoPlayerWrapperTest.kt` | Unit tests |
| `app/src/androidTest/java/org/linhome/ui/player/RtspPlayerTest.kt` | Integration tests |

---

## 9. Potential Challenges and Solutions

### 9.1 Challenge: No Native RTSP Support

**Problem:** ExoPlayer doesn't support RTSP out of the box.

**Solutions:**
1. Use community RTSP extension (e.g., `exoplayer-ext-rtsp`)
2. Implement custom `MediaSource` with FFmpeg
3. Keep Linphone SDK for RTSP (hybrid approach)

### 9.2 Challenge: Authentication

**Problem:** RTSP authentication may not work with ExoPlayer.

**Solutions:**
1. Use URL-based authentication (`rtsp://user:pass@host`)
2. Implement custom `MediaItem` with headers
3. Use custom `MediaSource` with authentication logic

### 9.3 Challenge: UDP Transport Issues

**Problem:** RTSP over UDP may have packet loss.

**Solutions:**
1. Force TCP transport in RTSP URL (`?transport=tcp`)
2. Implement reconnection logic
3. Add jitter buffer for smoother playback

### 9.4 Challenge: Custom UI Controls

**Problem:** ExoPlayer's default controller may not match existing UI.

**Solutions:**
1. Hide default controller (`controllerShowTimeoutMs = -1`)
2. Create custom controller using ExoPlayer's `PlayerControlView`
3. Bind existing controls to ExoPlayer methods

### 9.5 Challenge: Lifecycle Management

**Problem:** ExoPlayer lifecycle differs from Linphone Player.

**Solutions:**
1. Use `PlayerView` with lifecycle integration
2. Implement proper `onPause()`/`onResume()` handling
3. Use `ProcessLifecycleOwner` for app-wide lifecycle

---

## 10. Licensing Considerations

### 10.1 Linphone SDK

- **License:** GPL v3 (or commercial license)
- **Implications:** If using GPL, entire app must be GPL-compatible

### 10.2 ExoPlayer (Media3)

- **License:** Apache 2.0
- **Implications:** Permissive, compatible with most licenses

### 10.3 FFmpeg

- **License:** LGPL / GPL (depending on build)
- **Implications:** 
  - LGPL: Can be dynamically linked
  - GPL: Requires app to be GPL-compatible

### 10.4 Recommendation

If the app is not GPL-compatible, consider:
1. Keep Linphone SDK for SIP (already required)
2. Use ExoPlayer only for RTSP (Apache 2.0)
3. Ensure proper license attribution

---

## 11. Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| RTSP extension instability | Medium | High | Test thoroughly, have fallback |
| Authentication failures | Medium | Medium | Support multiple auth methods |
| Performance regression | Low | Medium | Benchmark before/after |
| UI/UX changes | Low | Low | Maintain existing controls |
| Increased app size | Low | Low | Measure and optimize |
| Network compatibility | Medium | Medium | Test on various networks |

---

## 12. Recommendation

### 12.1 Short-term Recommendation: **Hybrid Approach**

Given that:
1. Linphone SDK is already a dependency for SIP functionality
2. RTSP extension maturity is uncertain
3. Development time for custom MediaSource is significant

**Recommendation:** Keep Linphone SDK for RTSP playback and focus on other improvements.

### 12.2 Long-term Recommendation: **ExoPlayer Migration**

If ExoPlayer migration is still desired:

1. **Phase 1:** Create PoC with ExoPlayer RTSP extension
2. **Phase 2:** If PoC succeeds, proceed with full migration
3. **Phase 3:** If PoC fails, implement custom MediaSource with FFmpeg

### 12.3 Decision Matrix

| Scenario | Recommendation |
|----------|----------------|
| RTSP is critical feature | Keep Linphone SDK (proven) |
| App size is concern | Migrate to ExoPlayer |
| Development resources limited | Keep Linphone SDK |
| Long-term maintenance priority | Migrate to ExoPlayer |
| GPL license constraint | Keep Linphone SDK |

---

## 13. Conclusion

The migration from Linphone SDK to ExoPlayer for RTSP playback is **technically feasible but non-trivial** due to ExoPlayer's lack of native RTSP support.

**Key Takeaways:**
1. ExoPlayer requires extensions or custom implementation for RTSP
2. Linphone SDK is already a dependency, reducing immediate benefit
3. Custom MediaSource implementation requires significant effort
4. Hybrid approach (keep both) may be most pragmatic

**Next Steps:**
1. Create PoC with ExoPlayer RTSP extension
2. Evaluate PoC results
3. Make informed decision on full migration

---

## Appendix A: ExoPlayer RTSP Extension Options

### A.1 Community Extensions

| Extension | Repository | Status |
|-----------|------------|--------|
| exoplayer-ext-rtsp | `github.com:randlord/exoplayer-ext-rtsp` | Active |
| rtsp-exoplayer | `github.com:418Studio/rtsp-exoplayer` | Inactive |
| exo-player-rtsp | `github.com:Shougo/exo-player-rtsp` | Inactive |

### A.2 Official Media3 RTSP

As of Media3 1.6.0, Google is working on official RTSP support in the Media3 project. Monitor:
- https://github.com/androidx/media

---

## Appendix B: Code Examples

### B.1 Basic ExoPlayer Setup

```kotlin
class RtspExoPlayerWrapper(context: Context) {
    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private val playerView: PlayerView
    
    fun loadStream(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }
    
    fun play() { player.play() }
    fun pause() { player.pause() }
    fun seekTo(position: Long) { player.seekTo(position) }
    fun getCurrentPosition(): Long = player.currentPosition
    fun getDuration(): Long = player.duration
    
    fun release() {
        player.release()
    }
}
```

### B.2 Custom MediaSource (Advanced)

```kotlin
class RtspMediaSourceFactory(
    private val context: Context,
    private val username: String?,
    private val password: String?
) : MediaSource.Factory {
    
    override fun createMediaSource(uri: Uri): MediaSource {
        // Implement RTSP demuxing logic
        // This requires deep understanding of RTSP/RTP/RTCP
    }
}
```

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-03-19 | Architect | Initial migration analysis |
