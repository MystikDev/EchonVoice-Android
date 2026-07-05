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

### Cutting a release (tag-and-forget)
1. Bump `versionCode` + `versionName` in `app/build.gradle.kts`; optionally edit the
   `notes` in `distribution/latest.json`. Commit.
2. `git tag vX.Y.Z && git push origin main --tags`.
3. The `Release APK` GitHub Action (`.github/workflows/release.yml`) builds the signed
   APK, publishes it as `echon-release.apk`, and **derives `version_code`/`version_name`/
   `sha256` from the built APK and commits them into `latest.json`** — so the manifest
   can't drift from the artifact. Existing installs detect the new `versionCode`, verify
   the hash, and update.

The Action is **active**. Its four signing secrets are configured in the repo
(Settings → Secrets → Actions): `ECHON_KEYSTORE_BASE64` (`base64 -i app/echon-release.jks`),
`ECHON_KEYSTORE_PASSWORD`, `ECHON_KEY_ALIAS`, `ECHON_KEY_PASSWORD`. Rotate these if the
release key ever changes.

> Manual fallback (no CI) still works: build the signed APK, `shasum -a 256` it into
> `latest.json`, commit, then `gh release create vX.Y.Z echon-release.apk`.

### Integrity
The APK and manifest are fetched over standard HTTPS (GitHub host), not the
echon-voice.com pinned client. **Two** controls protect the update:
1. **OS signature check** — an update must be signed with the **same release key**
   as the installed app, so a differently-signed APK is rejected at install.
2. **Manifest SHA-256** — `latest.json` carries the `sha256` of the release APK.
   The updater always downloads from the fixed compile-time URL (never a
   manifest-supplied one) and verifies the bytes against `sha256` before install,
   so even a same-key but tampered/rolled-back artifact is caught. If `sha256` is
   absent (older manifest), the app falls back to the OS check alone.

The app/API traffic stays cert-pinned separately.

### Website download page
`download.html` is a drop-in; the web team hosts it at `/download` and the button
points at the GitHub `releases/latest/download/echon-release.apk` URL.

## Update manifest contract (`latest.json`)

```json
{
  "version_code": 4,                // integer; compared against the installed versionCode
  "version_name": "2.0.3",          // display string
  "apk_url": "https://github.com/<owner>/<repo>/releases/latest/download/echon-release.apk",
  "min_supported_version_code": 1,  // installs older than this are forced to update
  "mandatory": false,               // if true, the update is treated as required
  "notes": "What's new...",
  "sha256": "<lowercase hex SHA-256 of echon-release.apk>"  // optional but recommended
}
```
Keys are **snake_case** (matching the app's JSON decoder). The release asset must be
named exactly **`echon-release.apk`** so `releases/latest/download/echon-release.apk` resolves.
The `apk_url` field is informational only — the app downloads from the compile-time
constant, not this value — so it can't be used to redirect the download.

Release flow each time we ship:
1. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts`, build the signed APK.
2. Upload the APK to the stable `apkUrl` (asset name `echon-release.apk`).
3. Compute its hash — `shasum -a 256 echon-release.apk` (or `sha256sum`) — and set
   `sha256` in `latest.json` **to that exact APK's hash**.
4. Update `latest.json` (`versionCode`, `versionName`, `notes`, `sha256`), commit.
   Existing installs detect the bump, verify the hash, and prompt to update.

> The GitHub Action automates steps 3–4: it computes the APK SHA-256 after building
> and commits the updated `latest.json` to `main`, so the hash always matches the
> published artifact.

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
