# Server population and presence — 2.0.25 (26)

## Findings

The Android member screen loaded names once and did not observe or display presence.
Friends/DM presence depended entirely on WebSocket changes after connection: there
was no initial REST snapshot or recovery after missed events. Do Not Disturb and
unknown values appeared offline. The socket also lacked the JSON heartbeat used by
the current web client, allowing idle/stalled connections to leave stale presence.

The public web client served at `https://echon-voice.com/assets/index-BiItd8gz.js`
(confirmed September 6, 2026) establishes the existing contract:

- `GET /v1/servers/{id}/presence` and `GET /v1/me/social-presence` return `presences` maps.
- `presence-changed` / `presence.update` carry `user_id` and `status`.
- Supported public statuses are `online`, `idle`, `dnd`, and `offline`.
- A successful sparse snapshot treats absent users as offline.
- JSON `{"type":"ping"}` is sent every 15 seconds; no incoming frame for 35 seconds triggers reconnect.

## Changes

- Load social and visible-server snapshots after the socket's `ready` event; refresh
  every 60 seconds and reload on reconnect. Member membership refreshes every 30
  seconds while visible. Opening/returning to the member screen starts fresh polling.
- Apply events immediately, protect newer events from in-flight snapshots using
  revisions, and reject results from disconnected/account-cleared generations.
- Clear stale presence on disconnect or failed snapshots. Display unavailable status
  separately from confirmed offline status; malformed events never force offline.
- Group members into Online, Offline, and Status unavailable with counts and stable
  name ordering. Online includes Idle and Do Not Disturb, with each user's actual
  status shown beside their name. Duplicate IDs do not inflate population counts.
- Hydrate friends/DM presence and show Do Not Disturb with its own color/label.
- Send the server's JSON heartbeat, bound handshake/receive waits, cancel heartbeat
  work with its connection, and reconnect after returning from background suspension.
- Subscribe to events before starting the socket and retain its handle in `onOpen`
  so an immediate `ready` frame is not lost. Ignore late frames from closed attempts.

Manual availability preferences are not overwritten. Invisible users remain governed
by the backend's public offline response. No presence is inferred from typing, voice
participation, or a local guess about another user's activity.

## Validation

- Both complete unit suites: 96 passed, two optional credential-dependent tests skipped.
- New tests cover snapshot hydration, sparse offline handling, REST/event ordering,
  reconnect/account isolation, polling recovery and lifecycle, member counts, invalid
  events, heartbeat timeouts/cancellation, and a real local WebSocket handshake/ping.
- Direct and Play lint: zero errors (57 and 59 existing warnings respectively).
- Android 15 / API 35 emulator: member-screen population transitions and reconnect
  behavior passed with controlled REST data; existing stream-viewer regression passed.
- Debug application/instrumentation APKs and both signed release variants built successfully.
- Signed direct-release APK: signing identity matches earlier releases; 16 KiB APK
  zip alignment passed; install and startup passed on the API 35 emulator with no
  AndroidRuntime crash reported.

Production multi-account presence transitions and backend multi-device aggregation
have not been exercised in this review. Validate with two accounts/devices: open a
server with already-online members; change Online/Idle/DND/Invisible from the web
client; disconnect/reconnect one device; background/resume Android; sign out and
switch accounts. Status and counts should converge without leaving stale online users
or temporarily classifying the entire server offline.

## Published release

Version 2.0.25 was published through the existing signed GitHub workflow. The APK
downloaded from the website's stable link passes package/version, signing continuity,
manifest SHA-256, 16 KiB zip alignment, and GitHub source provenance checks. See
`distribution/verification-2.0.25.json`. The GitHub advisory scan examined 173 resolved
Maven components and exited successfully. Device checks used the local build; the
CI rebuild was separately verified as a public download.
