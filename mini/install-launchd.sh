#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
if [[ ! -x .venv/bin/helm-mini && ! -x .venv/bin/python ]]; then
  echo "先在 mini/ 里 uv sync" >&2
  exit 1
fi
BIN="$ROOT/.venv/bin/python"
LABEL="cn.weiekko.helm-mini"
DEST="$HOME/Library/LaunchAgents/${LABEL}.plist"
cat > "$DEST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>${LABEL}</string>
  <key>WorkingDirectory</key>
  <string>${ROOT}</string>
  <key>ProgramArguments</key>
  <array>
    <string>${BIN}</string>
    <string>-m</string>
    <string>helm_mini</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>EnvironmentVariables</key>
  <dict>
    <key>HELM_MINI_TOKEN</key>
    <string>helm-mini-weiekko</string>
  </dict>
  <key>StandardOutPath</key>
  <string>${ROOT}/helm-mini.log</string>
  <key>StandardErrorPath</key>
  <string>${ROOT}/helm-mini.log</string>
</dict>
</plist>
EOF
launchctl bootout "gui/$(id -u)/${LABEL}" 2>/dev/null || true
sleep 0.4
launchctl bootstrap "gui/$(id -u)" "$DEST"
launchctl enable "gui/$(id -u)/${LABEL}" 2>/dev/null || true
launchctl kickstart -k "gui/$(id -u)/${LABEL}"
echo "已安装 LaunchAgent：$DEST"
echo "日志：$ROOT/helm-mini.log"
