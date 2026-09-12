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
- Audio instrumentation selects available emulated outputs, returns to automatic,
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
- CI device matrix, dependency scan, signed release, and public-artifact
  verification completed successfully; evidence is recorded below.

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

## Release-check infrastructure

The first complete audio/storage candidate passed all six CI configurations.
On the final auth candidate, all 36 app instrumentation executions also passed,
but the API 24 startup check stalled during ADB installation and the API 35
startup check did not observe the login screen after an incremental install;
that emulator also reported a graphics-buffer error. No app crash was captured.
The stalled run was cancelled to retrieve its logs. These observations do not
establish a production app defect or prove one specific emulator cause.

The startup gate now installs the complete APK with `--no-incremental`, bounds
ADB operations, removes stale UI dumps, and prints UI/activity/crash diagnostics
on failure. It still requires both a live process and the actual login screen;
no app assertion or release gate was removed. The updated gate passes locally
on the signed release. The final preflight and tag pipeline both passed before
publication.


## Published release verification — September 12, 2026

[2.0.27 (28)](https://github.com/MystikDev/EchonVoice-Android/releases/tag/v2.0.27)
is published through the website's existing stable Android download link.
The source tag points to `d43d745a38cbf2044d8cd9e38f3ff9da1e38f916`.

- [Final preflight](https://github.com/MystikDev/EchonVoice-Android/actions/runs/34707112969)
  and [security scan](https://github.com/MystikDev/EchonVoice-Android/actions/runs/34707112960)
  passed. The preflight's first Android 7 attempt timed out while installing the
  debug APK, before any tests ran. That job passed on a fresh-runner rerun;
  the other six jobs passed their first attempt.
- [Tagged release pipeline](https://github.com/MystikDev/EchonVoice-Android/actions/runs/34708513305)
  passed on its first attempt: both variants' unit/lint/build checks, all 36
  instrumentation executions across six device configurations, all six minified
  startup checks, and the 198-component dependency scan. Firebase was enabled
  in the signed release.
- Downloaded the APK from the website's actual stable GitHub link. Verified
  package/version, update-manifest SHA-256, GitHub asset digest, signing-certificate
  continuity, ZIP 16 KiB alignment, every packaged 64-bit native library's load
  alignment, and GitHub build provenance bound to the exact tag/source/workflow.
- The public APK installed in place and reached the actual login screen on the
  dedicated Android 15 ARM64 emulator. Separately, the locally signed build
  upgraded a signed-out public 2.0.26 installation on Android 16 with 16 KiB pages,
  without clearing data or changing its original install time.
- Public APK SHA-256:
  `be753a693c858b44efc09a6ed54ef230a1c4da7222f814421b7272c7cfce5e66`.
- Machine-readable evidence:
  [distribution/verification-2.0.27.json](distribution/verification-2.0.27.json).

These checks do not replace the physical, authenticated-upgrade and two-device
listening acceptance described above.
