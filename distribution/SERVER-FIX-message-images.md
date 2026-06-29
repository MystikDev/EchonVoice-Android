# Server fix required: message-attachment images return 403 (all clients)

**Audience:** Echon web/infra team
**Severity:** Visible bug for every client (web, iOS, Android). Message *text* works; uploaded
**images/videos never display.**
**Scope:** Backend/nginx only. No app-side fix is possible (proven below).

## Symptom

Every message attachment URL the API returns, e.g.

```
https://echon-voice.com/uploads/message/<serverId>/<yyyy-mm>/<file>.jpg
```

responds **`403 Forbidden`** (nginx) for **every** request, regardless of client or auth.

Avatars at `https://echon-voice.com/uploads/avatar/...` return **200** and display fine. So the
problem is specific to the `/uploads/message/` path.

## Proof it is server-side, not the app

Tested directly with `curl` (independent of any app) against a real attachment URL from the
`Test` server. All return `403`:

| Attempt | Result |
|---|---|
| No auth | 403 |
| `Authorization: Bearer <valid token>` | 403 |
| `?token=<valid token>` query param | 403 |
| Login cookie jar + bearer (simulates iOS `URLSession`) | 403 |
| `Referer: https://echon-voice.com/` + bearer | 403 |
| `/uploads/avatar/...` (control) | **200** ✅ |

Two diagnostic tells:

1. **The 403 response carries the *application's* security headers** (`Content-Security-Policy`,
   `X-Frame-Options`, `Permissions-Policy`, …). The public avatar path does **not** — it returns
   `Access-Control-Allow-Origin: *` and none of those headers. This means requests to
   `/uploads/message/` are **not matching a static-file `location` block at all** — they fall
   through to the SPA/app catch-all `location`, which denies them. There is no nginx rule that
   serves message uploads the way avatars are served.

2. **The only cookie the API sets is `refresh_token; HttpOnly; Secure; SameSite=Lax;
   Path=/v1/auth`.** Because it is scoped to `Path=/v1/auth`, no browser or native HTTP stack
   (including iOS `URLSession`) will ever send it to `/uploads/...`. So there is no
   cookie-based auth path to these files either — iOS hits the exact same 403.

3. No authenticated proxy route exists: `GET /v1/uploads/message/...`, `/api/uploads/...`,
   `/v1/files/...` all return **404**. The bare `/uploads/message/...` path is the only one that
   exists, and it 403s.

The clients are requesting the *exact* URL the API hands them, with the same auth they use for
avatars. There is nothing further an app can do.

## The fix (one of two)

**Option A — serve message uploads like avatars (simplest).**
Add an nginx `location` for message uploads mirroring the avatar one, pointing at the same
storage root, e.g.:

```nginx
location /uploads/message/ {
    alias /var/www/echon/uploads/message/;   # <-- match the avatar block's real root
    add_header Access-Control-Allow-Origin *;
    try_files $uri =404;
}
```

(Use whatever root/alias the working `/uploads/avatar/` block uses.) Message attachments are no
more sensitive than avatars today, so public static serving restores parity immediately.

**Option B — authenticated serving (if these must be access-controlled).**
Serve via the app with `X-Accel-Redirect`/signed URLs **and** change the API so the attachment
`url` it returns is one the client can actually fetch (a signed URL, or a token-bearing
`/v1/...` route). Today the API returns a bare unauthenticated path, so Option A is the only
fix that works without also changing the API payload.

## How to verify the fix

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  "https://echon-voice.com/uploads/message/<serverId>/<yyyy-mm>/<file>.jpg"
# expect: 200
```

Once this returns 200, images appear in all clients with **no app update** — the Android/iOS
apps already request the correct URL and render the bytes.
