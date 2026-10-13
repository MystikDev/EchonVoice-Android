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
