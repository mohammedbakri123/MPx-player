# Samsung Secure Folder & Scoped Storage Fix

## Problem Summary

Users reported that MPx Player cannot read files when installed inside **Samsung Secure Folder** on Android 13 (Samsung Galaxy M62). The error occurs:

```
PathAccessException: Exists failed, path = '/storage/emulated/0/Movies'
(OS Error: Permission denied, errno = 13)
```

### Root Causes

1. **Android Scoped Storage (Android 10+ / API 29+)**
   - Apps can no longer freely access arbitrary directories on external storage using direct file paths (`dart:io` `Directory`/`File`)
   - `READ_EXTERNAL_STORAGE` permission only grants access through **MediaStore** or **Storage Access Framework (SAF)**
   - Direct path access like `/storage/emulated/0/Movies` fails with `Permission Denied`

2. **Samsung Secure Folder (Isolated User Profile)**
   - Creates a **separate Android user profile** (work profile)
   - Apps run under a **different Linux UID**
   - `/storage/emulated/0` points to the **Secure Folder's own isolated storage**, not the main profile's storage
   - Even with permissions, direct file path access is blocked

3. **Hardcoded Paths in Code**
   - `ReelService` hardcoded `/storage/emulated/0/Movies` for reels storage
   - `DirectoryBrowser` hardcoded `/storage/emulated/0` as the storage root
   - `PathBreadcrumb` hardcoded `/storage/emulated/0` as internal storage label

## Changes Made

### 1. New: `StoragePathService` (`lib/core/services/storage_path_service.dart`)

A centralized service for cross-profile compatible path resolution:

- **`getAppExternalDirectory()`**: Returns app-specific external storage (`/storage/emulated/[userId]/Android/data/<package>/files`)
  - Automatically resolves to the correct profile (main, Secure Folder, work profile)
  - Works without any storage permissions
  - Reliable across all Android versions

- **`getAccessibleStorageVolumes()`**: Returns all app-accessible storage directories
  - Primary external storage
  - SD cards and secondary storage

- **`getReelsDirectory()`**: Safe reels storage that works in all profiles

### 2. Fixed: `ReelService` (`lib/features/reels/services/reel_service.dart`)

**Before:**
```dart
baseDir = Directory('/storage/emulated/0/Movies');
```

**After:**
```dart
final baseDir = await StoragePathService.getAppExternalDirectory();
// Resolves to app-specific directory, works in Secure Folder
```

Reels are now stored in the app's private external directory, avoiding all permission issues.

### 3. Updated: `PermissionService` (`lib/core/services/permission_service.dart`)

- Added SDK-aware permission checking (Android 13+ uses granular media permissions)
- Added **`MANAGE_EXTERNAL_STORAGE`** support:
  - `checkManageExternalStorage()` - Check if "All files access" is granted
  - `requestManageExternalStorage()` - Request the permission (opens system settings on Android 11+)
- Added `device_info_plus` dependency for SDK version detection

### 4. Updated: `DirectoryBrowser` (`lib/features/library/data/datasources/directory_browser.dart`)

- `getStorageDirectories()` now tries multiple strategies:
  1. Legacy public path (`/storage/emulated/0`) - works on Android 9 or with MANAGE_EXTERNAL_STORAGE
  2. App-specific directories - works on all Android versions and profiles
  3. SD cards and external storage - last resort fallback

- Gracefully handles `PathAccessException` by falling back to accessible directories

### 5. Updated: `AndroidManifest.xml`

Added `MANAGE_EXTERNAL_STORAGE` permission:
```xml
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```

**Note:** Google Play requires justification for this permission if publishing on Play Store.

### 6. New: Settings UI (`lib/features/settings/presentation/screens/settings_screen.dart`)

Added **"Storage & Permissions"** section in Settings:
- Shows current "All files access" status
- Button to enable full access (opens system settings)
- Informational message about Samsung Secure Folder limitations

## How to Grant Full Access (For Users)

### Method 1: Through MPx Settings (Recommended)
1. Open MPx Player
2. Go to **Settings**
3. Expand **"Storage & Permissions"**
4. Tap **"Enable"** next to "All files access"
5. Toggle on "Allow access to manage all files"
6. Return to MPx and restart if needed

### Method 2: Through Android Settings
1. Open Android **Settings**
2. Go to **Apps** → **MPx Player**
3. Tap **Permissions**
4. Tap **Files and media**
5. Select **"Allow all the time"** or **"Allow management of all files"**

## Samsung Secure Folder Specific Behavior

### What Works:
- Reels feature (uses app-private storage)
- Videos copied/downloaded into Secure Folder
- App-specific directories

### What Doesn't Work:
- Accessing main profile files from Secure Folder (by Android design)
- File browsing main storage without MANAGE_EXTERNAL_STORAGE

### Workarounds for Secure Folder Users:
1. **Install MPx outside Secure Folder** to access all device files
2. **Copy videos into Secure Folder** using Samsung's file manager
3. **Download videos directly** into Secure Folder using MPx's downloader

## Future Improvements (Recommended)

### Option A: Use `photo_manager` package (Best for gallery apps)
- Queries MediaStore API for all videos
- Returns content URIs that work with scoped storage
- Works in Secure Folder (queries the profile's own MediaStore)

```yaml
dependencies:
  photo_manager: ^3.0.0
```

### Option B: Use Storage Access Framework (SAF)
- Let users pick directories via system picker
- Grants persistent access to selected folders
- Already have `file_picker` dependency

### Option C: Keep MANAGE_EXTERNAL_STORAGE
- Simplest for full file manager functionality
- Requires Play Store justification
- May be rejected by Play Store policy

## Testing Checklist

- [ ] Reels work on Android 13 main profile
- [ ] Reels work in Samsung Secure Folder
- [ ] File browser shows app directories when no full access
- [ ] File browser shows full storage when MANAGE_EXTERNAL_STORAGE granted
- [ ] Settings shows correct permission status
- [ ] Permission request opens system settings on Android 11+

## References

- [Android Scoped Storage Documentation](https://developer.android.com/training/data-storage#scoped-storage)
- [Samsung Secure Folder Documentation](https://www.samsung.com/uk/support/mobile-devices/what-is-the-secure-folder-and-how-do-i-use-it/)
- [MANAGE_EXTERNAL_STORAGE Permission](https://developer.android.com/training/data-storage/manage-all-files)
- [permission_handler Package](https://pub.dev/packages/permission_handler)
