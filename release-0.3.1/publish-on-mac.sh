#!/usr/bin/env bash
set -euo pipefail
cd /Users/weiekko/dock
DIST="dist/Helm-红米K20"
NOTES="/Users/weiekko/dock/release-0.3.1/notes.md"
GH=/opt/homebrew/bin/gh

if $GH release view v0.3.1 --repo wxydejoy/Helm >/dev/null 2>&1; then
  $GH release delete v0.3.1 --repo wxydejoy/Helm --yes
fi

$GH release create v0.3.1 \
  --repo wxydejoy/Helm \
  --title "v0.3.1" \
  --notes-file "$NOTES" \
  "$DIST/Helm-0.3.1.apk"

$GH release view v0.3.1 --repo wxydejoy/Helm --web=false
