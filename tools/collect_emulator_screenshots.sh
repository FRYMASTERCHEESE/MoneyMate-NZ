#!/usr/bin/env bash
# Collect the screenshots already asserted by VisualTest. adb's sync-based
# directory pull can fail on certain Play Store emulator images even when
# shell-created PNGs exist; exec-out streams files without that directory API.
# Keep screenshot checks strict; never convert a failed instrumentation test
# into a successful CI result.
set -euo pipefail

remote_dir=/sdcard/Download/moneymate-screenshots
local_dir=screenshots
mkdir -p "$local_dir"

for filename in \
  01-welcome.png \
  02-home.png \
  03-spending.png \
  transactions.png \
  budgets.png \
  goals.png \
  bills.png \
  export.png
do
  remote="$remote_dir/$filename"
  local_file="$local_dir/$filename"
  if ! adb exec-out cat "$remote" > "$local_file"; then
    echo "::error::Could not export screenshot $remote after instrumentation."
    exit 1
  fi
  size=$(wc -c < "$local_file")
  if (( size <= 1000 )); then
    echo "::error::Screenshot $filename is empty or truncated ($size bytes)."
    exit 1
  fi
  signature=$(od -An -N8 -tx1 "$local_file" | tr -d ' \n')
  if [[ "$signature" != 89504e470d0a1a0a ]]; then
    echo "::error::Screenshot $filename has an invalid PNG signature."
    exit 1
  fi
  echo "Verified screenshot $filename ($size bytes)"
done

echo 'All eight real Android UI screenshots exported successfully.'
