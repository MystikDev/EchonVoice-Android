# Android foundation upgrade — 2.0.26

This release updates the build and UI foundations and makes automated validation
a prerequisite for publishing the sideload APK. Calling-stack and encrypted-token
storage migrations remain separate changes with their own acceptance testing.

## Coordinated baseline

| Component | Previous | New |
| --- | --- | --- |
| Android Gradle Plugin | 8.7.3 | 9.1.1 |
| Gradle wrapper | 8.11.1 | 9.3.1, distribution SHA-256 pinned |
| Kotlin / Compose compiler | 2.0.21 | 2.3.21 |
| KSP | 2.0.21-1.0.28 | 2.3.9 |
| Hilt | 2.52 | 2.59.2 |
| AndroidX Hilt Compose integration | 1.2.0 | 1.3.0, lifecycle ViewModel artifact |
| Compose BOM | 2024.10.01 | 2026.06.01 |
| Core / Lifecycle / Activity | 1.13.1 / 2.8.7 / 1.9.3 | 1.17.0 / 2.10.0 / 1.12.4 |
| Navigation / Coroutines | 2.8.4 / 1.9.0 | 2.9.8 / 1.10.2 |
| WorkManager | 2.9.1 | 2.11.2 |
| Compile / target / minimum API | 35 / 35 / 24 | 37.0 / 36 / 24 |

These are coordinated stable versions, not a claim that every dependency is the
newest available. JDK 17 remains the supported build runtime. AGP now provides
built-in Kotlin integration; the legacy Kotlin Android plugin and `kotlinOptions`
DSL have been removed. The compiler, serialization plugin, and Compose compiler
share the same Kotlin version. Hilt ViewModel imports use the new lifecycle package.
Legacy Material icons have an explicit version because the newer Compose BOM no
longer supplies one.

Compiling against API 37 makes newer platform APIs available to the compiler;
targeting API 36 opts into Android 16 behavior. Targeting API 37 is a later behavior
migration, especially for background audio. Compilation alone does not establish
call reliability, device compatibility, or Play acceptance.

The API 36 behavior review found that the host already enables edge-to-edge,
navigation uses AndroidX Compose rather than legacy back-key interception, and
the manifest imposes no orientation/aspect-ratio restrictions. The upgraded
Activity and Navigation libraries retain supported back handling. Physical
Bluetooth routing and real background call acceptance remain outstanding.

The newer lint checks exposed a missing runtime notification-permission check in
the Direct updater. Notification posting now checks the permission and handles
revocation between checking and posting. The in-app update prompt remains usable.

Signed startup testing also reproduced a crash when Room reflectively created
WorkManager's database after R8 removed its no-argument constructor. The database
constructor is now kept explicitly, and WorkManager uses its stable 2.11.2 release
with newer Room dependencies and background-network fixes.

## Publication gates

The release workflow calls the same reusable validation and security workflows
used for main/PR checks. Signing and publication depend on both succeeding:

- Both Direct and Play unit suites, Android lint, and minified release builds.
- Instrumentation on API 24, 35, 36, and 37.0; both variants on API 36, plus a
  dedicated API 36 environment with an asserted 16 KiB memory page size.
- Minified-release startup on every matrix device, signed with a disposable test
  key. The gate waits for a live process and the actual Echon login screen.
- OSV scanning of the resolved Direct release runtime dependency graph. Findings
  and scanner failures block publication.
- Signature, 16 KiB ZIP alignment, package name, and tag/version checks on the
  signed APK before publication; existing provenance and manifest hashing remain.

