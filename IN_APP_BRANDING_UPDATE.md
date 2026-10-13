# Android 2.0.31: matching in-app icon

The upper-left header on signed-in tabs previously drew a separate three-bar
waveform. It now displays the same existing artwork resource used by the Android
launcher, at 32 dp, without applying a theme tint. Both normal and retro themes
use the shared brand artwork. The Echon wordmark and connection indicator remain
in place; the image is decorative for accessibility because the adjacent text
already names the app.

The launcher artwork is reused directly rather than copied, so future changes to
that resource also update the header. This release otherwise retains the 2.0.30
server compatibility, signup, and screen-sharing changes. Release validation and
public APK verification are recorded below after publication.


## Published verification

Published **2.0.31 (32)** from `35976fef76f44b9f3c8517372289761b420eaf68`.
[The tagged release](https://github.com/MystikDev/EchonVoice-Android/actions/runs/38468057332) passed all nine required jobs on
workflow attempt 1: unit tests, lint, both minified
build variants, all six emulator configurations (78 test executions and six
minified startup checks), dependency security scanning, and signed publication.
The scan reported 201 packages and no issues. Duplicate main-branch validation
runs were cancelled in favor of the tagged release's identical required gates.
GitHub's jobs-detail API returned HTTP 502 during monitoring; final status was
verified through the commit check-runs filtered to this release workflow. The
public workflow logs independently confirmed the device/startup counts.

Downloaded the public APK through the website's stable GitHub URL and verified
its package/version, SHA-256 agreement with the update manifest and GitHub asset,
signing-certificate continuity, GitHub provenance bound to the exact source/tag,
16 KB ZIP and 64-bit native ELF alignment, launcher resources, and notification
bell fallback. Firebase was enabled in the signed build.

APK SHA-256: `38f60bfa7637473dd07b5976b5ab4f75a989a46faf8b88d76b89973638a73059`.
Signing certificate SHA-256: `bdfbbbf992e30b1b5bf07fdcedc09133c1db684623aab457c2ef95ecc5e6b836`.

The public APK upgraded the official 2.0.30 (31) installation on the dedicated
Android 15 ARM64 emulator, preserved its original installation time, and reached
the sign-in screen. This was a signed-out emulator upgrade; physical-device visual
acceptance and authenticated migration were not tested. The header change uses
the previously inspected launcher artwork directly; no new image was generated.

[Public release](https://github.com/MystikDev/EchonVoice-Android/releases/tag/v2.0.31) · [Website](https://echon-voice.com/android.html)

Machine-readable evidence: `distribution/verification-2.0.31.json`.
The 2.0.30 live acceptance items for production signup and physical audio/sharing
remain open. Stream Deck work remains deferred to the user's later PC-side session.
