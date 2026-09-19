#!/usr/bin/env bash
# linkage-relay — one-command VPS setup for Selfhosted Linkage (self-hosted mode).
# Installs n0's stock iroh-relay + wires Caddy TLS + a static registry directory.
# Pure OSS: the only bespoke bits are this script and the tiny cert-sync helper.
#
# Usage:  sudo ./install.sh <hostname>        e.g.  sudo ./install.sh relay.example.com
# Assumes: Ubuntu/Debian, systemd, and Caddy already installed & running (it edits the Caddyfile).
set -euo pipefail

HOST="${1:-}"
[ -n "$HOST" ] || { echo "usage: sudo $0 <hostname>  (e.g. relay.example.com)"; exit 1; }
[ "$(id -u)" = 0 ] || { echo "run with sudo"; exit 1; }
RUN_USER="${SUDO_USER:-$USER}"
HOME_DIR="$(eval echo "~$RUN_USER")"
ARCH="$(uname -m)"; case "$ARCH" in x86_64) RTARGET=x86_64-unknown-linux-gnu;; aarch64|arm64) RTARGET=aarch64-unknown-linux-gnu;; *) echo "unsupported arch $ARCH"; exit 1;; esac
IROH_VER="${IROH_VER:-v1.2.0}"
QUIC_PORT="${QUIC_PORT:-7842}"

echo "==> 1/6  install iroh-relay $IROH_VER ($RTARGET) to $HOME_DIR/iroh-relay"
sudo -u "$RUN_USER" mkdir -p "$HOME_DIR/iroh-relay"
if [ ! -x "$HOME_DIR/iroh-relay/iroh-relay" ]; then
  url="https://github.com/n0-computer/iroh/releases/download/$IROH_VER/iroh-relay-$IROH_VER-$RTARGET.tar.gz"
  curl -fsSL "$url" -o /tmp/iroh-relay.tgz
  tar xzf /tmp/iroh-relay.tgz -C /tmp; rm /tmp/iroh-relay.tgz
  f="$(find /tmp -maxdepth 2 -name iroh-relay -type f | head -1)"
  install -o "$RUN_USER" -g "$RUN_USER" -m 755 "$f" "$HOME_DIR/iroh-relay/iroh-relay"
fi

echo "==> 2/6  relay config (HTTP behind Caddy + QUIC address discovery on udp/$QUIC_PORT)"
cat > "$HOME_DIR/iroh-relay/config.toml" <<EOF
http_bind_addr = "127.0.0.1:3340"
enable_quic_addr_discovery = true
enable_metrics = false

[tls]
hostname = "$HOST"
cert_mode = "Manual"
manual_cert_path = "/etc/iroh-relay/tls/$HOST.crt"
manual_key_path  = "/etc/iroh-relay/tls/$HOST.key"
https_bind_addr = "127.0.0.1:3341"
quic_bind_addr  = "0.0.0.0:$QUIC_PORT"
EOF
chown "$RUN_USER:$RUN_USER" "$HOME_DIR/iroh-relay/config.toml"

echo "==> 3/6  cert-sync (copy Caddy's cert where the relay user can read it; reload on renewal)"
CADDY_CERT_DIR="/var/lib/caddy/.local/share/caddy/certificates/acme-v02.api.letsencrypt.org-directory/$HOST"
cat > /usr/local/bin/iroh-relay-cert-sync.sh <<EOF
#!/bin/sh
set -e
SRC="$CADDY_CERT_DIR"; DST=/etc/iroh-relay/tls; mkdir -p "\$DST"; changed=0
for ext in crt key; do
  if ! cmp -s "\$SRC/$HOST.\$ext" "\$DST/$HOST.\$ext" 2>/dev/null; then cp "\$SRC/$HOST.\$ext" "\$DST/$HOST.\$ext"; changed=1; fi
done
chown -R $RUN_USER:$RUN_USER "\$DST"; chmod 700 "\$DST"; chmod 600 "\$DST"/*.key 2>/dev/null || true; chmod 644 "\$DST"/*.crt 2>/dev/null || true
[ "\$changed" = 1 ] && systemctl try-restart iroh-relay || true
EOF
chmod +x /usr/local/bin/iroh-relay-cert-sync.sh
cat > /etc/systemd/system/iroh-relay-cert-sync.service <<EOF
[Unit]
Description=Sync Caddy cert to iroh-relay
[Service]
Type=oneshot
ExecStart=/usr/local/bin/iroh-relay-cert-sync.sh
EOF
cat > /etc/systemd/system/iroh-relay-cert-sync.timer <<EOF
[Unit]
Description=Daily iroh-relay cert sync
[Timer]
OnCalendar=daily
Persistent=true
[Install]
WantedBy=timers.target
EOF

echo "==> 4/6  registry webroot + empty directory"
mkdir -p /etc/iroh-relay/webroot/.well-known
[ -f /etc/iroh-relay/webroot/.well-known/selfhosted-linkage.json ] || \
  echo '{ "version": 1, "servers": {} }' > /etc/iroh-relay/webroot/.well-known/selfhosted-linkage.json
chmod -R a+rX /etc/iroh-relay/webroot

echo "==> 5/6  Caddy site for $HOST (registry + relay reverse-proxy)"
if ! grep -q "^$HOST {" /etc/caddy/Caddyfile 2>/dev/null; then
  cp /etc/caddy/Caddyfile "/etc/caddy/Caddyfile.bak-linkage-$(date +%s)"
  cat >> /etc/caddy/Caddyfile <<EOF

$HOST {
	handle /.well-known/selfhosted-linkage.json {
		root * /etc/iroh-relay/webroot
		file_server
	}
	handle { reverse_proxy 127.0.0.1:3340 }
}
EOF
  caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null
  systemctl reload caddy
fi
# obtain the cert (a request triggers Caddy's ACME), then sync it in
curl -fsS "https://$HOST/" -o /dev/null 2>/dev/null || true
sleep 3; /usr/local/bin/iroh-relay-cert-sync.sh || true

echo "==> 6/6  iroh-relay service"
cat > /etc/systemd/system/iroh-relay.service <<EOF
[Unit]
Description=iroh relay for Selfhosted Linkage
After=network-online.target
Wants=network-online.target
[Service]
User=$RUN_USER
WorkingDirectory=$HOME_DIR/iroh-relay
ExecStart=$HOME_DIR/iroh-relay/iroh-relay -c $HOME_DIR/iroh-relay/config.toml
Restart=on-failure
RestartSec=3
LimitNOFILE=65536
[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now iroh-relay-cert-sync.timer
systemctl enable --now iroh-relay

echo
echo "DONE. Relay + registry live at https://$HOST"
echo "NEXT:"
echo "  1) Open inbound UDP $QUIC_PORT in your cloud firewall (for direct-connection QUIC discovery)."
echo "  2) On your home box, run linkage-server with --relay-url https://$HOST, then paste the"
echo "     ticket it prints into /etc/iroh-relay/webroot/.well-known/selfhosted-linkage.json"
echo "     under servers.\"<port>\" = { transport:iroh, relay_url:https://$HOST, ticket:... }"
echo "  3) Friends: tick the mod checkbox, Address = $HOST, Port = <port>."
