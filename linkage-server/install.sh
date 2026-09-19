#!/usr/bin/env bash
# linkage-server — install the home-side iroh agent that exposes your MC server.
# Wraps the stock linkage-iroh helper as a supervised service (systemd or launchd).
#
# Usage:
#   Public mode (no VPS, friends type a key):    sudo ./install.sh
#   Self-hosted mode (friends type a hostname):  sudo ./install.sh --relay-url https://relay.example.com
# Options: --mc-addr 127.0.0.1:25565  (the local MC server to expose)
set -euo pipefail

RELAY=""; MC="127.0.0.1:25565"
while [ $# -gt 0 ]; do case "$1" in
  --relay-url) RELAY="$2"; shift 2;;
  --mc-addr)   MC="$2"; shift 2;;
  *) echo "unknown arg $1"; exit 1;;
esac; done
[ "$(id -u)" = 0 ] || { echo "run with sudo"; exit 1; }
RUN_USER="${SUDO_USER:-$USER}"; HOME_DIR="$(eval echo "~$RUN_USER")"
DIR="$HOME_DIR/linkage-server"; HERE="$(cd "$(dirname "$0")" && pwd)"
OS="$(uname -s)"

echo "==> install into $DIR"
sudo -u "$RUN_USER" mkdir -p "$DIR"
# the linkage-iroh binary must sit next to this installer (bundled in the release)
[ -x "$HERE/linkage-iroh" ] || { echo "missing linkage-iroh binary next to install.sh"; exit 1; }
install -o "$RUN_USER" -g "$RUN_USER" -m 755 "$HERE/linkage-iroh" "$DIR/linkage-iroh"
install -o "$RUN_USER" -g "$RUN_USER" -m 755 "$HERE/run-agent.sh" "$DIR/run-agent.sh"
# persist a stable key once
if [ ! -f "$DIR/iroh-secret" ]; then
  sudo -u "$RUN_USER" sh -c "openssl rand -hex 32 > '$DIR/iroh-secret'; chmod 600 '$DIR/iroh-secret'"
fi

ENVLINE="LINKAGE_MC_ADDR=$MC"; [ -n "$RELAY" ] && ENVLINE="$ENVLINE LINKAGE_RELAY_URL=$RELAY"

if [ "$OS" = Linux ]; then
  echo "==> systemd service"
  cat > /etc/systemd/system/linkage-server.service <<EOF
[Unit]
Description=Selfhosted Linkage home agent
After=network-online.target
Wants=network-online.target
[Service]
User=$RUN_USER
WorkingDirectory=$DIR
Environment=$ENVLINE
ExecStart=$DIR/run-agent.sh
Restart=on-failure
RestartSec=3
[Install]
WantedBy=multi-user.target
EOF
  systemctl daemon-reload; systemctl enable --now linkage-server
  sleep 5; LOG="$(journalctl -u linkage-server -n 40 --no-pager 2>/dev/null || true)"
elif [ "$OS" = Darwin ]; then
  echo "==> launchd daemon"
  PL=/Library/LaunchDaemons/com.blopybox.linkage-server.plist
  cat > "$PL" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>com.blopybox.linkage-server</string>
  <key>UserName</key><string>$RUN_USER</string>
  <key>EnvironmentVariables</key><dict>
    <key>LINKAGE_MC_ADDR</key><string>$MC</string>$([ -n "$RELAY" ] && printf '\n    <key>LINKAGE_RELAY_URL</key><string>%s</string>' "$RELAY")
  </dict>
  <key>ProgramArguments</key><array><string>/bin/sh</string><string>$DIR/run-agent.sh</string></array>
  <key>RunAtLoad</key><true/><key>KeepAlive</key><true/>
  <key>StandardOutPath</key><string>$DIR/listen.log</string>
  <key>StandardErrorPath</key><string>$DIR/listen.log</string>
</dict></plist>
EOF
  launchctl bootout system/com.blopybox.linkage-server 2>/dev/null || true
  launchctl load -w "$PL"; sleep 5; LOG="$(cat "$DIR/listen.log" 2>/dev/null || true)"
else echo "unsupported OS $OS"; exit 1; fi

echo
if [ -n "$RELAY" ]; then
  echo "SELF-HOSTED mode. Paste this ticket into the relay's registry (servers.\"<port>\".ticket):"
  echo "$LOG" | grep -oE "endpoint[a-z0-9]+" | head -1
else
  echo "PUBLIC mode. Friends type this PUBLIC KEY as the Address (+ your MC port):"
  echo "$LOG" | grep -oE "endpoint id is [a-z0-9]+" | awk '{print $4}' | head -1
fi
echo "(Agent runs as a service and restarts on boot. Re-run to reconfigure.)"
