# Splash Screen Elimination - Implementation Plan

## Overview

This plan outlines the steps to eliminate the splash screen delay and achieve instant perceived startup in the Linhome Android app.

---

## Current State Analysis

### What We Found

1. **SplashActivity** has a hardcoded 2-second delay:
   - File: [`SplashActivity.kt:47`](app/src/main/java/org/linhome/SplashActivity.kt:47)
   - Code: `delay(Theme.arbitraryValue("splash_display_duration_ms", "2000").toLong())`

2. **Application initialization** happens in `LinhomeApplication`:
   - Unzips brand assets
   - Initializes Linphone core
   - Loads configuration
   - Sets up databases

3. **Total startup time** is approximately 4-5 seconds:
   - ~2.8s for initialization
   - ~2s splash delay

---

## Implementation Phases

### Phase 1: Immediate Actions (Quick Wins)

#### 1.1 Remove Splash Delay
- Change `splash_display_duration_ms` from 2000 to 0 or 500ms
- Test if app feels faster

#### 1.2 Make MainActivity the Launcher
- Update `AndroidManifest.xml` to launch MainActivity directly
- Keep SplashActivity for potential deep links

---

### Phase 2: Pre-initialization Strategy

#### 2.1 Move Core Init to Application.onCreate
- Initialize Linphone core immediately in `LinhomeApplication.onCreate()`
- Use `force = true` to ensure initialization
- Set `startService = false` to avoid foreground service on cold start

#### 2.2 Handle Process Death
- Check if core already exists before re-initializing
- Use `::coreContext.isInitialized` and `!coreContext.stopped` checks

#### 2.3 Background Service Integration
- Ensure core service starts if `keepServiceAlive` is enabled
- Coordinate with `CoreService` for background operation

---

### Phase 3: UI Improvements

#### 3.1 Transparent Splash Theme
- Create transparent theme for immediate UI
- Show MainActivity content without delay

#### 3.2 Progressive Loading
- Show skeleton/placeholder UI while data loads
- Lazy load non-critical data (history, devices)

#### 3.3 Loading States
- Add loading indicators for async operations
- Show "ready" state when core is initialized

---

## Detailed Implementation Steps

### Step 1: Modify SplashActivity

**File:** `app/src/main/java/org/linhome/SplashActivity.kt`

**Changes:**
- Remove the 2-second delay
- Immediately navigate to MainActivity
- Or remove SplashActivity entirely

### Step 2: Update AndroidManifest

**File:** `app/src/main/AndroidManifest.xml`

**Changes:**
- Change MainActivity to `android:exported="true"`
- Add LAUNCHER intent filter to MainActivity
- Remove LAUNCHER intent filter from SplashActivity (or keep SplashActivity)

### Step 3: Pre-initialize in Application

**File:** `app/src/main/java/org/linhome/LinhomeApplication.kt`

**Changes:**
```kotlin
override fun onCreate() {
    super.onCreate()
    instance = this
    
    // Pre-initialize core immediately
    ensureCoreExists(this, force = true, startService = false)
}
```

### Step 4: Update MainActivity

**File:** `app/src/main/java/org/linhome/MainActivity.kt`

**Changes:**
- Add loading state handling
- Show skeleton UI if core not ready
- Lazy load non-critical data

---

## Risk Mitigation

### Risk 1: App Crash on Cold Start
**Mitigation:** Add try-catch around initialization, graceful degradation

### Risk 2: Memory Issues
**Mitigation:** Monitor memory usage, lazy load non-critical components

### Risk 3: Core Already Running
**Mitigation:** Check `::coreContext.isInitialized` before re-initializing

### Risk 4: Boot Receiver Conflicts
**Mitigation:** Coordinate with `BootReceiver` to avoid duplicate initialization

---

## Testing Checklist

- [ ] Cold start (app not running)
- [ ] Warm start (app in background)
- [ ] Boot receiver auto-start
- [ ] Process death and restoration
- [ ] Memory usage monitoring
- [ ] Network reconnection scenarios
- [ ] Incoming call handling

---

## Success Metrics

1. **Perceived startup time:** < 1 second (from tap to visible UI)
2. **Functional startup time:** < 3 seconds (core ready)
3. **No splash screen delay visible**
4. **No crashes or ANRs**

---

## Alternative Approaches Considered

### Approach A: Keep Splash, Reduce Delay
- Simply reduce delay to 500ms
- **Pros:** Minimal changes
- **Cons:** Still has artificial delay

### Approach B: Transparent Splash
- Show transparent splash, then MainActivity
- **Pros:** No visible delay
- **Cons:** Still requires activity transition

### Approach C: Direct Launch (Recommended)
- Remove splash, launch MainActivity directly
- **Pros:** Fastest, simplest
- **Cons:** Requires careful initialization

---

## Next Steps

1. Review and approve this plan
2. Switch to Code mode for implementation
3. Implement Phase 1 (quick wins)
4. Test and iterate
5. Implement Phase 2 and 3

---

## Questions for Review

1. Should we keep SplashActivity for deep links or remove entirely?
2. Is pre-initialization in Application.onCreate acceptable for memory usage?
3. Should we show a loading indicator during core initialization?
4. Are there any specific user flows that need special handling?
