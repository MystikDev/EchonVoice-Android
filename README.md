# Echon — Android

Native Android client for [Echon](https://echon-voice.com), mirroring the native
SwiftUI iOS app. Kotlin + Jetpack Compose, talking to the same `/v1` REST + WebSocket
+ LiveKit backend (no backend changes — a second client on the same protocol).

## Stack
- Kotlin + Jetpack Compose (Material3, dark-only)
- OkHttp + Retrofit + kotlinx.serialization
- OkHttp `WebSocket` for realtime; OkHttp `CertificatePinner` (ISRG Root X1/X2) for pinning
- Hilt for DI
- Coil (images), LiveKit Android SDK (voice)
- Atomic AES-256-GCM session records backed by Android Keystore; verified migration from legacy encrypted preferences

## Architecture
Single `:app` module, package-by-feature under `com.echon.voice`:
- `core/network` — pinned client, Retrofit API, 401-refresh authenticator, session store
- `core/realtime` — WebSocket client, tolerant event model, realtime store
- `core/storage` — encrypted token store
- `core/designsystem` — Compose theme + shared components
- `model` — `@Serializable` API models
- `feature/*` — auth, moderation, servers, chat, dms, voice, friends, profiles, members, settings, invites

## Build
Requires JDK 17 and the Android SDK (platform `android-37.0`, build-tools 36.0.0).
`local.properties` points `sdk.dir` at the SDK. The app targets Android 16 (API 36)
and continues to support Android 7.0 (API 24) and later.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew :app:assembleDirectRelease :app:assemblePlayRelease
./gradlew -Pechon.disableFirebase=true :app:testDirectDebugUnitTest :app:testPlayDebugUnitTest
./gradlew -Pechon.disableFirebase=true :app:lintDirectDebug :app:lintPlayDebug
./gradlew -Pechon.disableFirebase=true :app:connectedDirectDebugAndroidTest
```

The live login check in `TlsPinningTest` runs only when `ECHON_TEST_EMAIL` /
`ECHON_TEST_PASSWORD` are exported (test-account creds are never committed).

## Email two-step sign-in

`POST /v1/auth/login` may return either the normal token response or
`{"two_factor_required":true,"method":"email","challenge_id":"…"}`.
The latter opens the verification screen without adopting a session or fetching
`/v1/me`. The app submits `{challenge_id, code}` to
`POST /v1/auth/login/verify`, then adopts the returned session, including the
refresh token from its HttpOnly cookie. Wrong or expired codes remain on the
verification screen with a retry message. Resending repeats login with the
original credentials and replaces the pending challenge with the new one.
Passwords, codes, and challenges are held only in memory and cleared when
sign-in succeeds or the user returns to the credentials screen. The email field
remains available for convenience.

`LoginSessionTest` covers both response shapes, verification request encoding,
malformed responses, rejected codes, and cookie persistence. `LoginViewModelTest`
covers retry/resend, duplicate submission guards, network failures, and reset.

## Security notes
- TLS pinning fails closed; pins the long-lived ISRG roots, not rotating intermediates.
- No payments/donate UI in the mobile client (store-policy parity with iOS).
- Moderation (report/block/blocked-list/instant content removal/ToS gate/account
  deletion) is built early — see the moderation feature package.

## September 2026 review

[Security and streaming review](SECURITY_REVIEW.md) documents the 2.0.24 changes,
validation evidence, Android 17 migration considerations, and the remaining live
audio/device acceptance checks. Debug verification can use
`-Pechon.disableFirebase=true` when the local Firebase configuration has only a
release package client; normal release builds keep Firebase enabled.

[Foundation upgrade](FOUNDATION_UPGRADE.md) records the 2.0.26 toolchain migration,
required release checks, and device compatibility.
[Audio and storage upgrade](AUDIO_STORAGE_UPGRADE.md) records the 2.0.27 LiveKit
update, output controls, encrypted-session migration, and physical acceptance checks.
