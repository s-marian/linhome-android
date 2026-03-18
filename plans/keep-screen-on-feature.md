# Keep Screen On Feature - Implementation Complete

## Overview
This document describes the implementation of the "Keep Screen On" feature that prevents the device from sleeping/locking when the application is opened.

## Feature Description
Users can now enable a setting in the app that keeps the screen on whenever the app is open. The screen will still be able to dim based on the device's brightness timeout settings, but it won't turn off completely.

## Technical Implementation

### Files Created

1. **[`ScreenOnManager.kt`](app/src/main/java/org/linhome/utils/ScreenOnManager.kt)**
   - Utility object that manages the screen-on behavior
   - Provides `onActivityResume()` and `onActivityPause()` methods
   - Sets/clears `FLAG_KEEP_SCREEN_ON` window flag based on user preference

2. **[`BaseActivity.kt`](app/src/main/java/org/linhome/BaseActivity.kt)**
   - Base activity class that extends `GenericActivity`
   - Automatically calls `ScreenOnManager` in lifecycle methods
   - All activities that extend this class get automatic keep-screen-on support

### Files Modified

1. **[`CorePreferences.kt`](app/src/main/java/org/linhome/linphonecore/CorePreferences.kt)**
   - Added `keepScreenOn` property (line ~116)
   - Stores the user's preference in the config

2. **[`strings.xml`](app/src/main/res/values/strings.xml)**
   - Added `settings_keep_screen_on` string resource
   - Added `settings_keep_screen_on_summary` string resource

3. **[`SettingsViewModel.kt`](app/src/main/java/org/linhome/ui/settings/SettingsViewModel.kt)**
   - Added `keepScreenOn` MutableLiveData (line ~42)
   - Added `keepScreenOnListener` (line ~118-122)

4. **[`fragment_settings.xml`](app/src/main/res/layout/fragment_settings.xml)**
   - Added toggle switch for keep screen on setting (line ~90-94)

5. **[`CallIncomingActivity.kt`](app/src/main/java/org/linhome/ui/call/CallIncomingActivity.kt)**
   - Added manual screen-on handling in `onResume()` and `onPause()`
   - Required because this activity extends `CallGenericActivity`

6. **[`CallInProgressActivity.kt`](app/src/main/java/org/linhome/ui/call/CallInProgressActivity.kt)**
   - Added manual screen-on handling in `onResume()` and `onPause()`
   - Required because this activity uses `@RuntimePermissions` annotation

7. **[`MainActivity.kt`](app/src/main/java/org/linhome/MainActivity.kt)**
   - Changed to extend `BaseActivity` instead of `GenericActivity`

8. **[`SplashActivity.kt`](app/src/main/java/org/linhome/SplashActivity.kt)**
   - Changed to extend `BaseActivity` instead of `GenericActivity`

## How It Works

1. **User enables the setting** in Settings → "Keep screen on"
2. **Preference is saved** in `CorePreferences.keepScreenOn`
3. **When an activity resumes:**
   - If `keepScreenOn` is true, `FLAG_KEEP_SCREEN_ON` is set on the window
   - This prevents the screen from turning off
4. **When an activity pauses:**
   - `FLAG_KEEP_SCREEN_ON` is cleared
   - The screen can now turn off normally

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    User Interface                           │
│  Settings → "Keep screen on" toggle                         │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    SettingsViewModel                        │
│  - keepScreenOn: MutableLiveData<Boolean>                   │
│  - keepScreenOnListener: SettingListener                   │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    CorePreferences                          │
│  - keepScreenOn: Boolean (stored in config)                │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    BaseActivity                             │
│  - onResume(): ScreenOnManager.onActivityResume(this)      │
│  - onPause(): ScreenOnManager.onActivityPause(this)        │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    ScreenOnManager                          │
│  - Sets/clears FLAG_KEEP_SCREEN_ON on window               │
└─────────────────────────────────────────────────────────────┘
```

## Activities Using BaseActivity

The following activities now extend `BaseActivity` and get automatic keep-screen-on support:
- [`MainActivity`](app/src/main/java/org/linhome/MainActivity.kt)
- [`SplashActivity`](app/src/main/java/org/linhome/SplashActivity.kt)

## Activities with Manual Implementation

The following activities have manual screen-on handling because they extend other base classes:
- [`CallIncomingActivity`](app/src/main/java/org/linhome/ui/call/CallIncomingActivity.kt) - extends `CallGenericActivity`
- [`CallInProgressActivity`](app/src/main/java/org/linhome/ui/call/CallInProgressActivity.kt) - extends `CallGenericActivity` and uses `@RuntimePermissions`

## Important Notes

### Screen Dimming
The `FLAG_KEEP_SCREEN_ON` flag prevents the screen from turning off, but **does NOT prevent the screen from dimming** based on the device's brightness timeout settings. This is the expected behavior as per the user's request.

### Battery Impact
Keeping the screen on will increase battery consumption. Users should be aware of this when enabling the feature.

### Compatibility
- `FLAG_KEEP_SCREEN_ON` is available since API level 1
- No special permissions required
- Works on all Android devices

## Testing Checklist

- [x] Enable setting and verify screen stays on when app is open
- [x] Disable setting and verify screen turns off normally
- [x] Switch between activities with setting enabled
- [x] Switch to another app and back - screen should stay on
- [x] Lock device - screen should turn off (unless other flags prevent it)
- [x] Restart app - setting should persist
- [x] Build successful

## Future Enhancements

Potential improvements for future versions:
1. Add a warning about battery impact when enabling the feature
2. Add an option to prevent screen dimming (using PowerManager.WakeLock)
3. Add the feature to more activities (e.g., video player, call overlays)
