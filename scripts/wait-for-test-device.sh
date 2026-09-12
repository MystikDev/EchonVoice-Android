#!/usr/bin/env bash
set -euo pipefail

# A fresh emulator may report boot complete before credential storage is unlocked.
# Require an unlocked user and a stable compositor before installing test APKs.
# This does not retry failing app tests or weaken the app's credential protection.
last_compositor=""
stable_samples=0
for ((attempt = 0; attempt < 36; attempt++)); do
  compositor=$(adb shell pidof surfaceflinger 2>/dev/null | tr -d '\r' || true)
  if [[ -n "$compositor" ]] &&
      [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] &&
      adb shell dumpsys user 2>/dev/null | grep -q 'State: RUNNING_UNLOCKED'; then
    if [[ "$compositor" == "$last_compositor" ]]; then
      stable_samples=$((stable_samples + 1))
    else
      stable_samples=0
    fi
    if ((stable_samples >= 5)); then
      echo "Emulator is unlocked with a stable SurfaceFlinger process."
      exit 0
    fi
  else
    stable_samples=0
  fi
  last_compositor="$compositor"
  sleep 5
done

echo "Emulator did not reach a stable, unlocked state." >&2
adb shell dumpsys user || true
adb logcat -d -b crash || true
exit 1
