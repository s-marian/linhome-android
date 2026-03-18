# Fix Server Friend List Creation - COMPLETED

## Status: ✅ Completed

## Summary

Fixed the issue where the error message "Error fetching remotely configured devices" was appearing in the Android UI even when no Linhome account was configured.

## Changes

### Code Changes

**File:** [`app/src/main/java/org/linhome/store/DeviceStore.kt`](app/src/main/java/org/linhome/store/DeviceStore.kt)

1. **Added `hasLinhomeAccount()` helper function** - Checks if a Linhome account is configured by verifying if any non-push-gateway account's domain matches `loginDomain`

2. **Modified `onFriendListCreated` callback** - Now only sets up the server friend list when `hasLinhomeAccount()` returns `true`. If no Linhome account is detected, the friend list is removed immediately.

3. **Simplified `fetchVCards()` function** - Now uses `hasLinhomeAccount()` to determine whether to fetch vCards from the remote server.

### Documentation

**File:** [`plans/fix-server-friend-list-creation.md`](plans/fix-server-friend-list-creation.md)

Created comprehensive documentation including:
- Problem description
- Root cause analysis
- Solution details
- Code changes (before/after)
- Impact assessment
- Testing instructions

## Verification

The fix has been verified to:
- Prevent the error dialog from appearing when no Linhome account is configured
- Maintain remote device synchronization functionality for users with Linhome accounts
- Clean up unnecessary server friend list creation

## Related Issues

- Error message: "Error fetching remotely configured devices"
- String resource: `vcard_sync_failed` in [`linhome-shared-themes/texts.xml`](linhome-shared-themes/texts.xml:992)
