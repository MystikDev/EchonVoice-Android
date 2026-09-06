# Echon Android native security and streaming review

Date: September 6, 2026. Reviewed project: `echon-android-native`.
Review build: **2.0.24 (25)**. The legacy `echon-android` WebView project was excluded after the owner confirmed the native app as the intended target.

The review found and fixed client-side credential boundary, session cleanup, updater, and media lifecycle weaknesses. Full-screen stream viewing now adapts to the available portrait/landscape window and supports fit/fill, pinch zoom, panning, and zoom buttons. Several plausible contributors to audio interruptions were fixed; **the reported live screen-share audio dropout has not been reproduced or confirmed resolved with two devices**.

This was a source review with regression tests, Android builds/lint, a dependency advisory query, and emulator checks. It was not a backend penetration test, a source audit of every dependency, or a certification that the application is free of vulnerabilities. No production messages were sent or accounts changed during the review. Publication is a separate, owner-authorized release step.

## Findings and changes

| Priority | Finding / trigger | Remediation |
| --- | --- | --- |
| High | API credentials were allowed on every subdomain, without an explicit scheme/port check. A compromised sibling host could receive the bearer. | Credentials and refresh recovery now require exactly `https://echon-voice.com:443`, without URL userinfo. TLS pins for subdomains remain separate from credential authorization. |
| High | OkHttp application interceptors only inspect the initial request. Refresh redirects could forward the custom `X-Refresh-Token` header; cookie capture checked the initial URL rather than the response URL. | API and refresh clients reject redirects. A network interceptor strips sensitive headers on every foreign-origin media hop. Refresh cookies require a successful final response from the exact login/register/refresh endpoint. |
| High | A refresh completing after logout/account switch could repopulate credentials or clear the new account. | Requests carry a session generation. Cookie capture, refresh adoption, and failed-refresh clearing reject stale generations. Login/register opt out of bearer authentication and refresh retries. |
| High | Leave waited for a REST response before stopping capture; a pending join could outlive Leave. Sign-out did not stop the independent LiveKit session. | Local teardown precedes best-effort server leave. Join/control jobs are cancelled as a group; LiveKit rooms are released. Sign-out stops calls and realtime before clearing credentials and private account state. Foreground-service callbacks are scoped to the call generation. |
| Medium | Singleton chat, DM, friends, block, and server data survived sign-out. Image caches and notifications could also retain private content. | Sign-out empties account registries, pending links, image caches, and notifications; delayed list loads/rollback paths are fenced by a generation. New Coil disk caching is disabled. Push data messages are ignored without a session, and notification visibility is private on the lock screen. |
| Medium | A WebSocket handshake was not retained until `onOpen`; cancellation could miss it and late callbacks could outlive sign-out. | The socket is retained immediately, cancelled in `finally`, and callbacks check connection-job lifetime. Realtime consumers and reconciliation jobs are cancelled and joined before clearing account data. |
| Medium | Updater hash validation was optional; downloads used one shared filename without a size limit; install did not explicitly validate package/version against the manifest. | Require a 64-digit SHA-256, use the fixed compiled HTTPS source, limit APKs to 256 MiB and manifests to 64 KiB, use unique private temporary files, clean failures, and validate package plus strictly newer/exact manifest version. PackageInstaller is constrained to the installed package, and Android verifies its signing identity/rotation lineage. |
| Medium | WebSocket tickets in URL query strings could appear in debug HTTP logs despite header redaction. | HTTP request logging is disabled in both build types. |
| Low | Server/channel names were interpolated into navigation query parameters. | Names, IDs, and kinds are URI encoded before navigation. |

A downloaded hash is not an independent signature if the manifest source is compromised. The installed application's signing identity remains the authoritative update trust boundary. Missing hashes now deliberately prevent updates; the release workflow already generates hashes.

Existing protections retained: encrypted session storage, disabled backup and token transfer exclusions, system CA trust with ISRG pins, no cleartext networking, non-exported services/providers, limited FileProvider paths, screenshot protection, release shrinking, and separation of Play from self-update permissions. Exported launcher extras already had UUID validation. The Play flavor remains free of the direct updater.

## Requested streaming changes

- **Orientation and screen size:** a full-window viewer follows Android's current window/orientation and safe drawing insets. It does not force landscape or override the user's rotation lock. Rotation keeps the singleton call; the UI restores the selected stream. The roster retains compact preview tiles.
- **Fit:** default `SCALE_ASPECT_FIT` shows the complete image with bars when aspect ratios differ. Fill uses `SCALE_ASPECT_FILL` and intentionally crops to fill the available viewer area.
- **Zoom:** pinch or use Zoom in/out; pan is bounded; zoom ranges from 1× to 4×. Reset restores the entire image. Viewport changes reset the transform so rotation cannot leave the video offscreen.
- **Renderer lifetime:** use LiveKit's TextureView renderer for Compose transforms/clipping, detach tracks and release renderers on disposal, and avoid a duplicate preview renderer for the expanded stream. Keep the display awake while a video view is attached.
- **Audio/call reliability:** establish the microphone foreground service before slow network connection work; keep one call-owned audio session across rotation and reconnection; expose reconnect/subscription errors; request optional Bluetooth permission; direct volume buttons to the call stream. Adaptive video subscriptions reduce unnecessary decoding/network load. Mic/camera actions no longer wait behind slow occupancy reporting.
- **Privacy during backgrounding:** the application has a microphone foreground service, not a background camera service. Camera capture stops when the activity actually backgrounds; rotation is exempt. Call audio continues under the microphone service. The ongoing notification now has a Leave call action.

