# Validation — September 6, 2026

Source version: 2.0.24 / versionCode 25.
Toolchain: JDK 17, Android SDK 35, Gradle 8.11.1, AGP 8.7.3.

## Automated checks

| Check | Result |
| --- | --- |
| Direct debug unit suite | 43 tests: 42 passed, 1 skipped, 0 failures/errors |
| Play debug unit suite | 31 tests: 30 passed, 1 skipped, 0 failures/errors |
| Direct debug lint | 0 errors, 57 warnings |
| Play debug lint | 0 errors, 59 warnings |
| Direct instrumentation on `echon_test`, Android 15 / API 35 / arm64 | 1 test passed: stream-view controls, landscape recreation, repeated renderer disposal |
| OSV Maven query | 140 resolved release runtime coordinates; no known advisories returned |
| `git diff --check` | Passed |

The same optional live-login test was skipped in each unit variant because
`ECHON_TEST_EMAIL` and `ECHON_TEST_PASSWORD` were not supplied. Existing live TLS
tests passed: the configured pin accepted the host and an incorrect pin rejected
it. Tests do not establish backend authorization correctness or live audio quality.

Lint warnings are dependency/toolchain update suggestions, existing unused
resources/Compose parameter guidance, and Play monochrome-icon suggestions. No
lint baseline or suppression was added to hide errors.

Commands (set JAVA_HOME and ANDROID_HOME for the installed toolchain):

```sh
./gradlew -Pechon.disableFirebase=true \
  :app:testDirectDebugUnitTest :app:testPlayDebugUnitTest \
  :app:lintDirectDebug :app:lintPlayDebug \
  :app:connectedDirectDebugAndroidTest

# Release builds use the existing Firebase configuration and release signing.
./gradlew :app:assembleDirectRelease :app:assemblePlayRelease
```

The local Firebase file has no `.debug` client. `echon.disableFirebase` is an
explicit local testing escape hatch; final release builds did not use that flag.
Signing credentials and Firebase configuration remain gitignored.

## Binary and runtime checks

Both direct and Play release builds complete with R8/resource shrinking and release
signing. Final artifact details and hashes are recorded in `artifacts.json`.
APK signature verification and 16 KiB ZIP alignment are checked for both variants.
The 64-bit native libraries (arm64-v8a and x86_64) have 16 KiB ELF LOAD alignment.
The 32-bit WebRTC binaries use 4 KiB alignment; 16 KiB Android systems require
compatible 64-bit libraries. No 16 KiB runtime was available for a device test.

The direct signed release was installed and launched on the local API 35 emulator.
The final startup/crash check is recorded in `artifacts.json`. No test-account
login, remote screen-share playback, background audio measurement, Firebase delivery,
or actual self-update session was exercised.

Original build reports remain under `~/.cache/echon-android-build/app/reports/`.
The review APK copies are local files under `distribution/review/`; they are
ignored by Git. Their hashes describe the locally tested builds, separately from
the signed APK rebuilt and distributed by the GitHub release workflow.

## Publication and advisory CI follow-up

Version 2.0.24 was published through the release workflow. The public APK's
version, hash, signature continuity, 16 KiB packaging alignment, and GitHub build
provenance were verified; the public artifact is recorded separately in
`artifacts.json`.

The existing advisory workflow referenced a non-executable action directory,
used a removed flag, and did not recognize Kotlin Gradle package sources. It now
resolves the current `directReleaseRuntimeClasspath` into CycloneDX with
`./gradlew -I gradle/runtime-sbom.init.gradle :app:writeSecuritySbom` before scanning.
The inventory includes Maven platform/metadata modules (173 components locally),
whereas the original review query covered 140 runtime artifact coordinates.
