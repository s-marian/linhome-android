# Auto-Restart Feature Implementation - Completed

## Overview
This document describes the implementation of the auto-restart feature for the Linhome Android application.

## Features Implemented

### 1. Auto-start on Boot
The app now automatically starts when the device boots if the `autoStart` preference is enabled. This functionality was already implemented and continues to work.

### 2. Keep Service Alive
The app can keep running in the background using the `keepServiceAlive` preference. When enabled, the CoreService runs as a foreground service with a persistent notification, which helps prevent the system from killing the app.

## Important Note on "Restart on Kill"

Due to Android's strict limitations on background processes, **reliably detecting when an app is killed and automatically restarting it is not possible** without using workarounds that can cause issues:

1. **ActivityLifecycleCallbacks** - This approach caused the app to detect normal activity transitions (like SplashActivity being destroyed) as "app death" and trigger restart loops.

2. **onTrimMemory()** - This callback is only triggered when the system is under memory pressure, but it doesn't guarantee the app will be killed.

3. **Force Stop** - If a user force-stops the app from system settings, no restart will occur (expected Android behavior).

### Recommended Approach

Instead of trying to detect and restart after app death, the recommended approach is to use the **`keepServiceAlive`** feature:

1. Enable "Background mode" in Settings
2. This starts the CoreService as a foreground service
3. The foreground service has a persistent notification
4. This significantly reduces the chances of the app being killed by the system

## Implementation Details

### Files Created

| File | Purpose |
|------|---------|
| [`app/src/main/java/org/linhome/linphonecore/AppRestartManager.kt`](app/src/main/java/org/linhome/linphonecore/AppRestartManager.kt) | Manages restart scheduling and loop prevention |
| [`app/src/main/java/org/linhome/linphonecore/AppExitReceiver.kt`](app/src/main/java/org/linhome/linphonecore/AppExitReceiver.kt) | BroadcastReceiver that handles restart requests from AlarmManager |

### Files Modified

| File | Changes |
|------|---------|
| [`app/src/main/java/org/linhome/linphonecore/CorePreferences.kt`](app/src/main/java/org/linhome/linphonecore/CorePreferences.kt:115) | Added `restartOnKill` preference |
| [`app/src/main/java/org/linhome/linphonecore/BootReceiver.kt`](app/src/main/java/org/linhome/linphonecore/BootReceiver.kt:41) | Added restart-on-kill check during boot |
| [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml:8) | Added `WAKE_LOCK` permission and `AppExitReceiver` |
| [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml:19) | Added string resources for the new setting |
| [`app/src/main/res/layout/fragment_settings.xml`](app/src/main/res/layout/fragment_settings.xml:96) | Added switch widget for restart-on-kill setting |
| [`app/src/main/java/org/linhome/ui/settings/SettingsViewModel.kt`](app/src/main/java/org/linhome/ui/settings/SettingsViewModel.kt:41) | Added `restartOnKill` LiveData and listener |
| [`app/src/main/java/org/linhome/ui/settings/SettingsFragment.kt`](app/src/main/java/org/linhome/ui/settings/SettingsFragment.kt:254) | Added `getText()` method for string resolution |

## User Interface

The following settings are available in the Settings screen:

1. **Background mode** (`keepServiceAlive`) - Keep the CoreService running in the background with a persistent notification
2. **Restart on app kill** (`restartOnKill`) - A placeholder setting that is currently not functional due to Android limitations

## Android Limitations and Considerations

### 1. Force Stop
If a user force-stops the app from the system settings, the app will NOT restart. This is expected Android behavior - force stop explicitly tells the system not to run the app's components.

### 2. Doze Mode and Battery Optimization
- Background services may be restricted during Doze mode
- Users may need to whitelist the app from battery optimization for reliable background operation

### 3. Restart Loops
- The implementation includes a 10-second delay between restarts to prevent infinite loops
- The last restart time is tracked in SharedPreferences

### 4. Memory Pressure
- When the system kills the app due to memory pressure, there's no reliable way to detect this
- The `keepServiceAlive` feature helps mitigate this by running a foreground service

### 5. WAKE_LOCK Permission
- The `WAKE_LOCK` permission is required for `AlarmManager.setExactAndAllowWhileIdle()`
- This permission is declared in AndroidManifest.xml

## Configuration

### Preferences

| Preference | Key | Default | Description |
|------------|-----|---------|-------------|
| `autoStart` | `app.auto_start` | `true` | Start CoreService on boot |
| `restartOnKill` | `app.restart_on_kill` | `false` | Restart app when killed (not functional) |
| `keepServiceAlive` | `app.keep_service_alive` | `false` | Keep CoreService running in background |

## Recommendations for Users

To ensure the app stays running:

1. **Enable "Background mode"** in Settings - This keeps the CoreService running as a foreground service
2. **Whitelist from battery optimization** - Go to Settings > Apps > Linhome > Battery > Unrestricted
3. **Don't force stop the app** - Force stopping will prevent automatic restart

## Future Enhancements

Potential improvements for future versions:

1. **WorkManager Integration** - Use WorkManager for more reliable scheduling across all Android versions
2. **Foreground Service Foreground** - Implement a dedicated foreground service that monitors app health
3. **Crash Reporting** - Integrate with crash reporting services to track restart causes
4. **User Notification** - Notify users when the app restarts automatically
5. **Exclusion List** - Allow users to specify activities that should not trigger a restart
