#!/bin/sh
set -e
SRC="/var/lib/caddy/.local/share/caddy/certificates/acme-v02.api.letsencrypt.org-directory/relay.example.com"
DST=/etc/iroh-relay/tls
mkdir -p "$DST"
changed=0
for ext in crt key; do
  if ! cmp -s "$SRC/relay.example.com.$ext" "$DST/relay.example.com.$ext" 2>/dev/null; then
    cp "$SRC/relay.example.com.$ext" "$DST/relay.example.com.$ext"; changed=1
  fi
done
chown -R admin:admin "$DST"; chmod 700 "$DST"; chmod 600 "$DST"/*.key; chmod 644 "$DST"/*.crt
[ "$changed" = 1 ] && systemctl try-restart iroh-relay || true
