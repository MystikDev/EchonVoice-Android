#!/usr/bin/env bash
# Run only on a dedicated test emulator with no signed-in Echon account.
# The caller supplies a signed, minified release APK (CI uses a disposable key).
set -euo pipefail

apk=${1:?Usage: check-release-startup.sh signed-release.apk}
package=com.echon.voice
result_dir=$(mktemp -d)

# ADB install/UI automation can hang even after instrumentation has passed.
# Bound the entire command group, including any subprocesses, on Linux and macOS.
bounded() {
    python3 - "$@" <<'PY'
import os, signal, subprocess, sys
process = subprocess.Popen(sys.argv[2:], start_new_session=True)
try:
    sys.exit(process.wait(timeout=int(sys.argv[1])))
except subprocess.TimeoutExpired:
    os.killpg(process.pid, signal.SIGKILL)
    process.wait()
    print(f"Timed out after {sys.argv[1]}s: {sys.argv[2]}", file=sys.stderr)
    sys.exit(124)
PY
}

finish() {
    local check_status=$?
    trap - EXIT
    if [ "$check_status" -ne 0 ]; then
        bounded 10 adb devices -l || true
        bounded 10 adb exec-out cat /sdcard/echon-startup-check.xml || true
        bounded 10 adb shell dumpsys activity activities | tail -80 || true
        bounded 10 adb logcat -d -b crash || true
        bounded 10 adb logcat -d -s AndroidRuntime ActivityManager lmkd keystore2 | tail -120 || true
    fi
    rm -rf "$result_dir"
    exit "$check_status"
}
trap finish EXIT

bounded 30 adb wait-for-device
# Install all bytes before launching. Incremental installs made CI startup depend
# on the host-side file server and could report success before resources were ready.
bounded 120 adb install --no-incremental -r "$apk"
api=$(bounded 10 adb shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$api" -ge 33 ]; then
    bounded 10 adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS
fi
bounded 10 adb shell am force-stop "$package"
bounded 10 adb logcat -c
bounded 30 adb shell am start -W -n "$package/.MainActivity"

# am start can report success before an asynchronous startup crash. Require the
# actual signed-out UI plus a live process after the application settles.
sleep 5
for attempt in $(seq 1 10); do
    if ! bounded 10 adb shell pidof "$package" >/dev/null; then
        echo 'Release process exited during startup' >&2
        exit 1
    fi
    bounded 10 adb shell rm -f /sdcard/echon-startup-check.xml
    if bounded 15 adb shell uiautomator dump /sdcard/echon-startup-check.xml; then
        bounded 10 adb exec-out cat /sdcard/echon-startup-check.xml > "$result_dir/window.xml"
        if grep -q 'text="Sign in to Echon"' "$result_dir/window.xml"; then
            echo 'Minified release reached the Echon login screen'
            exit 0
        fi
    fi
    sleep 1
done
echo 'Release did not render the expected login screen' >&2
exit 1
