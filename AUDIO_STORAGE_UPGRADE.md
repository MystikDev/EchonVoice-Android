# Android audio and encrypted-session upgrade — 2.0.27 (28)

## LiveKit and call audio

LiveKit Android moves from 2.27.0 to 2.28.2, including liwebrtc 144.7559.14.
The intervening 2.28.0 release fixes a silent-microphone race between connection
and microphone publication, cancelled publication cleanup, and native resource
leaks. These upstream fixes reduce known failure paths; they do not establish
that every reported screen-share dropout is resolved.

The call screen shows the SDK-reported output and offers automatic routing,
speaker, earpiece, and available wired/Bluetooth headsets. Automatic routing
retains LiveKit's headset-first priority. Explicit selection chooses an available device. Disconnecting that device
returns to Automatic. Automatic resolves a concrete device from the SDK's ordered
list and follows hot-plug callbacks: the pinned AudioSwitch implementation's null
selection clears its current route without immediately selecting a replacement,
so the app does not rely on that API for returning to Automatic. Listeners survive room reconnection, are removed at call
teardown, and ignore queued callbacks after teardown. A focus-loss notice explains
when another app interrupts audio. Route-device logging is disabled.

Communication audio mode, echo cancellation, microphone noise suppression,
redundant audio encoding, automatic remote-track subscription, and background
call lifetime retain their existing settings. Screen-share audio remains
subscribed independently of the video renderer, rotation, zoom, or fullscreen
state. Incoming audio cannot be repaired by changing local microphone filtering.

The obsolete protobuf override is removed: LiveKit now supplies patched
protobuf-javalite 3.25.9 itself. AndroidX Security Crypto is updated to stable
1.1.0 solely to read old encrypted preferences during migration.

Sources reviewed:
- [LiveKit 2.28.2](https://github.com/livekit/client-sdk-android/releases/tag/v2.28.2)
- [LiveKit 2.28.0](https://github.com/livekit/client-sdk-android/releases/tag/v2.28.0)
- [Pinned AudioSwitch selection implementation](https://github.com/davidliu/audioswitch/blob/039a35aefab7747c557242fa216c9ea11743b604/audioswitch/src/main/java/com/twilio/audioswitch/AbstractAudioSwitch.kt)
- [LiveKit audio routing implementation](https://github.com/livekit/client-sdk-android/blob/v2.28.2/livekit-android-sdk/src/main/java/io/livekit/android/audio/AudioSwitchHandler.kt)

## Encrypted session storage

Access and refresh tokens are stored together in a bounded, versioned AES-256-GCM
record. Each write uses a fresh provider-generated 96-bit IV, a 128-bit
authentication tag, and associated data binding the envelope to the package,
filename, and format version. A non-exportable Android Keystore key encrypts the
record in credential-protected `noBackupFilesDir`. Hardware backing depends on
the device; it is not assumed. No biometric prompt is required for background
session refresh after the device's first unlock.

AtomicFile, an explicit file sync, and decrypt/read-back verification replace
separate asynchronous preference writes. SessionStore adopts cached values only
after persistence succeeds. A process-wide lock serializes store instances.
Token container string representations are redacted.

On upgrade, the old encrypted access/refresh values are read together, written
to the new record, and verified before the old preferences are deleted. The old
master key is not deleted because it may be shared. A cleanup failure is retried;
it does not invalidate a verified migration. Once a new record exists, it is
authoritative: corrupt ciphertext or a missing key never triggers fallback to
old credentials or automatic key replacement. Logout writes an encrypted empty
record so stale legacy data cannot restore a session.

Storage failures stop credential use, stop call/realtime activity, clear cached
account UI, and show recovery actions. Retry retains the stored data. Forget
saved sign-in requires an in-app confirmation and explicitly replaces the key
and saved session. Failed persistence is not reported as a completed sign-out.
The refresh-cookie response is closed on persistence failure, and that failure
is not treated as server revocation. Network timeouts, HTTP 429/5xx responses,
and malformed refresh responses also retain credentials for retry; server 401/403
rejection still clears them. The app also offers retry when startup
cannot resolve the saved session over the network.

Backup remains disabled; the new file is outside backup/transfer data. Storage
migration does not change backend token lifetime, server-side revocation,
attachment authorization, or introduce end-to-end encryption for calls.

Sources reviewed:
- [Android cryptography guidance](https://developer.android.com/privacy-and-security/cryptography)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)

## Validation

- JVM tests cover encryption round trips, fresh IVs, corruption/truncation,
  wrong keys/context, payload bounds, migration write/read failures, stale legacy
  data, logout tombstones, failed token rotation, startup recovery, stale request
  generations, and HTTP response cleanup when persistence fails.
- Instrumentation uses real Android Keystore and legacy encrypted keysets with
  isolated test aliases. It checks migration, reopening, rotation, logout,
  tampering, incomplete legacy keysets, and missing-key behavior without reading production credentials.
- Audio instrumentation selects speaker and earpiece, returns to automatic,
  and repeats three call lifetimes with the real SDK routing handler.
- Existing presence and fullscreen/rotation/renderer-disposal tests remain gates.
- Local Android 16 ARM64 emulator: verified 16,384-byte pages; all six
  instrumentation tests passed on the final implementation.
- Final two-flavor unit checks: 122 passed and two optional tests skipped. Lint:
  zero errors (42 Direct / 43 Play warnings).
- Local Android 17 ARM64 emulator: all six integration tests passed.
- Manual startup recovery on the dedicated signed-out debug app: corrupt record
  shows recovery; Retry and Cancel preserve it; confirmed Forget creates an
  encrypted empty record; restart reaches sign-in.
- Resolved runtime inventory: 198 Maven components, including LiveKit 2.28.2,
  WebRTC 144.7559.14, protobuf-javalite 3.25.9, and Security Crypto 1.1.0.
- CI device matrix, dependency scan, final signed release, and public-artifact
  results are recorded below when complete.

## Physical acceptance still required

Use two test accounts on separate physical devices. Upgrade a signed-in 2.0.26
installation and verify account continuity after restart and token refresh.
Listen to voice and desktop screen-share audio for at least ten minutes, including
quiet/loud speech, media playback, mute/unmute, fullscreen/rotation/zoom, and
background/return. Test Bluetooth/wired attach and detach, explicit routes and
Automatic, incoming phone-call interruptions, and Wi-Fi/cellular handover under
weak reception. Verify recovery after reconnect and leave/rejoin. Emulator route
selection does not certify acoustic quality, headset microphones, packet-loss
recovery, or production-server behavior. No physical acceptance has been claimed.
