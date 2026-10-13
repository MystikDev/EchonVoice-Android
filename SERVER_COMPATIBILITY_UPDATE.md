# Android 2.0.30: server compatibility, signup, and screen sharing

Source reviewed October 13, 2026: main client `pkapral2-droid/echon-rs`, commit
`e4d990469d7a39dd75353c74209802c8c178bd14`. Its LiveKit server update is
[`8b0e87a`](https://github.com/pkapral2-droid/echon-rs/commit/8b0e87a26a9395b2fd89a419f2fdd5cd3f583eea),
1.13.5 to 1.13.9. This Android change updates its separate SDK from 2.28.2 to
[2.29.0](https://github.com/livekit/client-sdk-android/releases/tag/v2.29.0).
The main server commit flags desktop H.264 sharing for acceptance; an SDK upgrade
alone cannot establish desktop-to-phone media interoperability.

## Requested contract changes

- `channel-created` and `channel-updated` now upsert full channel data, including
  previously unknown channels whose access was restored. `channel-deleted` removes
  the channel, message cache, typing/unread state, room subscription, voice roster,
  and active call for that channel. Open chat/voice screens exit, and a stale
  voice route cannot rejoin or request microphone access after revocation. Delayed REST results and
  late queued messages cannot repopulate revoked content. Ready and subsequent
  selection reconcile channel access through REST, including changes missed offline.
  Role and member-role events refresh the affected server too; explicit revocations
  purge immediately even if permission state changes again before UI collection.
- Filtered voice snapshots replace occupancy rather than treating hidden rooms as
  empty visible rooms. No native invite-event inventory exists to reconcile; create
  and redeem remain authenticated REST actions. Normal channel behavior is retained.
- The chat joins its channel room before emitting typing; revoked rooms suppress
  local typing too.
- WebSocket close 4001, an unauthorized ticket API request/confirmed revoked handshake, and the server's
  `403 account_disabled` response clear the matching
  session and stop the reconnect loop. The existing auth observer ends audio,
  purges account data, and returns to login. Generation checks protect a later
  login from an older socket. An expired one-time ticket retries with a fresh ticket
  without clearing a healthy session. Password reset/revocation follows this same path.
- Message uploads already preserve the `/v1/uploads` URL. Rejected attachments now
  explain that the user must discard the failed message and upload again. Server
  metadata remains authoritative.
- Profile avatar updates accept only a bounded Echon `/uploads/` URL, and an empty
  value clears the avatar. Server icon/banner/name-color editing is not offered
  by this native client, so it does not construct those update requests.
- Native image requests use origin-restricted bearer authentication, which the
  new `/files` handler still accepts and now revocation-checks. Android does not
  retain or depend on the old media-token cookie/query format or the October 16
  transition. No token is added to external image URLs.
- This client has no native forgot-password request flow. The backend's generic
  rate-limit response needs no Android contract change.

## Signup captcha

Signup fetches unauthenticated `/v1/auth/captcha`. A null site key skips the widget;
configuration errors show retry rather than guessing enforcement state. When a
key is configured, optional checks send a token if available; required checks block
submission until a fresh token is available. A `captcha_required` response refreshes
configuration in case enforcement changed while the form was open. The
controlled HTML loads with `https://echon-voice.com/` as its base and the `signup`
action. A returned token is included as `captcha_token`; login is unchanged.
Every completed submit attempt clears the single-use token and recreates the
widget. Revision checks reject callbacks from disposed widgets; expiry clears
readiness. Captcha-specific failures show actionable messages.

The WebView has no file/content access or app API credentials. Its message bridge
is restricted to the Echon origin and top frame. Top-level navigation is blocked;
only Cloudflare HTTPS frames and its required blank/srcdoc frames are allowed.
A CSP limits external script/frame/connect sources to Cloudflare. Older WebViews
without the restricted bridge fail closed with a retry/update message.

The live public configuration was fetched and its real challenge loaded on the
Android 15 emulator. Cloudflare returned error 300010; the app displayed retry.
This does not establish a production signup success. Physical signup acceptance
must precede making captcha mandatory for all Android versions. No enforcement
setting or production account was changed.

References: [Cloudflare mobile integration](https://developers.cloudflare.com/turnstile/get-started/mobile-implementation/),
[official test fixtures](https://developers.cloudflare.com/turnstile/troubleshooting/testing/),
[Android bridge guidance](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges).

## Screen sharing and icon

Screen-share publications are discoverable before subscription. Shares start
closed: Watch subscribes to the chosen video and its share-audio publications;
Stop watching unsubscribes them. Share audio has an independent mute and is silenced
immediately without touching ordinary voice microphone tracks. Both explicit
screen-audio sources and the desktop `:screen` participant convention are handled.
Preferences clear at call teardown and when a publication ends. Cameras and voice
remain subscribed normally.

Fullscreen video uses the whole current window, with controls overlaid rather
than reducing video height. Tap the video to hide/show controls. Closed shares use
compact cards without reserving video-stage space. Rotation rebuilds the video
renderer and resets transforms for the new viewport; fit/fill scaling applies in
both orientations. Zoom and Reset remain available. The launcher uses the current
main desktop client's existing Android icon exports, with adaptive safe-area
padding and a matching dark background; notification alerts retain their bell.

## Stream Deck item 9: separate desktop/plugin work remains

There is no Stream Deck bridge in Android. Main client
`apps/web/src/desktop/streamDeck` connects to `ws://127.0.0.1:45210` and sends the
raw pairing token in its v1 `hello`, confirming the reported exposure. The bundled
`apps/streamdeck-plugin` is **com.echon.streamdeck**, using a Windows named pipe;
it is not the requested **com.echon.voice** plugin. Its source location is still
needed to implement and test both ends of the fix. No unilateral desktop wire
change or desktop deployment is included in this Android release.

A coordinated v2 should never transmit the shared token. Echon first sends a fresh
32-byte client nonce. The plugin returns its own 32-byte nonce and a server-role
HMAC-SHA256 over both nonces. Echon verifies that proof before sending its client-role
HMAC, and the plugin verifies the client proof before accepting state or commands.
Use the decoded 32-byte pairing key; domain-separate the proofs, e.g.
`echon.streamdeck.v2/server\0` and `echon.streamdeck.v2/client\0` followed by the
fixed-length client/server nonce bytes. Both sides need cryptographic randomness,
constant-time proof comparison, a bounded handshake timeout, rejection of replayed
or out-of-order proofs, and no automatic v1 fallback. A challenge alone does not
prove the listener knows the token. Ship both updated peers together, testing a
malicious listener, wrong key, replay, timeout, and ordinary commands/reconnects.

## Validation and live acceptance

Regression coverage includes channel upsert/removal and stale REST fencing,
cache revocation, terminal session-close/handshake behavior with no reconnect,
signup wire format, captcha reset/config failure, safe profile URLs, attachment
recovery, screen/voice subscription policy, WebView origin restrictions, and
screen-share controls/fullscreen rotation. Release gates retain both build flavors,
security scanning, and the six existing emulator configurations.

Live acceptance uses consenting accounts to verify a real signup/retry with the
production key, session revocation while a call is active, private-channel access
loss/restoration and offline reconnect, desktop H.264 sharing to Android,
independent share-audio mute while speech remains audible, portrait/landscape
fullscreen, and the launcher icon. Automated policy/layout checks do not establish
acoustic behavior, codec interoperability, or production captcha acceptance.

Local checks: 170 passing unit-test executions across Direct and Play, two optional
skips, zero lint errors, and both minified variants built during development.
The Android 15 ARM64 suite passed all 13 tests; the explicit Cloudflare always-pass
fixture diagnostic returned a dummy token without making a registration request.
Production signup acceptance remains separate from this fixture result.


## Published 2.0.30 verification

Published Android **2.0.30 (31)** from `07fb8ba00b9fc5c0a5dc5f9e82a357cda27523c0`. The previous
v2.0.29 release run was cancelled before publication; its tag remains unchanged.
Version 2.0.30 includes the final fullscreen overlay and compact closed-share fixes.

- [Main validation](https://github.com/MystikDev/EchonVoice-Android/actions/runs/38455284649): all seven jobs passed on the release source.
- [Main security scan](https://github.com/MystikDev/EchonVoice-Android/actions/runs/38455285014): passed.
- [Tagged release](https://github.com/MystikDev/EchonVoice-Android/actions/runs/38455287388): all nine required jobs passed; final workflow attempt 2.
- 170 passing local unit-test executions, two optional skips, zero lint errors, and both minified variants built. Tagged validation passed 78 instrumentation executions across six emulator configurations and six minified startup checks. The dependency scan reported 201 packages and no issues.

The tagged release's first API 24 attempt failed to install the debug APK
(`Failed to install-write all apks`) before tests ran. An unchanged fresh-runner
retry passed the required tests and release startup. Earlier validation on
`0ba93e3f8a5146ed02086dffca1c8e825280e4ec` passed all 13 API 24 tests, then timed out
installing the minified APK after 120 seconds; that unchanged retry also passed.
Neither installation failure's underlying cause was established. No gate or
assertion was removed, and no timeout was extended to obtain these results.

Downloaded the public APK through the stable link used by
[the website](https://echon-voice.com/android.html). Verified package/version, manifest and GitHub
asset SHA-256 agreement, release signature continuity, GitHub provenance tied to
the exact tag/source/workflow, 16 KB ZIP alignment and every packaged 64-bit ELF
LOAD alignment, launcher resources, and the notification bell fallback. Firebase
was enabled in the signed release build.

APK SHA-256: `5fad128fdfd321cfcf10d12b3b782c7465b2656cb7971f2f80299ce0a26e8826`.
Signing certificate SHA-256: `bdfbbbf992e30b1b5bf07fdcedc09133c1db684623aab457c2ef95ecc5e6b836`.

The downloaded public APK installed over the official 2.0.28 (29) on the Android
15 ARM64 emulator, preserved the original installation time, and reached the
sign-in screen. This was a signed-out in-place upgrade, not an authenticated
account migration or a physical-device test. Machine-readable evidence is in
`distribution/verification-2.0.30.json`.

Real-phone acceptance remains required for production signup, private-channel
permission loss/restoration, session revocation during a call, desktop H.264
sharing, independent share-audio mute while speech remains audible, Bluetooth,
weak networks, rotation, and notifications/invites. The production captcha loaded
in the emulator but Cloudflare returned 300010; native retry appeared. The public
always-pass fixture delivered a dummy token without account creation. Mandatory
captcha enforcement was not enabled and should wait for successful production
signup on a real phone and user adoption.

The separate com.echon.voice Stream Deck fix still requires the matching plugin
source and a coordinated update of both desktop/plugin peers. The user confirmed
the source is on the PC and deferred that work to a later PC-side session.
