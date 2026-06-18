# Echon Android — Build Plan

Native Kotlin/Compose client mirroring the iOS app (`echon-ios/native/EchonVoice`),
same backend. Full plan: `~/.claude/plans/hashed-soaring-lollipop.md`.

Each phase ends runnable + verified on emulator/device with the demo account, then a commit.

## Phase 0 — Scaffold + secure networking spine  ✅ done (commit f20daef)
- [x] Install toolchain (JDK 17, Android SDK 35, build-tools, emulator, Gradle wrapper 8.11.1)
- [x] Gradle/Hilt/Compose project (single module, package-by-feature)
- [x] Harvest assets from `echon-android` (icons, theme colors, strings, netsec config)
- [x] Derive ISRG X1/X2 SPKI pins (X1 matches legacy wrapper pin — cross-validated)
- [x] `TlsPinning` (CertificatePinner), `SecureTokenStore` (EncryptedSharedPreferences)
- [x] `SessionStore`, `AuthInterceptor`, `TokenRefresher`, `TokenAuthenticator` (401 → rotate, single in-flight)
- [x] `EchonApi` (Retrofit) + `apiCall` error mapping, Hilt `NetworkModule`
- [x] **Verify:** `assembleDebug` green; `TlsPinningTest` 3/3 — pinned client connects, wrong pin fails closed, live login returns token
- [x] Commit (f20daef)

## Phase 1 — Auth + EULA gate + bootstrap  ✅ done
- [x] AuthStore (phase machine: Loading/SignedOut/NeedsEula/SignedIn), bootstrap, login, register, acceptTos, signOut
- [x] Login + Register (DOB date-picker, ToS checkbox) + native EULA gate screens
- [x] Root router on auth phase; encrypted-token cold-start restore; unauthorized→signOut wiring
- [x] **Verify (emulator, demo account):** login → Signed in (MystikDev#1838); force-stop→relaunch restores session; sign-out → Login; Register renders with DOB+ToS gate
- [ ] Commit

## Phase 2 — Moderation (early): report/block/blocked-list/instant filter/account deletion
## Phase 3 — Servers/channels + realtime chat (WS, reconcile, attachments)
## Phase 4 — DMs, friends, invites, profiles, members, presence
## Phase 5 — LiveKit voice + view-only screen share
## Phase 6 — Settings polish, theming, hardening, Play release prep

## Review
_(filled in as phases complete)_
