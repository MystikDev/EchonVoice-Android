# Push notifications (FCM) — setup & backend contract

The Android app now has a **complete client-side implementation** of push
notifications for new messages/DMs, using **Firebase Cloud Messaging (FCM)**. It
is **inert until two things are configured**:

1. A **Firebase project** + `google-services.json` (app owner — see §1).
2. **Backend support**: a device-token endpoint + sending FCM pushes (web team — §2/§3).

Until both land, the app builds and runs normally with no push and no crashes
(the client degrades gracefully). No app code changes are needed to turn it on —
just the Firebase config file and the backend.

---

## Why FCM (not a persistent socket)
The realtime WebSocket only runs while the app is open, so it can't deliver
notifications when the app is backgrounded/closed. A persistent
foreground-service socket would drain battery and is a Google Play policy risk.
FCM is the standard, battery-friendly, Play-approved mechanism.

---

## 1. Firebase project + `google-services.json` (app owner)
1. Go to <https://console.firebase.google.com> → **Add project** (or reuse one).
2. Add an **Android app** with package name **`com.echon.voice`**.
   - The app ships two build flavors (`direct`, `play`) but **one** applicationId
     (`com.echon.voice`), so a single Firebase Android app covers both. (The debug
     build uses `com.echon.voice.debug` — optionally add it too for testing.)
3. Download **`google-services.json`** and place it at **`app/google-services.json`**.
   - It is **gitignored** (not committed to the public repo). Each build environment
     needs its own copy; for CI, add it as a base64 secret and write it out in the
     workflow (same pattern as the signing keystore).
4. In Firebase → Project settings → **Cloud Messaging**, note the credentials the
   backend will use to send (service account JSON for the HTTP v1 API).

That's the only client-side step — the Gradle build auto-detects the file and
enables FCM.

## 2. Device-token endpoint (backend)
The app registers its FCM token (authenticated) whenever the user signs in and
whenever FCM rotates the token.

```
POST /v1/devices
Authorization: Bearer <access token>
Content-Type: application/json

{ "token": "<fcm registration token>", "platform": "android" }
```

Backend should:
- Associate the token with the authenticated user (a user may have several devices).
- **Upsert** by token (idempotent — the app may re-send the same token).
- Keep `platform` for future iOS/APNs support.
- Prune tokens FCM reports as stale (`UNREGISTERED` / `NOT_FOUND` on send).

A `2xx` with any/empty body is fine. (A `4xx/5xx` is tolerated by the client — it
just logs and retries on next sign-in.)

## 3. Sending a push (backend)
When a user should be notified (new message in a channel they're in, a DM, or a
mention), send an **FCM *data* message** (not a "notification" message) to each of
the recipient's registered tokens, so the app controls display and deep-linking
even when backgrounded.

**Data payload keys the client reads** (all strings):

| key | value |
|-----|-------|
| `channel_id` | the channel/DM id to open on tap |
| `channel_kind` | `"dm"` for a direct message; anything else = server channel |
| `channel_name` | display name (channel or DM peer) |
| `title` | notification title (e.g. sender or channel name) |
| `body` | notification text (message preview) |

Example (FCM HTTP v1):
```json
{
  "message": {
    "token": "<device token>",
    "data": {
      "channel_id": "5b3ab957-4781-4a08-a945-4f5f60a988dc",
      "channel_kind": "text",
      "channel_name": "general",
      "title": "MystikDev in #general",
      "body": "hey, are you around?"
    },
    "android": { "priority": "high" }
  }
}
```

**Important policy/UX rules for the backend:**
- **Don't push to a user who has blocked the sender** (the app filters in-UI, but a
  notification would still leak the blocked user's name/preview). Respect blocks
  server-side before sending.
- Don't push a user for their **own** messages, or for a channel/DM they currently
  have open (optional refinement; the app has no "active channel" signal server-side
  yet, so at minimum skip self).
- Keep `body` short and free of sensitive content beyond a normal preview.

---

## What the client already does (implemented)
- Declares `com.echon.voice.core.push.EchonMessagingService` (`FirebaseMessagingService`).
- On sign-in and on token rotation, POSTs the token to `/v1/devices` (guarded — no-op
  if Firebase/endpoint absent).
- On a data push, posts a notification on a **"Messages"** channel (importance high),
  respecting the user's notification permission (`POST_NOTIFICATIONS`, already requested).
- **Tapping** the notification deep-links straight into the channel/DM.

## Testing once configured
1. Drop `google-services.json` into `app/`, build, install, sign in.
2. Confirm the app calls `POST /v1/devices` (check backend logs) — the token is registered.
3. From Firebase Console → **Cloud Messaging → send test message** to that token with
   the data keys above (or have the backend send one), with the app backgrounded.
4. The "Messages" notification should appear; tapping it opens the right conversation.
