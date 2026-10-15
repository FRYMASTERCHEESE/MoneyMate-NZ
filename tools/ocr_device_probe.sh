#!/usr/bin/env bash
# Fresh CI emulators and generated financial fixtures only. No real accounts.
set -euo pipefail
variant="${1:?debug or r8Test required}"
mode="${2:?cold or sequence required}"
case "$variant" in debug|r8Test) ;; *) exit 2 ;; esac
case "$mode" in cold|sequence) ;; *) exit 2 ;; esac
mkdir -p screenshots
prefix="screenshots/${mode}-${variant}"
snapshot() {
  local phase="$1"
  date -u +%FT%TZ > "${prefix}-${phase}-time.txt"
  for package in com.google.android.gms com.android.vending; do
    adb shell dumpsys package "$package" > "${prefix}-${phase}-${package}.txt" 2>&1 || echo "Diagnostic dumpsys failed for $package" >&2
  done
  adb shell ps -A > "${prefix}-${phase}-processes.txt" 2>&1 || echo 'Diagnostic process capture failed' >&2
}
if [[ "$mode" == cold ]]; then
  adb shell pm list packages > "${prefix}-initial-packages.txt"
  if grep -Eq '^package:nz[.]moneymate[.]app([.]r8test)?([.]test)?$' "${prefix}-initial-packages.txt"; then
    echo '::error::Cold run already contains MoneyMate; refusing a warmed device.'
    exit 1
  fi
  grep -Fxq 'package:com.google.android.gms' "${prefix}-initial-packages.txt"
  grep -Fxq 'package:com.android.vending' "${prefix}-initial-packages.txt"
fi
snapshot before
adb logcat -v threadtime > "${prefix}-logcat.txt" 2>&1 &
log_pid=$!
set +e
if [[ "$variant" == debug ]]; then
  timeout --kill-after=15 420 gradle --no-daemon :app:connectedDebugAndroidTest > "${prefix}-gradle.txt" 2>&1
else
  timeout --kill-after=15 420 gradle --no-daemon -PmoneymateTestBuildType=r8Test :app:connectedR8TestAndroidTest > "${prefix}-gradle.txt" 2>&1
fi
test_status=$?
set -e
snapshot after
kill "$log_pid" 2>/dev/null || echo 'Diagnostic logcat process already stopped' >&2
wait "$log_pid" 2>/dev/null || :
cat "${prefix}-gradle.txt"
# Summaries are diagnostic only; the saved test exit status remains authoritative.
for phase in before after; do
  echo "Play services $phase:"
  grep -E 'versionCode=|versionName=|lastUpdateTime=' "${prefix}-${phase}-com.google.android.gms.txt" || echo 'No version metadata captured'
done
echo 'OCR and Play services lifecycle events:'
grep -E 'MoneyMateOcr|started: syntheticPdf|finished: syntheticPdf|failed: syntheticPdf|OCR model|Force stopping com.google.android.gms.*(installPackageLI|pkg removed)|Package com.google.android.gms updated, version' "${prefix}-logcat.txt" || echo 'No matching diagnostic lifecycle events captured'
printf '%s\n' "$test_status" > "${prefix}-exit-status.txt"
# Preserve the real test result, including timeouts and module-install failures.
exit "$test_status"
