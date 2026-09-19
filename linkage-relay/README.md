# linkage-relay — the VPS companion (self-hosted mode)

One-command setup of the VPS side: n0's stock **iroh-relay** + **Caddy** TLS + a static **registry**
directory. Only needed for *self-hosted mode* (public mode uses iroh's free relays, no VPS).

```sh
sudo ./install.sh relay.example.com
```
Then open inbound **UDP 7842** in your cloud firewall (QUIC direct-connection discovery), and paste your
home agent's ticket into `/etc/iroh-relay/webroot/.well-known/selfhosted-linkage.json`.

It's **pure OSS + config** — the only bespoke code is this installer and the ~15-line cert-sync helper it
writes. Assumes Ubuntu/Debian + systemd + Caddy already installed. The generic config files (`config.toml`, `registry.example.json`, `iroh-relay-cert-sync.sh`)
and the manual equivalents are in `../relay/README.md`.

Ops: `sudo systemctl {status,restart} iroh-relay`; config at `~/iroh-relay/config.toml`; cert auto-syncs
daily via `iroh-relay-cert-sync.timer`.
