# Android user-feedback fixes — 2.0.28 (29)

## Notifications and icon

The native app now displays a bell and a Notifications section in Settings. It
reports Android app/category permission separately from push registration. Users
can request permission, open the correct Android notification settings, retry
registration, and post a clearly labelled local test alert. Returning from system
settings refreshes permission status. A local alert does not prove server push
delivery.

Push registration now retries temporary failures, bounds Firebase-token fetches,
retries on foreground return, and exposes failures instead of silently logging
and waiting until the next sign-in. Auth/account changes cancel registration work;
no device tokens or message bodies are logged. Production Firebase configuration
and the backend `/v1/devices`/FCM delivery service are still required.

Messages, ongoing calls, and updater notifications use a transparent monochrome
bell instead of adaptive launcher artwork, preventing the solid-square small-icon
appearance. The same icon is configured as Firebase's notification fallback.

The reported phrase “blocked by browser settings” does not exist in this native
Kotlin app. It may refer to Chrome, a PWA, or the obsolete WebView wrapper; the
reporter's app/version has not been confirmed. This release cannot grant browser
permissions or prove that a separate browser installation was repaired.

## Multiline messages

Sending, editing, and retrying preserve intentional leading/trailing whitespace
and repeated blank lines. CRLF and lone CR become LF without collapsing lines.
Blank-only messages remain rejected. The composer explicitly supports multiline
input; Enter adds a line break and the Send button sends. Regression tests cover
both DM and server message request bodies and the rendered composer/message row.
Server-side formatting is outside the client checks.

## Invites

Create/use POSTs now send a JSON object, matching the established iOS contract,
instead of an empty body. The server Invite action always opens its sheet and
supports voice-only servers. Users can choose another channel, reload an empty
channel list, see permission/network errors, retry, copy with confirmation, and
open Android's share sheet.

Joining accepts codes or HTTPS Echon `/invite/{code}` and `/invites/{code}` links.
It validates and extracts the code before API use, debounces previews, clears
stale preview state, and requires a successful preview of the current code before
joining. Changing input or dismissing the sheet cancels obsolete work. Creating
and joining use separate state so an old code cannot bleed into a new sheet.

## Validation and live acceptance

Automated regression coverage includes actual Retrofit wire requests, multiline
send/edit/retry, code/link parsing, invitation debounce/reset, registration retry,
timeout/account cancellation, and Compose/device tests for consecutive Enter,
voice-only invites, notification settings, and the posted bell icon. Existing
security, storage, presence, and streaming tests remain in the release gates.

Live acceptance uses two consenting test accounts: install the official APK,
check Settings > Version, enable Messages notifications, then verify a real DM
push in the background and its conversation tap. Compare that with the local
notification test. Create/copy/share/redeem an invite on the production server,
including permission-denied and expired-invite cases. Send/edit a message with
several blank lines and inspect it from another client.

References: [Android notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission),
[Firebase Android setup](https://firebase.google.com/docs/cloud-messaging/android/get-started).

Local checks: 140 passing unit-test executions across Direct and Play, two optional
skips, zero lint errors (45/46 warnings), both minified release variants built,
and all ten tests passed on the dedicated Android 15 ARM64 emulator. An overlapping
local debug-package build failed once; rerunning after the release builds completed
passed. No assertion or release gate was removed.

Release execution and public-artifact verification are recorded below once complete.
