# Fix: Server Friend List Creation Only for Linhome Accounts

## Problem

The error message "Error fetching remotely configured devices" was appearing in the Android UI even when no Linhome account was configured. This happened because:

1. The app unconditionally created a server friend list for `https://subscribe.linhome.org/contacts/vcard` during core initialization
2. This friend list automatically attempted to sync from the server
3. The sync failed because the user's account was on a different domain (e.g., `core.homelabs.ro` instead of `sip.linhome.org`)
4. The `syncFailed` LiveData was set to `true`, triggering the error dialog

## Root Cause

The server friend list was created in the `onFriendListCreated` callback without checking if a Linhome account was configured. The `fetchVCards()` function correctly checked for a Linhome account before setting up the vCard list URL, but the friend list itself was created by the Linphone core regardless.

## Solution

Modified the `DeviceStore` object in [`app/src/main/java/org/linhome/store/DeviceStore.kt`](app/src/main/java/org/linhome/store/DeviceStore.kt) to only create and use the server friend list when a Linhome account is detected.

### Changes Made

#### 1. Added `hasLinhomeAccount()` Helper Function

```kotlin
/**
 * Checks if there is a Linhome account configured (account domain matches loginDomain).
 * Excludes push gateway accounts from the check.
 */
private fun hasLinhomeAccount(): Boolean {
    val core = LinhomeApplication.coreContext.core
    val nonPushAccounts = core.accountList.filter { it.params?.idkey != LinhomeAccount.PUSH_GW_ID_KEY }
    if (nonPushAccounts.isEmpty()) {
        return false
    }
    return nonPushAccounts.first()?.params?.domain == LinhomeApplication.corePreferences.loginDomain
}
```

This function:
- Filters out push gateway accounts (which are auto-created)
- Checks if any remaining account's domain matches `loginDomain`
- Returns `true` only if a Linhome account is configured

#### 2. Modified `onFriendListCreated` Callback

**Before:**
```kotlin
override fun onFriendListCreated(core: Core, friendList: FriendList) {
    if (corePreferences.vcardListUrl.equals(friendList.displayName) && serverFriendList == null) {
        serverFriendList = friendList
        friendList.addListener(serverFriendListListener)
    }
    // ...
}
```

**After:**
```kotlin
override fun onFriendListCreated(core: Core, friendList: FriendList) {
    // Only set up server friend list if a Linhome account is configured
    if (corePreferences.vcardListUrl.equals(friendList.displayName) && serverFriendList == null) {
        if (hasLinhomeAccount()) {
            Log.i("[DeviceStore] Linhome account detected, setting up server friend list")
            serverFriendList = friendList
            friendList.addListener(serverFriendListListener)
        } else {
            Log.i("[DeviceStore] No Linhome account detected, removing server friend list")
            // Remove the friend list since we don't need it
            core.removeFriendList(friendList)
        }
    }
    // ...
}
```

Now the server friend list is only set up when `hasLinhomeAccount()` returns `true`. If no Linhome account is detected, the friend list is immediately removed.

#### 3. Simplified `fetchVCards()` Function

**Before:**
```kotlin
fun fetchVCards() {
    // Extensive debug logging
    val isLinhomeAccount = !nonPushAccounts.isEmpty() && nonPushAccounts.first()?.params?.domain == LinhomeApplication.corePreferences.loginDomain
    
    if (isLinhomeAccount) {
        // Fetch vCards
    } else {
        Log.i("No vards to fetch...")
    }
}
```

**After:**
```kotlin
fun fetchVCards() {
    // Only fetch vCards if a Linhome account is configured
    if (hasLinhomeAccount()) {
        Log.i("[DeviceStore] Linhome account detected, fetching vCards")
        core.config?.setString(
            "misc",
            "contacts-vcard-list",
            "https://subscribe.linhome.org/contacts/vcard"
        )
        core.config?.sync()
        core.stop()
        core.start()
    } else {
        Log.i("[DeviceStore] No Linhome account detected, skipping vCard fetch")
    }
}
```

The function now uses the `hasLinhomeAccount()` helper and has cleaner, more maintainable code.

## Impact

- **No more spurious error dialogs** - Users without Linhome accounts won't see the "Error fetching remotely configured devices" message
- **Cleaner initialization** - The server friend list is only created when needed
- **Better resource usage** - Unnecessary sync attempts are avoided
- **Maintained functionality** - Users with Linhome accounts continue to have remote device synchronization working

## Testing

To verify the fix:

1. **Without Linhome account:**
   - Install the app with an account on a different domain (e.g., `core.homelabs.ro`)
   - Launch the app
   - Verify no error dialog appears
   - Check logs for: `No Linhome account detected, removing server friend list`

2. **With Linhome account:**
   - Configure a Linhome account (domain: `sip.linhome.org`)
   - Launch the app
   - Verify remote device synchronization works
   - Check logs for: `Linhome account detected, setting up server friend list`

## Files Modified

- [`app/src/main/java/org/linhome/store/DeviceStore.kt`](app/src/main/java/org/linhome/store/DeviceStore.kt)
