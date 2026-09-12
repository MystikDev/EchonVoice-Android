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

The newer lint checks exposed a missing runtime notification-permission check in
the Direct updater. Notification posting now checks the permission and handles
revocation between checking and posting. The in-app update prompt remains usable.

## Publication gates

The release workflow calls the same reusable validation and security workflows
used for main/PR checks. Signing and publication depend on both succeeding:

- Both Direct and Play unit suites, Android lint, and minified release builds.
- Instrumentation on API 24, 35, 36, and 37.0; both variants on API 36, plus a
  dedicated API 36 environment with an asserted 16 KiB memory page size.
- OSV scanning of the resolved Direct release runtime dependency graph. Findings
  and scanner failures block publication.
- Signature, 16 KiB ZIP alignment, package name, and tag/version checks on the
  signed APK before publication; existing provenance and manifest hashing remain.

Validation jobs do not receive signing secrets and deliberately disable Firebase.
The final release uses the configured Firebase secret. Emulator tests exercise
the production presence UI/store and LiveKit renderer, including rotation and
disposal; they do not authenticate users or transmit a live call.

## Validation record

Release validation is in progress. Final results and public-artifact verification
will be recorded here after the checks complete.

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
- [Hilt releases](https://github.com/google/dagger/releases)
- [Android 17 background audio](https://developer.android.com/about/versions/17/changes/bg-audio)
- [AndroidX Security release notes](https://developer.android.com/jetpack/androidx/releases/security)
