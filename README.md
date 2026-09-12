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
- EncryptedSharedPreferences for session tokens

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
required release checks, and remaining calling/storage work.
