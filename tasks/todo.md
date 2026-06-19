# Echon Android — Build Plan

Native Kotlin/Compose client mirroring the iOS app (`echon-ios/native/EchonVoice`),
same backend. Full plan: `~/.claude/plans/hashed-soaring-lollipop.md`.

Each phase ends runnable + verified on emulator/device with the demo account, then a commit.

## Distribution — direct download from website  ✅ pipeline done
- [x] Release keystore + Gradle signing config (secrets gitignored); signed release APK builds under R8
- [x] Fix R8 (Tink errorprone dontwarn); release build verified to RUN (login works minified) + APK signature verified (v2)
- [x] In-app updater: manifest model, UpdateChecker, ApkInstaller (FileProvider + REQUEST_INSTALL_PACKAGES), UpdatePrompt overlay; 5/5 unit tests
- [x] download.html + latest.json + distribution/README.md (hosting handoff for web team)
- [x] Relocate build output out of iCloud-synced tree (fixes conflicted-copy build breakage)
- [ ] Web team: host APK + latest.json + /download; complete Android developer verification
- [ ] (later) bump v3 signing; density PNG launcher icons

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
- [x] Commit (ccb195d)

## Phase 2 — Moderation  ✅ done (commit 68d7d61)
- [x] BlocksStore (optimistic, render-time filter), ReportSheet, BlockedUsersScreen, account deletion, Settings
- [x] Verified on emulator: blocked list loads live (Phrosen#2963); delete dialog gates; nav works

## Phase 2 — Moderation (early): report/block/blocked-list/instant filter/account deletion
## Phase 3 — Servers/channels + realtime chat  ✅ done (commit 981989a)
- [x] WS layer (WsClient/WsEvent/RealtimeStore), ServersStore, MessageStore+ChatStores
- [x] ServersScreen, ChatScreen, MessageRow, MessageActionsSheet, MainScaffold bottom-nav
- [x] Verified live: servers/channels load, history paginates, message sent+confirmed, image attachments render, block filter active

## Phase 3 — Servers/channels + realtime chat (WS, reconcile, attachments)
## Phase 4 — DMs, friends, invites, profiles, members  ✅ done (commit 0b19554)
- [x] DMsStore/FriendsStore, DMListScreen, FriendsScreen, UserProfileSheet, MembersScreen, invite sheets
- [x] 4-tab MainScaffold; chat route carries channelKind (DMs reuse ChatScreen); shared profile/report sheets
- [x] Verified live: Friends lists Echon01#6413; DMs opens Echon01 DM with history

## Phase 4 — DMs, friends, invites, profiles, members, presence
## Phase 5 — LiveKit voice + view-only screen share
## Phase 6 — Settings polish, theming, hardening, Play release prep

## Review
_(filled in as phases complete)_
