#!/bin/sh
# Home-side linkage agent: exposes the local Minecraft server over iroh.
# Two modes, chosen by whether LINKAGE_RELAY_URL is set:
#   - set   → self-hosted mode: use that relay (friends type its hostname).
#   - unset → public mode: use iroh's free public relays + discovery (friends type this key).
# The iroh secret key is persisted (iroh-secret, chmod 600) so the identity/ticket is stable.
set -e
cd "$(dirname "$0")"
[ -f iroh-secret ] || { echo "missing iroh-secret" >&2; exit 1; }
export IROH_SECRET="$(cat iroh-secret)"
MC="${LINKAGE_MC_ADDR:-127.0.0.1:25565}"
if [ -n "${LINKAGE_RELAY_URL:-}" ]; then
  exec ./linkage-iroh listen-tcp --host "$MC" --relay-url "$LINKAGE_RELAY_URL" -v
else
  exec ./linkage-iroh listen-tcp --host "$MC" -v
fi
