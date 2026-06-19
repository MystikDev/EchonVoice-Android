# Echon Android — direct download (website distribution)

This directory holds everything the **web team** needs to host the Android app
directly from echon-voice.com, plus the in-app updater's contract. No Google Play
involved; the app is signed with the Echon developer key (see "Signing" below).

## How distribution works (GitHub Releases)

The installer is a **GitHub Release asset** — not committed into the repo (a 44 MB
binary would bloat history). The website link and the in-app updater both pull from
GitHub, so it's transparent to users.

| Artifact | URL | Who serves it |
|----------|-----|---------------|
| Signed APK | `https://github.com/<owner>/<repo>/releases/latest/download/echon-release.apk` | GitHub Releases |
| Update manifest | `https://raw.githubusercontent.com/<owner>/<repo>/main/distribution/latest.json` | GitHub (raw, committed) |
| Download page | `https://echon-voice.com/download` → links to the APK URL above | web team hosts `download.html` |

The repo slug is set in two places (update both if it changes): `distribution/latest.json`
+ `download.html`, and `UpdateConfig` in `core/update/ReleaseManifestSource.kt`.

### Cutting a release (one push)
1. Bump `versionCode` + `versionName` in `app/build.gradle.kts` and `distribution/latest.json`
   (set `notes`), commit.
2. `git tag vX.Y.Z && git push --tags`.
3. The `Release APK` GitHub Action builds the signed APK and attaches `echon-release.apk`
   to the release. Existing installs detect the new `versionCode` and prompt to update.

> **Enable the Action first:** it ships here as `distribution/github-release-workflow.yml`
> (pushing into `.github/workflows/` needs the `workflow` token scope). To activate it,
> move it to `.github/workflows/release.yml` and push after
> `gh auth refresh -h github.com -s workflow` — or just add it through the GitHub web UI.
> Until then, releases can be cut manually: `gh release create vX.Y.Z echon-release.apk`.

**CI secrets required** (Settings → Secrets → Actions): `ECHON_KEYSTORE_BASE64`
(`base64 -i app/echon-release.jks`), `ECHON_KEYSTORE_PASSWORD`, `ECHON_KEY_ALIAS`,
`ECHON_KEY_PASSWORD`.

### Integrity
The APK and manifest are fetched over standard HTTPS (GitHub host), not the
echon-voice.com pinned client. Integrity is guaranteed by Android's package
verifier: an update must be signed with the **same release key** as the installed
app, so a tampered APK is rejected at install. The app/API traffic stays cert-pinned.

### Website download page
`download.html` is a drop-in; the web team hosts it at `/download` and the button
points at the GitHub `releases/latest/download/echon-release.apk` URL.

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