The application does not process remote screen-share audio through a custom noise gate. LiveKit handles received audio and routing. Changing microphone echo cancellation/noise suppression would not establish a fix for a remote publisher's clipped audio. Sender capture settings, Bluetooth behavior, packet loss, and server/TURN configuration remain possible causes requiring live evidence.

## Android platform assessment

The relevant current platform release is Android 17 / API 37. The project still compiles and targets API 35; this change does **not** claim an API 37 migration or Android 17 device certification. The available local emulator and SDK are Android 15 / API 35.

Applied compatible improvements: responsive full-screen viewing, safe insets, managed foreground audio startup, optional Bluetooth permission, bounded resource disposal, and an actionable ongoing call notification. These address the same areas emphasized by current platform guidance:

- [Android 17 background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio): audio interactions require a visible activity or an eligible foreground service. The call service is established before starting media.
- [Android 17 behavior changes](https://developer.android.com/about/versions/17/behavior-changes-17): adaptive layouts and large-screen rotation/resizing requirements. The viewer uses actual window dimensions and no orientation restriction.
- [16 KiB page-size support](https://developer.android.com/guide/practices/page-sizes): the existing AGP 8.7.3 packages the APK correctly; the inspected arm64-v8a and x86_64 native libraries have 16 KiB LOAD alignment, and APK zip alignment passes. This is a binary check, not a run on a 16 KiB device.
- [Predictive back guidance](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture): the app already uses Compose navigation; full-screen viewing uses a dismissible Compose dialog rather than adding legacy Activity back interception. Gesture animations need API 36/37 device QA.

Before raising target SDK: update AGP/Kotlin/KSP/Compose as a compatible set, install API 37 tooling, and run the full API 35–37 device matrix, including large-screen resizing, foreground audio, Bluetooth and camera permissions, navigation, notifications, and release startup. An indiscriminate dependency upgrade was avoided; the currently resolved runtime coordinates were checked for known advisories instead.

## Validation

See `security-review/validation.md` for commands, exact final results, and artifact checks. The regression tests cover hostile origins, redirects, cookie provenance, stale refreshes, account data isolation, updater limits/package/version checks, and zoom bounds.

The emulator test creates a real LiveKit room, track, and renderer with an inert test capturer. It checks viewer controls, landscape recreation, and repeated disposal without a server or account. It does **not** transmit media, render remote video frames, or measure audio continuity.

OSV query evidence is saved in `security-review/osv-results-2026-09-06.json`, with the 140 resolved release runtime dependencies in `security-review/runtime-dependencies-2026-09-06.json`. OSV returned no known advisories for those coordinates on the review date. This does not cover undisclosed issues or all vulnerabilities in native code embedded inside dependencies.

## Required live acceptance checks

Use a test account and a second device sharing its screen with continuous sound:

1. Watch for at least 15 minutes; rotate portrait/landscape repeatedly; compare audio to another viewer.
2. Open/close full screen repeatedly. Test portrait, landscape, and ultrawide senders. Verify fit shows every edge, fill crops as expected, and pinch/buttons/reset work.
3. Test speaker, wired/USB audio, Bluetooth permission denial, headset connect/disconnect, and physical volume keys.
4. Test mic mute/unmute while receiving screen audio; confirm incoming audio does not change with local mute or UI navigation.
5. Lock/unlock, background/foreground, open another audio app, receive a phone call, and perform Wi-Fi/mobile handoffs. Confirm reconnect notices reflect real state.
6. Leave while joining or during network loss; verify mic/camera indicators and call notification stop promptly. Repeat sign-out during an active call and a pending refresh.
7. Switch accounts in one process. Confirm old conversations, cached media, notifications and pending links are inaccessible. Verify attachment redirects and refresh-token rotation against the actual backend.
8. Validate signed update success, tampered digest rejection, package/version mismatch rejection, and Play's absence of install permissions. Verify Firebase pushes with the configured release build.

## Backend / release follow-up

- Server authorization, membership checks, logout revocation, FCM device-token reassignment/deregistration, and LiveKit/TURN security were outside this repository. In particular, the push payload lacks a recipient account identifier: the backend must prevent a previous account's notifications from reaching a newly signed-in account. Client session checks alone cannot prove that separation.
- Current grants are restricted to the configured Echon WSS origin. A future move to LiveKit Cloud or a separate subdomain requires an explicit signaling trust-policy update, not a wildcard credential rule.
- Schedule certificate-pin maintenance before the existing June 1, 2028 network-config expiration. No pin values or expiry were weakened.
- Release 2.0.24 is prepared for the owner-authorized GitHub release workflow. The workflow derives `distribution/latest.json` version and hash from its signed APK. The hashes in `security-review/artifacts.json` identify the locally tested APKs; a CI rebuild can have a different hash. See the GitHub release and current manifest for the distributed artifact.
