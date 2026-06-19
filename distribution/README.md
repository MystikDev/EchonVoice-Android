# Echon Android — direct download (website distribution)

This directory holds everything the **web team** needs to host the Android app
directly from echon-voice.com, plus the in-app updater's contract. No Google Play
involved; the app is signed with the Echon developer key (see "Signing" below).

## What to host

| File | Suggested URL | Notes |
|------|---------------|-------|
| Signed APK | `https://echon-voice.com/app/echon-latest.apk` | The build artifact (`~/.cache/echon-android-build/app/outputs/apk/release/app-release.apk` — build output is relocated out of the iCloud-synced repo). Keep this URL stable; overwrite on each release. |
| Update manifest | `https://echon-voice.com/app/latest.json` | See `latest.json`. The app polls this to detect updates. |
| Download page | `https://echon-voice.com/download` | See `download.html` (drop-in, restyle to taste). |

### Critical server config
- Serve the APK with `Content-Type: application/vnd.android.package-archive`.
- Serve `latest.json` with `Content-Type: application/json` and **no aggressive caching**
  (e.g. `Cache-Control: max-age=60`) so updates are seen promptly.
- HTTPS only. The app **pins** echon-voice.com to the ISRG roots, so the APK and
  manifest fetches go over the pinned client — keep the cert chain under Let's Encrypt
  (ISRG X1/X2), same as the API.

## Update manifest contract (`latest.json`)

```json
{
  "versionCode": 1,             // integer; compared against the installed versionCode
  "versionName": "2.0.0",       // display string
  "apkUrl": "https://echon-voice.com/app/echon-latest.apk",
  "minSupportedVersionCode": 1, // installs older than this are forced to update
  "mandatory": false,           // if true, the app blocks until updated
  "notes": "What's new..."      // shown in the update prompt
}
```

Release flow each time we ship:
1. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts`, build the signed APK.
2. Upload the APK to the stable `apkUrl`.
3. Update `latest.json` (`versionCode`, `versionName`, `notes`).
   Existing installs detect the bump and prompt to update.

## Signing (important)

The app is signed with a **release keystore we control** (`app/echon-release.jks`,
credentials in `keystore.properties` — both gitignored, NOT in the repo).
- Android requires every update to be signed with the **same key** as the installed
  app. **Back up the keystore + passwords** (password manager / secure vault). Losing
  the key means users must uninstall + reinstall to take updates.
- SHA-256 of the current signing cert is recorded with the build; verify any APK with
  `apksigner verify --print-certs app-release.apk`.

## Android developer verification (2026)

Direct download still works, but to install on **certified** devices in some regions
(from Sept 30, 2026, expanding through 2027) the **developer** must be verified via the
free **Android Developer Console** (identity only — no app/content review, unlike Play).
Plan to complete that one-time registration. `adb` and the "advanced" sideload flow
remain exempt for unregistered apps.

## User install experience
Users tap Download → open the APK → Android prompts to allow installs from the browser
(per-app since Android 8) → Install. After the first install, the in-app updater handles
subsequent versions, so users don't have to revisit the site.
