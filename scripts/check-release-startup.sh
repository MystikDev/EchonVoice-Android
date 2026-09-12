#!/usr/bin/env bash
# Run only on a dedicated test emulator with no signed-in Echon account.
# The caller supplies a signed, minified release APK (CI uses its debug key).
set -euo pipefail

apk=${1:?Usage: check-release-startup.sh signed-release.apk}
package=com.echon.voice
result_dir=$(mktemp -d)
trap 'rm -rf "$result_dir"' EXIT

adb install -r "$apk"
api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$api" -ge 33 ]; then
    adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS
fi
adb shell am force-stop "$package"
adb logcat -c
adb shell am start -W -n "$package/.MainActivity"

# am start can report success before an asynchronous startup crash. Require the
# actual signed-out UI plus a live process after the application settles.
sleep 5
for attempt in $(seq 1 10); do
    if ! adb shell pidof "$package" >/dev/null; then
        adb logcat -d -b crash
        echo 'Release process exited during startup' >&2
        exit 1
    fi
    if adb shell uiautomator dump /sdcard/echon-startup-check.xml >/dev/null 2>&1; then
        adb exec-out cat /sdcard/echon-startup-check.xml > "$result_dir/window.xml"
        if grep -q 'text="Sign in to Echon"' "$result_dir/window.xml"; then
            echo 'Minified release reached the Echon login screen'
            exit 0
        fi
    fi
    sleep 1
done
adb logcat -d -b crash
echo 'Release did not render the expected login screen' >&2
exit 1