Validation jobs do not receive signing secrets and deliberately disable Firebase.
Each job explicitly installs the SDK and pinned command-line tools; it does not
depend on the tools being present on a GitHub runner's PATH.
Emulators use the supported software graphics backend and wait for a stable,
unlocked user before tests. API 37 and the 16 KiB image receive 4 GiB RAM to avoid
first-boot memory pressure. The API 37 x86_64 image currently
crashes SurfaceFlinger when the host lacks ReadColorBufferDMA, so the required
transport is enabled with `-feature GLDirectMem,HasSharedSlotsHostMemoryAllocator`
for API 37 only. The upstream host enables this extension when both features are
enabled; the image's mapper aborts if it is absent. SystemUI,
the app's graphics rendering, and all test assertions remain enabled.
The final release uses the configured Firebase secret. Emulator tests exercise
the production presence UI/store and LiveKit renderer, including rotation and
disposal; they do not authenticate users or transmit a live call.

## Validation record

Local unit validation passed: Direct 54 passed / 1 optional login skipped; Play
42 passed / 1 optional login skipped. Lint has zero errors (47 Direct and 49 Play
warnings, chiefly dependency updates and style/resource suggestions).

Both instrumentation tests passed in each variant on an ARM64 API 36 emulator
whose reported memory page size was 16384 bytes. This exercises real native
LiveKit renderer creation/disposal, orientation, and presence UI reconciliation.

The startup gate was checked against the pre-fix APK and correctly rejected its
database initialization crash. Both corrected signed release variants then passed
the gate on API 37. The Direct 2.0.25 public APK was installed on API 36 with 16 KiB
pages and upgraded to signed 2.0.26 without clearing data; startup passed and the
background updater job remained registered. This signed-out upgrade check does
not establish preservation of an authenticated session.

The final dependency scan resolved 198 runtime Maven packages and reported no
known issues. Both signed variants pass 16 KiB ZIP alignment; the Direct signing
certificate is unchanged.

The complete [pre-release CI run](https://github.com/MystikDev/EchonVoice-Android/actions/runs/34701453983)
passed on the release commit: both unit/lint/release-build variants, 12 UI test
executions across six emulator configurations, and six minified-release startup
checks. The matching [dependency scan](https://github.com/MystikDev/EchonVoice-Android/actions/runs/34701453969)
also passed. The [tagged release workflow](https://github.com/MystikDev/EchonVoice-Android/actions/runs/34702159549)
reran every gate successfully before signing and publishing
[v2.0.26](https://github.com/MystikDev/EchonVoice-Android/releases/tag/v2.0.26).

The APK downloaded through the website's stable link reports version 2.0.26,
build 27, package `com.echon.voice`, minimum API 24, and target API 36. Its SHA-256
matches the public updater manifest and GitHub release asset. Signature continuity,
16 KiB ZIP alignment, and the GitHub provenance statement's release commit were
verified. The production build restored Firebase configuration. The complete
public verification record is [distribution/verification-2.0.26.json](distribution/verification-2.0.26.json).

## Subsequent work

- Upgrade LiveKit separately and evaluate real screen-share audio with two physical
  devices, including weak-network reconnects, Bluetooth, rotation, and backgrounding.
- Replace deprecated AndroidX Security Crypto with a platform-Keystore-backed
  store through an explicit migration that preserves existing signed-in sessions.
- Complete the API 37 target behavior review and physical-device acceptance before
  increasing the target SDK again.

## Primary references

- [AGP compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- [Built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Android 16 target behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16)
- [Hilt releases](https://github.com/google/dagger/releases)
- [WorkManager release notes](https://developer.android.com/jetpack/androidx/releases/work)
- [Android 17 background audio](https://developer.android.com/about/versions/17/changes/bg-audio)
- [AndroidX Security release notes](https://developer.android.com/jetpack/androidx/releases/security)
- [Emulator requirements and release notes](https://developer.android.com/studio/releases/emulator)
- [Graphics feature advertisement in the upstream emulator](https://android.googlesource.com/platform/hardware/google/gfxstream/+/refs/heads/main/host/RenderControl.cpp)
- [Graphics mapper requirements in the upstream system image](https://android.googlesource.com/device/generic/goldfish/+/refs/heads/main/hals/gralloc/mapper.cpp)
