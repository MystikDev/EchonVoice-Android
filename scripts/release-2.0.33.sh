#!/usr/bin/env bash
# Run from a normal terminal with GitHub CLI write access. This preserves the
# existing release gates: main validation first, then the signed tag workflow.
set -euo pipefail

cd "$(cd "$(dirname "$0")/.." && pwd)"
repo=MystikDev/EchonVoice-Android
tag=v2.0.33
expected_signer=bdfbbbf992e30b1b5bf07fdcedc09133c1db684623aab457c2ef95ecc5e6b836

command -v gh >/dev/null || { echo 'Install GitHub CLI before releasing.' >&2; exit 1; }
gh auth status --hostname github.com
test "$(git branch --show-current)" = main || { echo 'Run this on main.' >&2; exit 1; }
case "$(git remote get-url origin)" in
  https://github.com/MystikDev/EchonVoice-Android.git|git@github.com:MystikDev/EchonVoice-Android.git) ;;
  *) echo 'Unexpected origin; refusing to push.' >&2; exit 1 ;;
esac

# Refuse to include unrelated work or existing staged changes in the release.
git diff --cached --quiet || { echo 'Existing staged changes must be reviewed first.' >&2; exit 1; }
python3 - <<'PY'
from pathlib import Path
import re, subprocess
expected = {
    'README.md', 'app/build.gradle.kts', 'distribution/latest.json',
    'app/src/main/java/com/echon/voice/model/Auth.kt',
    'app/src/main/java/com/echon/voice/core/network/EchonApi.kt',
    'app/src/main/java/com/echon/voice/core/network/RefreshCookieInterceptor.kt',
    'app/src/main/java/com/echon/voice/feature/auth/AuthStore.kt',
    'app/src/main/java/com/echon/voice/feature/auth/Login.kt',
    'app/src/main/java/com/echon/voice/feature/auth/LoginSession.kt',
    'app/src/test/java/com/echon/voice/feature/auth/LoginSessionTest.kt',
    'app/src/test/java/com/echon/voice/feature/auth/LoginViewModelTest.kt',
    'app/src/androidTest/java/com/echon/voice/feature/auth/LoginScreenTest.kt',
    'scripts/release-2.0.33.sh',
}
tracked = subprocess.check_output(['git', 'diff', '--name-only', '-z']).decode().split('\0')
untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '-z']).decode().split('\0')
unexpected = (set(tracked + untracked) - {''}) - expected
if unexpected:
    raise SystemExit('Unrelated changes found; review before releasing: ' + ', '.join(sorted(unexpected)))
gradle = Path('app/build.gradle.kts').read_text()
if not re.search(r'versionCode\s*=\s*34\b', gradle) or not re.search(r'versionName\s*=\s*"2\.0\.33"', gradle):
    raise SystemExit('Expected Android version 2.0.33 (34).')
PY

git fetch origin main --tags
git merge-base --is-ancestor origin/main HEAD || {
  echo 'Remote main has new commits. Reconcile them before releasing.' >&2; exit 1;
}
git diff --check
git add README.md app/build.gradle.kts distribution/latest.json \
  app/src/main/java/com/echon/voice/model/Auth.kt \
  app/src/main/java/com/echon/voice/core/network/EchonApi.kt \
  app/src/main/java/com/echon/voice/core/network/RefreshCookieInterceptor.kt \
  app/src/main/java/com/echon/voice/feature/auth/AuthStore.kt \
  app/src/main/java/com/echon/voice/feature/auth/Login.kt \
  app/src/main/java/com/echon/voice/feature/auth/LoginSession.kt \
  app/src/test/java/com/echon/voice/feature/auth/LoginSessionTest.kt \
  app/src/test/java/com/echon/voice/feature/auth/LoginViewModelTest.kt \
  app/src/androidTest/java/com/echon/voice/feature/auth/LoginScreenTest.kt \
  scripts/release-2.0.33.sh
if ! git diff --cached --quiet; then
  git commit -m 'Support email two-step sign-in in Android 2.0.33'
fi
release_sha=$(git rev-parse HEAD)
if git rev-parse --verify --quiet "refs/tags/$tag" >/dev/null; then
  test "$(git rev-parse "$tag^{commit}")" = "$release_sha" || {
    echo 'Release tag already exists on a different commit; refusing to move it.' >&2; exit 1;
  }
fi
git push origin main

wait_for_workflow() {
  local workflow=$1 branch=$2 run_id= attempt
  for attempt in $(seq 1 30); do
    run_id=$(gh run list --repo "$repo" --workflow "$workflow" --commit "$release_sha" \
      --branch "$branch" --event push --limit 1 --json databaseId --jq '.[0].databaseId // empty')
    if [ -n "$run_id" ]; then
      gh run watch "$run_id" --repo "$repo" --exit-status
      return
    fi
    sleep 2
  done
  echo "No $workflow run found for $release_sha; refusing to continue." >&2
  return 1
}

wait_for_workflow validation.yml main
wait_for_workflow security-scan.yml main
if ! git rev-parse --verify --quiet "refs/tags/$tag" >/dev/null; then
  git tag "$tag" "$release_sha"
fi
git push origin "refs/tags/$tag"
wait_for_workflow release.yml "$tag"

verification_dir=$(mktemp -d)
trap 'rm -rf "$verification_dir"' EXIT
gh release download "$tag" --repo "$repo" --pattern echon-release.apk --dir "$verification_dir"
gh api "repos/$repo/contents/distribution/latest.json?ref=main" --jq .content > "$verification_dir/manifest.b64"
python3 - "$verification_dir" <<'PY'
from pathlib import Path
import base64, hashlib, json, sys
root = Path(sys.argv[1])
manifest = json.loads(base64.b64decode((root / 'manifest.b64').read_text()))
digest = hashlib.sha256((root / 'echon-release.apk').read_bytes()).hexdigest()
if manifest['version_name'] != '2.0.33' or manifest['version_code'] != 34 or manifest['sha256'] != digest:
    raise SystemExit('Published APK and update manifest do not match.')
print('Verified published Android 2.0.33 (34) APK and update manifest:', digest)
PY
sdk_dir=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
if [ -x "$sdk_dir/build-tools/36.0.0/apksigner" ]; then
  export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}
  signer=$("$sdk_dir/build-tools/36.0.0/apksigner" verify --print-certs "$verification_dir/echon-release.apk" \
    | awk -F': ' '/Signer #1 certificate SHA-256 digest:/{print $2}')
  test "$signer" = "$expected_signer" || { echo 'APK signer continuity check failed.' >&2; exit 1; }
  echo 'APK signer matches the existing Android release key.'
fi
git pull --ff-only origin main
gh release view "$tag" --repo "$repo" --json url --jq .url
